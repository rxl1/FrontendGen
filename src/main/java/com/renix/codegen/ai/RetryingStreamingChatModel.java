package com.renix.codegen.ai;

import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 为流式模型增加瞬时故障重试。
 *
 * <p>仅在供应商尚未返回任何文本或工具调用时重试，避免响应已经开始后重放内容或重复执行工具。</p>
 */
@Slf4j
public class RetryingStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;
    private final int maxRetries;
    private final Duration initialBackoff;

    public RetryingStreamingChatModel(StreamingChatModel delegate,
                                      int maxRetries,
                                      Duration initialBackoff) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 不能为空");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能小于 0");
        }
        if (initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff 不能为负数");
        }
        this.delegate = delegate;
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        executeAttempt(chatRequest, handler, 0);
    }

    private void executeAttempt(ChatRequest chatRequest,
                                StreamingChatResponseHandler handler,
                                int retryCount) {
        AtomicBoolean responseStarted = new AtomicBoolean(false);
        AtomicBoolean attemptTerminated = new AtomicBoolean(false);

        StreamingChatResponseHandler retryHandler = new StreamingChatResponseHandler() {
            private boolean canForward() {
                return !attemptTerminated.get();
            }

            private void markResponseStarted() {
                responseStarted.set(true);
            }

            @Override
            public void onPartialResponse(String partialResponse) {
                if (!canForward()) return;
                markResponseStarted();
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                if (!canForward()) return;
                markResponseStarted();
                handler.onPartialResponse(partialResponse, context);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                if (!canForward()) return;
                markResponseStarted();
                handler.onPartialThinking(partialThinking);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
                if (!canForward()) return;
                markResponseStarted();
                handler.onPartialThinking(partialThinking, context);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                if (!canForward()) return;
                markResponseStarted();
                handler.onPartialToolCall(partialToolCall);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
                if (!canForward()) return;
                markResponseStarted();
                handler.onPartialToolCall(partialToolCall, context);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                if (!canForward()) return;
                markResponseStarted();
                handler.onCompleteToolCall(completeToolCall);
            }

            @Override
            public void onUnmappedRawEvent(Object rawEvent) {
                if (!canForward()) return;
                markResponseStarted();
                handler.onUnmappedRawEvent(rawEvent);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                if (attemptTerminated.compareAndSet(false, true)) {
                    handler.onCompleteResponse(completeResponse);
                }
            }

            @Override
            public void onError(Throwable error) {
                if (!attemptTerminated.compareAndSet(false, true)) return;

                if (!responseStarted.get() && error instanceof RetriableException && retryCount < maxRetries) {
                    long delayMillis = retryDelayMillis(retryCount);
                    log.warn("流式模型暂时不可用，将在 {} ms 后进行第 {}/{} 次重试: errorType={}",
                            delayMillis, retryCount + 1, maxRetries, error.getClass().getSimpleName());
                    CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS)
                            .execute(() -> executeAttempt(chatRequest, handler, retryCount + 1));
                    return;
                }

                handler.onError(error);
            }
        };

        try {
            delegate.doChat(chatRequest, retryHandler);
        } catch (Throwable error) {
            retryHandler.onError(error);
        }
    }

    private long retryDelayMillis(int retryCount) {
        long initialMillis = initialBackoff.toMillis();
        long multiplier = 1L << Math.min(retryCount, 20);
        try {
            return Math.multiplyExact(initialMillis, multiplier);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
