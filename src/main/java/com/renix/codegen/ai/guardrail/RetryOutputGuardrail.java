package com.renix.codegen.ai.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.util.List;
import java.util.Locale;

/**
 * AI 输出重试护轨。
 */
public class RetryOutputGuardrail implements OutputGuardrail {

    private static final List<String> SENSITIVE_WORDS = List.of(
            "密码", "password", "secret", "token",
            "api key", "私钥", "证书", "credential"
    );

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        if (responseFromLLM == null) {
            return successWith(AiMessage.from(""));
        }

        // 工具调用响应可以只有 tool_calls 而没有 text，不能将其误判为无效输出。
        if (responseFromLLM.hasToolExecutionRequests()) {
            return successWith(withNonNullText(responseFromLLM));
        }

        String response = responseFromLLM.text();
        // 检查响应是否为空或过短
        if (response == null || response.trim().isEmpty()) {
            return OutputGuardrailResult.successWith(responseFromLLM.withText(""));
        }
        if (response.trim().length() < 2) {
            return reprompt("响应内容过短", "请提供更详细的内容");
        }
        // 检查是否包含敏感信息或不当内容
        if (containsSensitiveContent(response)) {
            return reprompt("包含敏感信息", "请重新生成内容，避免包含敏感信息");
        }
        return OutputGuardrailResult.successWith(responseFromLLM.withText(response));
    }

    private AiMessage withNonNullText(AiMessage message) {
        return message.text() == null ? message.withText("") : message;
    }

    /**
     * 检查是否包含敏感内容。
     */
    private boolean containsSensitiveContent(String response) {
        String lowerResponse = response.toLowerCase(Locale.ROOT);
        return SENSITIVE_WORDS.stream().anyMatch(lowerResponse::contains);
    }

}
