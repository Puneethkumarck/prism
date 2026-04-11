package com.stablebridge.prism.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class ReconnectHandlerTest {

    @Test
    void shouldProduceCorrectDelaySequence() {
        // given
        var handler = new ReconnectHandler();

        // when/then
        assertThat(handler.nextDelay()).isEqualTo(4L);
        assertThat(handler.nextDelay()).isEqualTo(8L);
        assertThat(handler.nextDelay()).isEqualTo(16L);
        assertThat(handler.nextDelay()).isEqualTo(30L);
        assertThat(handler.nextDelay()).isEqualTo(30L);
    }

    @Test
    void shouldCapAtMaxDelay() {
        // given
        var handler = new ReconnectHandler();
        IntStream.range(0, 10).forEach(_ -> handler.nextDelay());

        // when
        var delay = handler.nextDelay();

        // then
        assertThat(delay).isEqualTo(30L);
    }

    @Test
    void shouldResetAfter60SecondsStable() {
        // given
        var handler = new ReconnectHandler();
        handler.nextDelay();
        handler.nextDelay();
        handler.nextDelay();

        // when
        handler.resetIfStable(60L);

        // then
        assertThat(handler.nextDelay()).isEqualTo(4L);
    }

    @Test
    void shouldNotResetBelowResetThreshold() {
        // given
        var handler = new ReconnectHandler();
        handler.nextDelay();
        handler.nextDelay();

        // when
        handler.resetIfStable(59L);

        // then
        assertThat(handler.nextDelay()).isEqualTo(16L);
    }

    @Test
    void shouldExposeTwoSecondBaseDelay() {
        // when/then
        assertThat(ReconnectHandler.RECONNECT_BASE_SECS).isEqualTo(2L);
    }

    @Test
    void shouldExposeThirtySecondMaxDelay() {
        // when/then
        assertThat(ReconnectHandler.RECONNECT_MAX_SECS).isEqualTo(30L);
    }

    @Test
    void shouldExposeSixtySecondResetThreshold() {
        // when/then
        assertThat(ReconnectHandler.RECONNECT_RESET_SECS).isEqualTo(60L);
    }
}
