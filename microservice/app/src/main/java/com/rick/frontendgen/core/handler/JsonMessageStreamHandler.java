package com.rick.frontendgen.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rick.frontendgen.ai.model.message.*;
import com.rick.frontendgen.ai.tools.BaseTool;
import com.rick.frontendgen.ai.tools.ToolManager;
import com.rick.frontendgen.entity.User;
import com.rick.frontendgen.enums.ChatHistoryMessageTypeEnum;
import com.rick.frontendgen.enums.CodeGenTypeEnum;
import com.rick.frontendgen.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理包含工具调用信息的复杂流式响应。
 * 传给前端的内容保持为结构化 JSON；写入聊天历史的内容转换为便于阅读的 Markdown。
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 Vue 项目的 TokenStream
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @param codeGenType        代码生成类型
     * @return 保持原始 JSON 事件格式的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser,
                               CodeGenTypeEnum codeGenType) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .doOnNext(chunk -> handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds))
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 不把供应商响应、请求信息等内部细节写入聊天记录。
                    String errorMessage = error instanceof dev.langchain4j.exception.RetriableException
                            ? "AI 服务暂时繁忙，请稍后重试"
                            : "AI 回复失败，请重试";
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            log.warn("忽略未知的流式消息: {}", chunk);
            return "";
        }
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getContent();
                // 直接拼接响应
                if (data != null) {
                    chatHistoryStringBuilder.append(data);
                    return data;
                }
                return "";
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager == null ? null : toolManager.getTool(toolName);
                    // 未注册的工具使用兼容展示格式。
                    String output = tool == null
                            ? String.format("\n\n> [选择工具] %s\n\n", getToolDisplayName(toolName))
                            : tool.generateToolRequestResponse();
                    chatHistoryStringBuilder.append(output);
                    return output;
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                // 根据工具名称获取工具实例
                String toolName = toolExecutedMessage.getName();
                BaseTool tool = toolManager == null ? null : toolManager.getTool(toolName);
                if (tool == null) {
                    appendToolExecution(chatHistoryStringBuilder, toolExecutedMessage);
                    return "";
                }
                String result = tool.generateToolExecutedResult(jsonObject);
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }

    private void appendToolExecution(StringBuilder history, ToolExecutedMessage message) {
        String toolName = getToolDisplayName(message.getName());
        try {
            JSONObject arguments = JSONUtil.parseObj(message.getArguments());
            String relativeFilePath = arguments.getStr("relativeFilePath");
            String content = arguments.getStr("content", "");
            String suffix = relativeFilePath == null ? "text" : FileUtil.getSuffix(relativeFilePath);
            if (suffix == null || suffix.isBlank()) {
                suffix = "text";
            }
            String fence = createCodeFence(content);
            history.append("\n\n[工具调用] ")
                    .append(toolName);
            if (relativeFilePath != null) {
                history.append(" `").append(relativeFilePath.replace("`", "\\`")).append("`");
            }
            history.append("\n\n")
                    .append(fence).append(suffix).append('\n')
                    .append(content).append('\n')
                    .append(fence).append("\n\n");
            if (message.getResult() != null && !message.getResult().isBlank()) {
                history.append("> ").append(message.getResult()).append("\n\n");
            }
        } catch (Exception e) {
            log.warn("解析工具调用参数失败，工具: {}", message.getName(), e);
            history.append("\n\n[工具调用] ")
                    .append(toolName)
                    .append("\n\n> ")
                    .append(message.getResult() == null ? "工具执行完成" : message.getResult())
                    .append("\n\n");
        }
    }

    private String getToolDisplayName(String toolName) {
        return "writeFile".equals(toolName) ? "写入文件" : (toolName == null ? "未知工具" : toolName);
    }

    private String createCodeFence(String content) {
        int maxRun = 0;
        int currentRun = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '`') {
                maxRun = Math.max(maxRun, ++currentRun);
            } else {
                currentRun = 0;
            }
        }
        return "`".repeat(Math.max(3, maxRun + 1));
    }
}
