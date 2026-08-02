package com.renix.codegen.core;

import com.renix.codegen.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 测试工具调用流式输出
 */
@SpringBootTest
class AiCodeGeneratorFacadeToolTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void testGenerateCodeWithTools() {
        // 测试工具调用流式输出
        Flux<String> flux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "创建一个简单的个人主页，包含标题和简介",
                CodeGenTypeEnum.VUE_PROJECT,
                999L
        );

        // 收集所有消息
        List<String> messages = new ArrayList<>();
        flux.doOnNext(message -> {
            System.out.println("收到消息: " + message);
            messages.add(message);
        }).doOnComplete(() -> {
            System.out.println("流式完成，共收到 " + messages.size() + " 条消息");
        }).doOnError(error -> {
            System.err.println("错误: " + error.getMessage());
        }).blockLast(); // 阻塞等待完成

        // 验证结果
        assertFalse(messages.isEmpty(), "应该收到至少一条消息");
        System.out.println("测试通过！");
    }
}
