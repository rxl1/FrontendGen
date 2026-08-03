package com.renix.codegen.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.renix.codegen.ai.tools.FileWriteTool;
import com.renix.codegen.exception.BusinessException;
import com.renix.codegen.exception.ErrorCode;
import com.renix.codegen.model.enums.CodeGenTypeEnum;
import com.renix.codegen.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {
    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel openAiStreamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * AI 服务实例缓存 <p>
     * 缓存策略：<p>
     * - 最大缓存 1000 个实例 <p>
     * - 写入后 30 分钟过期 <p>
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（为了兼容老逻辑）
     *
     * @param appId
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 创建新的 AI 服务实例
     * @param appId 应用id
     * @param codeGenType 代码生成类型枚举
     * @return
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建新的 AI 服务实例，类型: {}", appId, codeGenType);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        //  从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return switch (codeGenType) {
            // 多文件和 Vue 项目生成，使用工具调用
            case MULTI_FILE, VUE_PROJECT -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools(new FileWriteTool(codeGenType.getValue()))
                    // 工具参数由模型生成，长代码中的引号或反斜杠偶尔会导致 JSON 不合法。
                    // 将参数错误反馈给模型，让它只重试失败的文件，避免一次格式错误终止整个 SSE 流。
                    .toolArgumentsErrorHandler(malformedToolArgumentsRetryHandler())
                    // 处理工具调用幻觉问题
                    .hallucinatedToolNameStrategy(toolExecutionRequest ->
                            ToolExecutionResultMessage.from(toolExecutionRequest,
                                    "Error: there is no tool called " + toolExecutionRequest.name())
                    )
                    .build();
            // HTML 生成，使用流式对话模型
            case HTML -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .build();
            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    /**
     * 创建工具参数纠错处理器。
     *
     * <p>LangChain4j 默认会把参数解析异常直接抛出。这里返回一条安全的工具结果，
     * 让模型获得一次自我修正机会；日志只记录工具名和参数长度，不记录用户生成的完整代码。</p>
     */
    static ToolArgumentsErrorHandler malformedToolArgumentsRetryHandler() {
        return (error, context) -> {
            String toolName = context.toolExecutionRequest() == null
                    ? "unknown"
                    : context.toolExecutionRequest().name();
            String arguments = context.toolExecutionRequest() == null
                    ? null
                    : context.toolExecutionRequest().arguments();
            int argumentLength = arguments == null ? 0 : arguments.length();
            log.warn("工具参数解析失败，已反馈模型重试: tool={}, argumentLength={}, errorType={}",
                    toolName, argumentLength, error.getClass().getSimpleName());
            return ToolErrorHandlerResult.text(
                    "工具参数格式错误，当前工具没有执行。请重新调用 writeFile，只重试刚才失败的文件；"
                            + "relativeFilePath 和 content 必须是合法字符串，content 中的双引号、反斜杠和换行"
                            + "必须由工具调用协议正确转义，不要在一次调用中写入多个文件。"
            );
        };
    }

    /**
     * 默认提供一个 Bean<p>
     * 创建 AI 代码生成器服务
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * 构造缓存键
     *
     * @param appId
     * @param codeGenType
     * @return
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }
}
