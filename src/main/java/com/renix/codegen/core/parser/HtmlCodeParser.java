package com.renix.codegen.core.parser;

import com.renix.codegen.ai.model.HtmlCodeResult;
import com.renix.codegen.exception.BusinessException;
import com.renix.codegen.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 单文件代码解析器
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML_DOCUMENT_PATTERN = Pattern.compile(
            "(?:<!DOCTYPE\\s+html[^>]*>\\s*)?<html\\b[\\s\\S]*?</html>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HTML_BODY_PATTERN = Pattern.compile(
            "<body\\b[\\s\\S]*?</body>",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        // 优先提取 Markdown HTML 代码块。
        String htmlCode = extractHtmlCode(codeContent);
        if (!isCompleteHtmlDocument(htmlCode)) {
            // 兼容模型不使用 Markdown 代码块、但确实返回了完整 HTML 文档的情况。
            htmlCode = extractRawHtmlDocument(codeContent);
        }
        if (!isCompleteHtmlDocument(htmlCode)) {
            // 普通对话文本绝不能覆盖已经生成的 index.html。
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "AI 回复中未包含完整 HTML 文档，保留原网页"
            );
        }

        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode(htmlCode.trim());
        return result;
    }

    /**
     * 提取 HTML 代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private String extractHtmlCode(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从带有说明文字的响应中提取完整的原始 HTML 文档。
     */
    private String extractRawHtmlDocument(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = HTML_DOCUMENT_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private boolean isCompleteHtmlDocument(String content) {
        return content != null
                && HTML_DOCUMENT_PATTERN.matcher(content).find()
                && HTML_BODY_PATTERN.matcher(content).find();
    }
}
