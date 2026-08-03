package com.renix.codegen.core.handler;

import cn.hutool.json.JSONUtil;
import com.renix.codegen.ai.model.message.AiResponseMessage;
import com.renix.codegen.ai.model.message.StreamErrorMessage;
import com.renix.codegen.ai.model.message.ToolRequestMessage;
import com.renix.codegen.model.entity.User;
import com.renix.codegen.model.enums.CodeGenTypeEnum;
import com.renix.codegen.service.ChatHistoryService;
import dev.langchain4j.model.chat.response.PartialToolCall;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamMessageProtocolTest {

    @Test
    void shouldSerializeSafeStreamErrorPayload() {
        StreamErrorMessage message = new StreamErrorMessage("AI_STREAM_ERROR", "AI 生成过程中出现异常，请重试");

        String json = JSONUtil.toJsonStr(message);

        assertEquals("error", JSONUtil.parseObj(json).getStr("type"));
        assertEquals("AI_STREAM_ERROR", JSONUtil.parseObj(json).getStr("code"));
        assertEquals("AI 生成过程中出现异常，请重试", JSONUtil.parseObj(json).getStr("message"));
    }

    @Test
    void shouldSerializePartialToolCallWithCorrelationFields() {
        PartialToolCall partialToolCall = PartialToolCall.builder()
                .index(1)
                .id("call-1")
                .name("writeFile")
                .partialArguments("{\"relativeFilePath\":")
                .build();

        ToolRequestMessage message = new ToolRequestMessage(partialToolCall);
        String json = JSONUtil.toJsonStr(message);

        assertEquals("tool_request", JSONUtil.parseObj(json).getStr("type"));
        assertEquals(1, JSONUtil.parseObj(json).getInt("index"));
        assertEquals("call-1", JSONUtil.parseObj(json).getStr("id"));
        assertEquals("{\"relativeFilePath\":", JSONUtil.parseObj(json).getStr("arguments"));
    }

    @Test
    void shouldWrapPlainTextChunksInTheUnifiedProtocol() {
        AtomicReference<Object[]> savedMessage = new AtomicReference<>();
        ChatHistoryService historyService = recordingHistoryService(savedMessage);
        User user = new User();
        user.setId(9L);

        List<String> output = new SimpleTextStreamHandler()
                .handle(Flux.just("以下是", "网站代码"), historyService, 7L, user)
                .collectList()
                .block();

        assertEquals(2, output.size());
        assertEquals("以下是", JSONUtil.toBean(output.get(0), AiResponseMessage.class).getContent());
        assertEquals("网站代码", JSONUtil.toBean(output.get(1), AiResponseMessage.class).getContent());
        assertEquals(List.of(7L, "以下是网站代码", "ai", 9L), List.of(savedMessage.get()));
    }

    @Test
    void shouldKeepStructuredEventsForSseAndStoreReadableHistory() {
        AtomicReference<Object[]> savedMessage = new AtomicReference<>();
        ChatHistoryService historyService = recordingHistoryService(savedMessage);
        User user = new User();
        user.setId(9L);
        JsonMessageStreamHandler handler = new JsonMessageStreamHandler();

        String aiEvent = JSONUtil.toJsonStr(new AiResponseMessage("## 网站生成计划\n"));
        String requestEvent = JSONUtil.toJsonStr(Map.of(
                "type", "tool_request",
                "index", 0,
                "id", "call-1",
                "name", "writeFile",
                "arguments", "{\"relativeFilePath\":"
        ));
        String executedEvent = JSONUtil.toJsonStr(Map.of(
                "type", "tool_executed",
                "id", "call-1",
                "name", "writeFile",
                "arguments", "{\"relativeFilePath\":\"index.html\",\"content\":\"<h1>你好</h1>\"}",
                "result", "文件写入成功: index.html"
        ));

        List<String> output = handler.handle(
                        Flux.just(aiEvent, requestEvent, executedEvent),
                        historyService,
                        7L,
                        user,
                        CodeGenTypeEnum.MULTI_FILE
                )
                .collectList()
                .block();

        assertEquals(List.of(aiEvent, requestEvent, executedEvent), output);
        assertEquals(7L, savedMessage.get()[0]);
        assertEquals("ai", savedMessage.get()[2]);
        assertEquals(9L, savedMessage.get()[3]);
        String history = (String) savedMessage.get()[1];
        assertTrue(history.contains("[选择工具] 写入文件"));
        assertTrue(history.contains("[工具调用] 写入文件 `index.html`"));
        assertTrue(history.contains("<h1>你好</h1>"));
    }

    private ChatHistoryService recordingHistoryService(AtomicReference<Object[]> savedMessage) {
        return (ChatHistoryService) Proxy.newProxyInstance(
                ChatHistoryService.class.getClassLoader(),
                new Class[]{ChatHistoryService.class},
                (proxy, method, args) -> {
                    if ("addChatMessage".equals(method.getName())) {
                        savedMessage.set(args);
                        return true;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                }
        );
    }
}
