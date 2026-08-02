package com.renix.codegen.core.handler;

import cn.hutool.json.JSONUtil;
import com.renix.codegen.ai.model.message.AiResponseMessage;
import com.renix.codegen.model.entity.User;
import com.renix.codegen.model.enums.ChatHistoryMessageTypeEnum;
import com.renix.codegen.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 类型的流式响应，并将纯文本片段包装成统一的 JSON 事件。
 */
@Slf4j
public class SimpleTextStreamHandler {


    /**
     * 处理传统 HTML 文本流
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(chunk -> {
                    // 收集AI响应内容
                    aiResponseBuilder.append(chunk);
                    // 所有 SSE data 都使用相同的消息协议，避免前端把 JSON 当正文展示
                    return JSONUtil.toJsonStr(new AiResponseMessage(chunk));
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }
}
