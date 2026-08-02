package com.renix.codegen.controller;

import com.renix.codegen.ai.model.message.AiResponseMessage;
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
