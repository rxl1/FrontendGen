package com.renix.codegen.controller;

import com.renix.codegen.ai.model.message.AiResponseMessage;
import com.renix.codegen.ai.model.message.StreamErrorMessage;
import com.renix.codegen.exception.BusinessException;
import com.renix.codegen.exception.ErrorCode;
import dev.langchain4j.exception.InternalServerException;
import com.renix.codegen.model.entity.User;
import com.renix.codegen.service.AppService;
import com.renix.codegen.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.lang.reflect.Proxy;
import java.util.List;

import static cn.hutool.json.JSONUtil.toJsonStr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AppControllerStreamTest {

    @Test
    void shouldSendStructuredMessagesDirectlyAndDisableBuffering() {
        User loginUser = new User();
        loginUser.setId(9L);
        String firstChunk = toJsonStr(new AiResponseMessage("以下是"));
        String secondChunk = toJsonStr(new AiResponseMessage("网站代码"));

        UserService userService = proxy(UserService.class, (methodName, args) -> {
            if ("getLoginUser".equals(methodName)) return loginUser;
            return null;
        });
        AppService appService = proxy(AppService.class, (methodName, args) -> {
            if ("chatToGenCode".equals(methodName)) return Flux.just(firstChunk, secondChunk);
            return null;
        });

        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "appService", appService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        List<ServerSentEvent<String>> events = controller.chatToGenCode(
                        7L,
                        "创建网站",
                        new MockHttpServletRequest(),
                        response
                )
                .collectList()
                .block();

        assertEquals(3, events.size());
        assertEquals(firstChunk, events.get(0).data());
        assertEquals(secondChunk, events.get(1).data());
        assertEquals("done", events.get(2).event());
        assertEquals("no-cache, no-transform", response.getHeader("Cache-Control"));
        assertEquals("no", response.getHeader("X-Accel-Buffering"));
    }

    @Test
    void shouldConvertStreamFailureToBusinessErrorEvent() {
        User loginUser = new User();
        loginUser.setId(9L);
        String partialChunk = toJsonStr(new AiResponseMessage("已完成 index.html"));

        UserService userService = proxy(UserService.class, (methodName, args) -> {
            if ("getLoginUser".equals(methodName)) return loginUser;
            return null;
        });
        AppService appService = proxy(AppService.class, (methodName, args) -> {
            if ("chatToGenCode".equals(methodName)) {
                return Flux.concat(
                        Flux.just(partialChunk),
                        Flux.error(new RuntimeException("provider-sensitive-error"))
                );
            }
            return null;
        });

        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "appService", appService);

        List<ServerSentEvent<String>> events = controller.chatToGenCode(
                        7L,
                        "创建网站",
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse()
                )
                .collectList()
                .block();

        assertEquals(2, events.size());
        assertEquals(partialChunk, events.get(0).data());
        assertEquals("business-error", events.get(1).event());
        StreamErrorMessage errorMessage = cn.hutool.json.JSONUtil.toBean(
                events.get(1).data(),
                StreamErrorMessage.class
        );
        assertEquals("error", errorMessage.getType());
        assertEquals("AI_STREAM_ERROR", errorMessage.getCode());
        assertEquals("AI 生成过程中出现异常，请重试", errorMessage.getMessage());
        assertFalse(events.get(1).data().contains("provider-sensitive-error"));
    }

    @Test
    void shouldConvertSynchronousServiceFailureToBusinessErrorEvent() {
        User loginUser = new User();
        loginUser.setId(9L);

        UserService userService = proxy(UserService.class, (methodName, args) -> {
            if ("getLoginUser".equals(methodName)) return loginUser;
            return null;
        });
        AppService appService = proxy(AppService.class, (methodName, args) -> {
            if ("chatToGenCode".equals(methodName)) {
                throw new RuntimeException("synchronous-service-error");
            }
            return null;
        });

        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "appService", appService);

        List<ServerSentEvent<String>> events = controller.chatToGenCode(
                        7L,
                        "创建网站",
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse()
                )
                .collectList()
                .block();

        assertEquals(1, events.size());
        assertEquals("business-error", events.getFirst().event());
        assertFalse(events.getFirst().data().contains("synchronous-service-error"));
    }

    @Test
    void shouldExposeSafeBusinessErrorMessage() {
        User loginUser = new User();
        loginUser.setId(9L);

        UserService userService = proxy(UserService.class, (methodName, args) -> {
            if ("getLoginUser".equals(methodName)) return loginUser;
            return null;
        });
        AppService appService = proxy(AppService.class, (methodName, args) -> {
            if ("chatToGenCode".equals(methodName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
            }
            return null;
        });

        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "appService", appService);

        ServerSentEvent<String> event = controller.chatToGenCode(
                        7L,
                        "修改标题",
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse()
                )
                .blockFirst();

        assertEquals("business-error", event.event());
        StreamErrorMessage errorMessage = cn.hutool.json.JSONUtil.toBean(
                event.data(),
                StreamErrorMessage.class
        );
        assertEquals("应用代码生成类型错误", errorMessage.getMessage());
    }

    @Test
    void shouldExposeFriendlyMessageAfterTransientRetriesAreExhausted() {
        User loginUser = new User();
        loginUser.setId(9L);

        UserService userService = proxy(UserService.class, (methodName, args) -> {
            if ("getLoginUser".equals(methodName)) return loginUser;
            return null;
        });
        AppService appService = proxy(AppService.class, (methodName, args) -> {
            if ("chatToGenCode".equals(methodName)) {
                return Flux.error(new InternalServerException("provider-sensitive-error"));
            }
            return null;
        });

        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "appService", appService);

        ServerSentEvent<String> event = controller.chatToGenCode(
                        7L,
                        "修改标题",
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse()
                )
                .blockFirst();

        StreamErrorMessage errorMessage = cn.hutool.json.JSONUtil.toBean(
                event.data(),
                StreamErrorMessage.class
        );
        assertEquals("AI 服务暂时繁忙，自动重试后仍未恢复，请稍后再试", errorMessage.getMessage());
        assertFalse(event.data().contains("provider-sensitive-error"));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, ProxyMethod proxyMethod) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[]{type},
                (proxy, method, args) -> {
                    Object result = proxyMethod.invoke(method.getName(), args);
                    if (result != null) return result;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                }
        );
    }

    @FunctionalInterface
    private interface ProxyMethod {
        Object invoke(String methodName, Object[] args);
    }
}
