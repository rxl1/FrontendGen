package com.renix.codegen.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGeneration;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationMessage;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationParam;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationResult;
import com.renix.codegen.langgraph4j.model.ImageResource;
import com.renix.codegen.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logo 图片生成工具
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    //@Value("${dashscope.image-model:wan2.6-t2i}")
    @Value("${dashscope.image-model:wan2.7-t2v-2026-06-12}")
    private String imageModel;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            ImageGenerationMessage message = ImageGenerationMessage.builder()
                    .role("user")
                    .content(Collections.singletonList(
                            Collections.singletonMap("text", logoPrompt)
                    ))
                    .build();
            ImageGenerationParam param = ImageGenerationParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .messages(Collections.singletonList(message))
                    .size("1280*1280")
                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
                    .negativePrompt("")
                    .promptExtend(true)
                    .watermark(false)
                    .build();
            ImageGenerationResult result = new ImageGeneration().call(param);
            if (result == null || result.getOutput() == null || result.getOutput().getChoices() == null) {
                return logoList;
            }
            result.getOutput().getChoices().stream()
                    .filter(choice -> choice != null && choice.getMessage() != null
                            && choice.getMessage().getContent() != null)
                    .flatMap(choice -> choice.getMessage().getContent().stream())
                    .map(content -> content.get("image"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(StrUtil::isNotBlank)
                    .map(imageUrl -> ImageResource.builder()
                            .category(ImageCategoryEnum.LOGO)
                            .description(description)
                            .url(imageUrl)
                            .build())
                    .forEach(logoList::add);
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }
}
