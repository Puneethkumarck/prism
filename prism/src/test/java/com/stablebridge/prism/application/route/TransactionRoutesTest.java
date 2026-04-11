package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.api.TransactionResponse;
import com.stablebridge.prism.application.mapper.TransactionResponseMapper;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.port.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransactionRoutesTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Spy
    private final TransactionResponseMapper mapper = Mappers.getMapper(TransactionResponseMapper.class);

    @InjectMocks
    private TransactionRoutes routes;

    @Test
    void shouldClampLimitToOneWhenZero() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbClampZero0001"))
                .build();
        given(transactionRepository.findAll(1L, 0L, null)).willReturn(List.of(tx));
        given(transactionRepository.countAll()).willReturn(7L);

        // when
        var result = routes.listTransactions(0L, 0L, null);

        // then
        var expected = Page.<TransactionResponse>builder()
                .data(List.of(toResponse(tx)))
                .total(7L)
                .limit(1L)
                .offset(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldClampLimitToFiveHundredWhenExceedsMax() {
        // given
        given(transactionRepository.findAll(500L, 0L, null)).willReturn(List.of());
        given(transactionRepository.countAll()).willReturn(0L);

        // when
        var result = routes.listTransactions(999L, 0L, null);

        // then
        var expected = Page.<TransactionResponse>builder()
                .data(List.of())
                .total(0L)
                .limit(500L)
                .offset(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldPassThroughLimitWhenWithinRange() {
        // given
        given(transactionRepository.findAll(50L, 10L, null)).willReturn(List.of());
        given(transactionRepository.countAll()).willReturn(100L);

        // when
        var result = routes.listTransactions(50L, 10L, null);

        // then
        var expected = Page.<TransactionResponse>builder()
                .data(List.of())
                .total(100L)
                .limit(50L)
                .offset(10L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReturnPageOfTransactions() {
        // given
        var tx1 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbPageTest00001"))
                .build();
        var tx2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbPageTest00002"))
                .build();
        given(transactionRepository.findAll(25L, 5L, null)).willReturn(List.of(tx1, tx2));
        given(transactionRepository.countAll()).willReturn(42L);

        // when
        var result = routes.listTransactions(25L, 5L, null);

        // then
        var expected = Page.<TransactionResponse>builder()
                .data(List.of(toResponse(tx1), toResponse(tx2)))
                .total(42L)
                .limit(25L)
                .offset(5L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldFilterBySuccessTrue() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSuccessT00001"))
                .failed(false)
                .build();
        given(transactionRepository.findAll(50L, 0L, true)).willReturn(List.of(tx));
        given(transactionRepository.countBySuccess(true)).willReturn(9L);

        // when
        var result = routes.listTransactions(50L, 0L, true);

        // then
        var expected = Page.<TransactionResponse>builder()
                .data(List.of(toResponse(tx)))
                .total(9L)
                .limit(50L)
                .offset(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldFilterBySuccessFalse() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbFailedF00001"))
                .failed(true)
                .build();
        given(transactionRepository.findAll(50L, 0L, false)).willReturn(List.of(tx));
        given(transactionRepository.countBySuccess(false)).willReturn(3L);

        // when
        var result = routes.listTransactions(50L, 0L, false);

        // then
        var expected = Page.<TransactionResponse>builder()
                .data(List.of(toResponse(tx)))
                .total(3L)
                .limit(50L)
                .offset(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReturnTransactionBySignature() {
        // given
        var sig = new Signature("5Kx7aEwMbFindOne000001");
        var tx = transactionBuilder().signature(sig).slot(777L).build();
        given(transactionRepository.findBySignature(sig)).willReturn(Optional.of(tx));

        // when
        var result = routes.getBySignature("5Kx7aEwMbFindOne000001");

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(toResponse(tx));
    }

    @Test
    void shouldThrowWhenSignatureNotFound() {
        // given
        var sig = new Signature("5Kx7aEwMbMissing000001");
        given(transactionRepository.findBySignature(sig)).willReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> routes.getBySignature("5Kx7aEwMbMissing000001"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("5Kx7aEwMbMissing000001");
    }

    @Test
    void shouldReturnTransactionsForSlot() {
        // given
        var tx1 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSlotTest00001"))
                .slot(500L)
                .build();
        var tx2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSlotTest00002"))
                .slot(500L)
                .build();
        given(transactionRepository.findBySlot(500L)).willReturn(List.of(tx1, tx2));

        // when
        var result = routes.getBySlot(500L);

        // then
        var expected = List.of(toResponse(tx1), toResponse(tx2));
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    private TransactionResponse toResponse(SolanaTransaction tx) {
        return TransactionResponse.builder()
                .signature(tx.signature().value())
                .slot(tx.slot())
                .success(!tx.failed())
                .createdAt(null)
                .build();
    }
}
