package com.stablebridge.prism.application.mapper;

import static com.stablebridge.prism.fixtures.TransactionFixtures.largeTransferBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.stablebridge.prism.api.TransferResponse;
import com.stablebridge.prism.domain.model.Signature;

class TransferResponseMapperTest {

    private final TransferResponseMapper mapper = Mappers.getMapper(TransferResponseMapper.class);

    @Test
    void shouldMapLargeTransfer() {
        // given
        var transfer = largeTransferBuilder()
                .signature(new Signature("5Kx7aEwMbMapperXfer001"))
                .slot(42L)
                .amount(new BigDecimal("12.75"))
                .build();

        // when
        var result = mapper.toResponse(transfer);

        // then
        var expected = TransferResponse.builder()
                .id(0)
                .signature("5Kx7aEwMbMapperXfer001")
                .slot(42L)
                .amount(12.75d)
                .createdAt(null)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
