package com.jashmore.sqs.resolver.batching;

import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.Max;
import javax.validation.constraints.Positive;

/**
 * Static implementation that will contain constant size and time limit for the buffer.
 */
@Value
@Builder(toBuilder = true)
public class StaticBatchingMessageResolverProperties implements BatchingMessageResolverProperties {
    private final long bufferingTimeInMs;
    private final int bufferingSizeLimit;

    @Positive
    @Override
    public long getBufferingTimeInMs() {
        return bufferingTimeInMs;
    }

    @Positive
    @Max(10)
    @Override
    public int getBufferingSizeLimit() {
        return bufferingSizeLimit;
    }
}
