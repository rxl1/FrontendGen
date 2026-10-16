package com.rick.frontendgen.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * SSE 流式错误消息。
 *
 * <p>只向前端暴露稳定的错误码和用户可读文案，避免把模型响应、文件路径或堆栈信息泄露给浏览器。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StreamErrorMessage extends StreamMessage {

    private String code;

    private String message;

    public StreamErrorMessage(String code, String message) {
        super(StreamMessageTypeEnum.ERROR.getValue());
        this.code = code;
        this.message = message;
    }
}
