package com.renix.codegen.ai;

import com.renix.codegen.ai.model.HtmlCodeResult;
import com.renix.codegen.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {

    /**
     * 生成HTML代码
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码
     * @param userMessage 用户消息
     * @return 生成的结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 流式生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 流式生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 生成代码（带工具调用，流式输出）
     * 使用 TokenStream 支持工具调用的流式输出
     *
     * @param appId       应用 ID
     * @param userMessage 用户提示词
     * @return TokenStream，支持工具调用的流式输出
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-with-tools-system-prompt.txt")
    TokenStream generateMultiFileCodeWithTools(@MemoryId long appId, @UserMessage String userMessage);

    /**
     * 生成 Vue 工程代码（带工具调用，流式输出）
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeWithTools(@MemoryId long appId, @UserMessage String userMessage);
}
