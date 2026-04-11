package com.stablebridge.prism.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.fixtures.E2eBlockFixture;
import com.stablebridge.prism.infrastructure.grpc.TransactionParser;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateTransaction;
import com.stablebridge.prism.infrastructure.websocket.BlockNotificationParser;

class AdapterParityTest {

    private final TransactionParser grpcParser = new TransactionParser();
    private final BlockNotificationParser webSocketParser = new BlockNotificationParser();

    @Test
    void shouldProduceIdenticalDomainTransactionsFromBothAdapters() {
        // given
        var expected = E2eBlockFixture.expectedDomainTransactions();

        // when
        var parity = parseParity();

        // then
        assertThat(parity.grpcTransactions())
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(parity.webSocketTransactions())
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(parity.grpcTransactions())
                .usingRecursiveComparison()
                .isEqualTo(parity.webSocketTransactions());
    }

    @Test
    void shouldProduceIdenticalFeePayerAccountsFromBothAdapters() {
        // given
        var expected = E2eBlockFixture.expectedDomainFeePayers();

        // when
        var parity = parseParity();

        // then
        assertThat(parity.grpcFeePayers())
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(parity.webSocketFeePayers())
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(parity.grpcFeePayers())
                .usingRecursiveComparison()
                .isEqualTo(parity.webSocketFeePayers());
    }

    @Test
    void shouldSplitTransactionsIntoTwoSuccessesAndOneFailureFromBothAdapters() {
        // given
        var parity = parseParity();

        // when
        var counts = ClassificationCounts.of(parity);

        // then
        var expected = new ClassificationCounts(2L, 1L, 2L, 1L);
        assertThat(counts).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldProduceDistinctFeePayersForAllThreeTransactions() {
        // given
        var parity = parseParity();

        // when
        var distinctPubkeys = parity.grpcFeePayers().stream()
                .map(Account::pubkey)
                .distinct()
                .toList();

        // then
        assertThat(distinctPubkeys).hasSize(3);
    }

    private ParityResult parseParity() {
        var grpcUpdates = E2eBlockFixture.grpcUpdates();
        var webSocketBlock = E2eBlockFixture.webSocketBlockValue();
        return new ParityResult(
                parseGrpcTransactions(grpcUpdates),
                parseGrpcFeePayers(grpcUpdates),
                webSocketParser.parseBlock(webSocketBlock),
                webSocketParser.extractFeePayers(webSocketBlock));
    }

    private List<SolanaTransaction> parseGrpcTransactions(List<SubscribeUpdateTransaction> updates) {
        return updates.stream()
                .map(grpcParser::parseTransaction)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<Account> parseGrpcFeePayers(List<SubscribeUpdateTransaction> updates) {
        return updates.stream()
                .map(grpcParser::extractFeePayer)
                .flatMap(Optional::stream)
                .toList();
    }

    private record ParityResult(
            List<SolanaTransaction> grpcTransactions,
            List<Account> grpcFeePayers,
            List<SolanaTransaction> webSocketTransactions,
            List<Account> webSocketFeePayers) {}

    private record ClassificationCounts(
            long grpcSuccessCount,
            long grpcFailedCount,
            long webSocketSuccessCount,
            long webSocketFailedCount) {

        static ClassificationCounts of(ParityResult parity) {
            return new ClassificationCounts(
                    parity.grpcTransactions().stream().filter(tx -> !tx.failed()).count(),
                    parity.grpcTransactions().stream().filter(SolanaTransaction::failed).count(),
                    parity.webSocketTransactions().stream().filter(tx -> !tx.failed()).count(),
                    parity.webSocketTransactions().stream().filter(SolanaTransaction::failed).count());
        }
    }
}
