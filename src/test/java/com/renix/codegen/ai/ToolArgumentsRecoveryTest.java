package com.renix.codegen.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolArgumentsRecoveryTest {

    @Test
    void shouldReturnMalformedToolArgumentsToModelAndAllowRetry() throws InterruptedException {
        AtomicInteger modelRequestCount = new AtomicInteger();
        AtomicInteger toolExecutionCount = new AtomicInteger();
        AtomicReference<ChatRequest> retryRequest = new AtomicReference<>();

        StreamingChatModel model = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                int requestNumber = modelRequestCount.incrementAndGet();
                if (requestNumber == 1) {
                    // 模拟线上问题：content 字符串中的双引号未转义，导致工具参数不是合法 JSON。
                    ToolExecutionRequest malformedRequest = ToolExecutionRequest.builder()
                            .id("call-1")
                            .name("writeFile")
                            .arguments("{\"relativeFilePath\":\"script.js\",\"content\":\"const value = \"0\";\"}")
                            .build();
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from(malformedRequest))
                            .build());
                    return;
                }

                if (requestNumber == 2) {
                    // 参数错误处理器的返回文本应进入下一轮请求；这里模拟模型修正 JSON 后重试当前文件。
                    retryRequest.set(request);
                    ToolExecutionRequest correctedRequest = ToolExecutionRequest.builder()
                            .id("call-2")
                            .name("writeFile")
                            .arguments("{\"relativeFilePath\":\"script.js\",\"content\":\"const value = 0;\"}")
                            .build();
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from(correctedRequest))
                            .build());
                    return;
                }

                // 修正后的工具执行成功，模型完成最终回复。
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("script.js 已重新生成完成"))
                        .build());
            }
        };

        RecoveryAiService service = AiServices.builder(RecoveryAiService.class)
                .streamingChatModel(model)
                .tools(new RecordingFileTool(toolExecutionCount))
                .toolArgumentsErrorHandler(AiCodeGeneratorServiceFactory.malformedToolArgumentsRetryHandler())
                .build();

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<ChatResponse> finalResponse = new AtomicReference<>();
        AtomicReference<Throwable> streamError = new AtomicReference<>();
        service.chat("创建网站")
                .onCompleteResponse(response -> {
                    finalResponse.set(response);
                    completed.countDown();
                })
                .onError(error -> {
                    streamError.set(error);
                    completed.countDown();
                })
                .start();

        assertTrue(completed.await(2, TimeUnit.SECONDS), "流式调用应在测试超时前完成");
        assertNull(streamError.get(), "参数 JSON 错误不应再终止 TokenStream");
        assertEquals(3, modelRequestCount.get(), "模型应收到参数错误、纠正工具调用并完成最终回复");
        assertEquals(1, toolExecutionCount.get(), "非法参数不能执行工具，修正后的调用应且仅应执行一次");
        assertEquals("script.js 已重新生成完成", finalResponse.get().aiMessage().text());

        ToolExecutionResultMessage toolError = retryRequest.get().messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(Boolean.TRUE, toolError.isError());
        assertTrue(toolError.text().contains("只重试刚才失败的文件"));
        assertFalse(toolError.text().contains("script.js"), "反馈给模型的错误不应复制用户代码或文件内容");
    }

    private interface RecoveryAiService {
        TokenStream chat(@UserMessage String message);
    }

    private static final class RecordingFileTool {

        private final AtomicInteger executionCount;

        private RecordingFileTool(AtomicInteger executionCount) {
            this.executionCount = executionCount;
        }

        @Tool("写入测试文件")
        public String writeFile(@P("文件相对路径") String relativeFilePath,
                                @P("文件内容") String content) {
            executionCount.incrementAndGet();
            return "文件写入成功: " + relativeFilePath;
        }
    }
}
