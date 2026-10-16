package com.rick.frontendgen.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 响应消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AiResponseMessage extends StreamMessage {

    /**
     * AI 响应内容
     */
    private String content;

    public AiResponseMessage(String content) {
        super(StreamMessageTypeEnum.AI_RESPONSE.getValue());
        this.content = content;
    }
}
