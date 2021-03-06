package com.jashmore.sqs.broker.concurrent;

import com.google.common.base.Preconditions;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.jcip.annotations.ThreadSafe;

import javax.annotation.Nullable;
import javax.validation.constraints.PositiveOrZero;

/**
 * Implementation that stores the value as non-mutable field values and therefore will return the same value on every call.
 *
 * <p>This is useful when you don't need the listener to be dynamic or have the ability to turn off when needed.
 *
 * <p>This implementation is thread safe, even though it doesn't need to be, due to it only returning immutable values.
 */
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@ThreadSafe
public final class StaticConcurrentMessageBrokerProperties implements ConcurrentMessageBrokerProperties {
    private final Integer concurrencyLevel;
    private final Long preferredConcurrencyPollingRateInMilliseconds;
    private final Long errorBackoffTimeInMilliseconds;

    public StaticConcurrentMessageBrokerProperties(final Integer concurrencyLevel,
                                                   final Long preferredConcurrencyPollingRateInMilliseconds,
                                                   final Long errorBackoffTimeInMilliseconds) {
        Preconditions.checkArgument(concurrencyLevel != null && concurrencyLevel >= 0, "concurrencyLevel should be greater than or equal to zero");
        Preconditions.checkArgument(preferredConcurrencyPollingRateInMilliseconds == null || preferredConcurrencyPollingRateInMilliseconds >= 0,
                "preferredConcurrencyPollingRateInMilliseconds should null or greater than or equal to zero");

        this.concurrencyLevel = concurrencyLevel;
        this.preferredConcurrencyPollingRateInMilliseconds = preferredConcurrencyPollingRateInMilliseconds;
        this.errorBackoffTimeInMilliseconds = errorBackoffTimeInMilliseconds;
    }

    @PositiveOrZero
    @Override
    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    @PositiveOrZero
    @Override
    public Long getConcurrencyPollingRateInMilliseconds() {
        return preferredConcurrencyPollingRateInMilliseconds;
    }

    @Nullable
    @PositiveOrZero
    @Override
    public Long getErrorBackoffTimeInMilliseconds() {
        return errorBackoffTimeInMilliseconds;
    }
}
