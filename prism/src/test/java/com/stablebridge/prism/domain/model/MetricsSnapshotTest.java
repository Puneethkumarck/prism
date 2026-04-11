package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.MetricsFixtures.metricsSnapshotBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MetricsSnapshotTest {

    @Test
    void shouldBuildSnapshotWithAllCounters() {
        // given
        var builder = metricsSnapshotBuilder();

        // when
        var snapshot = builder.build();

        // then
        var expected = MetricsSnapshot.builder()
                .received(1_635L)
                .written(968L)
                .failed(631L)
                .memos(7L)
                .transfers(121L)
                .accountsWritten(0L)
                .accountsDropped(0L)
                .batches(30L)
                .slots(8L)
                .build();
        assertThat(snapshot).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldRejectNegativeAccountsDropped() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().accountsDropped(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountsDropped");
    }

    @Test
    void shouldExposeProcessedAsWrittenPlusFailed() {
        // given
        var snapshot = metricsSnapshotBuilder().written(10L).failed(4L).build();

        // when
        var processed = snapshot.processed();

        // then
        assertThat(processed).isEqualTo(14L);
    }

    @Test
    void shouldRejectNegativeReceived() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().received(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("received");
    }

    @Test
    void shouldRejectNegativeWritten() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().written(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("written");
    }

    @Test
    void shouldRejectNegativeFailed() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().failed(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void shouldRejectNegativeMemos() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().memos(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memos");
    }

    @Test
    void shouldRejectNegativeTransfers() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().transfers(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transfers");
    }

    @Test
    void shouldRejectNegativeAccountsWritten() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().accountsWritten(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountsWritten");
    }

    @Test
    void shouldRejectNegativeBatches() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().batches(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batches");
    }

    @Test
    void shouldRejectNegativeSlots() {
        // when/then
        assertThatThrownBy(() -> metricsSnapshotBuilder().slots(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slots");
    }
}
