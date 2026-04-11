package com.stablebridge.prism.application.mapper;

import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.stablebridge.prism.api.TransactionResponse;
import com.stablebridge.prism.domain.model.Signature;

class TransactionResponseMapperTest {

    private final TransactionResponseMapper mapper = Mappers.getMapper(TransactionResponseMapper.class);

    @Test
    void shouldMapSuccessfulTransaction() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbMapperTest0001"))
                .slot(123_456L)
                .failed(false)
                .build();

        // when
        var result = mapper.toResponse(tx);

        // then
        var expected = TransactionResponse.builder()
                .signature("5Kx7aEwMbMapperTest0001")
                .slot(123_456L)
                .success(true)
                .createdAt(null)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldMapFailedTransaction() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbMapperTest0002"))
                .slot(999L)
                .failed(true)
                .build();

        // when
        var result = mapper.toResponse(tx);

        // then
        var expected = TransactionResponse.builder()
                .signature("5Kx7aEwMbMapperTest0002")
                .slot(999L)
                .success(false)
                .createdAt(null)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
