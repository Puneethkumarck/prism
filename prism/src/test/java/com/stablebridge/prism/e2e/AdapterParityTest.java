package com.stablebridge.prism.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.fixtures.E2eBlockFixture;
import com.stablebridge.prism.infrastructure.grpc.TransactionParser;
import com.stablebridge.prism.infrastructure.websocket.BlockNotificationParser;

class AdapterParityTest {

    private final TransactionParser grpcParser = new TransactionParser();
    private final BlockNotificationParser webSocketParser = new BlockNotificationParser();

    @Test
    void shouldProduceIdenticalDomainTransactionsFromBothAdapters() {
        // given
        var grpcUpdates = E2eBlockFixture.grpcUpdates();
        var webSocketBlock = E2eBlockFixture.webSocketBlockValue();
        var expected = E2eBlockFixture.expectedDomainTransactions();

        // when
        var grpcTransactions = grpcUpdates.stream()
                .map(grpcParser::parseTransaction)
                .flatMap(Optional::stream)
                .toList();
        var webSocketTransactions = webSocketParser.parseBlock(webSocketBlock);

        // then
        assertThat(grpcTransactions)
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(webSocketTransactions)
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(grpcTransactions)
                .usingRecursiveComparison()
                .isEqualTo(webSocketTransactions);
    }

    @Test
    void shouldProduceIdenticalFeePayerAccountsFromBothAdapters() {
        // given
        var grpcUpdates = E2eBlockFixture.grpcUpdates();
        var webSocketBlock = E2eBlockFixture.webSocketBlockValue();
        var expected = E2eBlockFixture.expectedDomainFeePayers();

        // when
        var grpcFeePayers = grpcUpdates.stream()
                .map(grpcParser::extractFeePayer)
                .flatMap(Optional::stream)
                .toList();
        var webSocketFeePayers = webSocketParser.extractFeePayers(webSocketBlock);

        // then
        assertThat(grpcFeePayers)
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(webSocketFeePayers)
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(grpcFeePayers)
                .usingRecursiveComparison()
                .isEqualTo(webSocketFeePayers);
    }

    @Test
    void shouldClassifySuccessAndFailureConsistently() {
        // given
        var grpcUpdates = E2eBlockFixture.grpcUpdates();
        var webSocketBlock = E2eBlockFixture.webSocketBlockValue();

        // when
        var grpcFailed = grpcUpdates.stream()
                .map(grpcParser::parseTransaction)
                .flatMap(Optional::stream)
                .filter(SolanaTransaction::failed)
                .toList();
        var webSocketFailed = webSocketParser.parseBlock(webSocketBlock).stream()
                .filter(SolanaTransaction::failed)
                .toList();
        var grpcSuccessCount = grpcUpdates.stream()
                .map(grpcParser::parseTransaction)
                .flatMap(Optional::stream)
                .filter(tx -> !tx.failed())
                .count();

        // then
        assertThat(grpcFailed).hasSize(1);
        assertThat(webSocketFailed)
                .usingRecursiveComparison()
                .isEqualTo(grpcFailed);
        assertThat(grpcSuccessCount).isEqualTo(2L);
    }

    @Test
    void shouldProduceDistinctFeePayersForAllThreeTransactions() {
        // given
        var grpcUpdates = E2eBlockFixture.grpcUpdates();

        // when
        var distinctPubkeys = grpcUpdates.stream()
                .map(grpcParser::extractFeePayer)
                .flatMap(Optional::stream)
                .map(Account::pubkey)
                .distinct()
                .toList();

        // then
        assertThat(distinctPubkeys).hasSize(3);
    }
}
