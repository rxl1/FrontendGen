package com.renix.codegen.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.renix.codegen.ai.model.message.*;
import com.renix.codegen.constant.AppConstant;
import com.renix.codegen.core.builder.VueProjectBuilder;
import com.renix.codegen.model.entity.User;
import com.renix.codegen.model.enums.ChatHistoryMessageTypeEnum;
import com.renix.codegen.model.enums.CodeGenTypeEnum;
import com.renix.codegen.service.ChatHistoryService;
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
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 处理 TokenStream（MULTI_FILE、VUE_PROJECT）
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
                .doOnNext(chunk -> collectHistoryContent(chunk, chatHistoryStringBuilder, seenToolIds))
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
                        // Vue 工程需要构建 dist，原生多文件项目无需构建
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                        vueProjectBuilder.buildProjectAsync(projectPath);
                    }
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private void collectHistoryContent(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            log.warn("忽略未知的流式消息: {}", chunk);
            return;
        }
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String content = aiMessage.getContent();
                if (content != null) {
                    chatHistoryStringBuilder.append(content);
                }
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolKey = toolId != null ? toolId : toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolKey != null && seenToolIds.add(toolKey)) {
                    chatHistoryStringBuilder
                            .append("\n\n> [选择工具] ")
                            .append(getToolDisplayName(toolRequestMessage.getName()))
                            .append("\n\n");
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                appendToolExecution(chatHistoryStringBuilder, toolExecutedMessage);
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
