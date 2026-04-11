package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.TransactionFixtures.largeTransferBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.api.TransferResponse;
import com.stablebridge.prism.application.mapper.TransferResponseMapper;
import com.stablebridge.prism.domain.model.LargeTransfer;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.port.TransferRepository;

@ExtendWith(MockitoExtension.class)
class TransferRoutesTest {

    @Mock
    private TransferRepository transferRepository;

    @Spy
    private final TransferResponseMapper mapper = Mappers.getMapper(TransferResponseMapper.class);

    @InjectMocks
    private TransferRoutes routes;

    @Test
    void shouldReturnPageOfTransfers() {
        // given
        var minAmount = new BigDecimal("2.0");
        var transfer = largeTransferBuilder()
                .signature(new Signature("5Kx7aEwMbTransferUnit1"))
                .slot(123L)
                .amount(new BigDecimal("3.50"))
                .build();
        given(transferRepository.findByMinAmount(minAmount, 50L, 0L)).willReturn(List.of(transfer));
        given(transferRepository.countByMinAmount(minAmount)).willReturn(4L);

        // when
        var result = routes.listTransfers(50L, 0L, minAmount);

        // then
        var expected = Page.<TransferResponse>builder()
                .data(List.of(toResponse(transfer)))
                .total(4L)
                .limit(50L)
                .offset(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldClampLimitToMax() {
        // given
        given(transferRepository.findByMinAmount(BigDecimal.ZERO, 500L, 0L)).willReturn(List.of());
        given(transferRepository.countByMinAmount(BigDecimal.ZERO)).willReturn(0L);

        // when
        var result = routes.listTransfers(999L, 0L, BigDecimal.ZERO);

        // then
        var expected = Page.<TransferResponse>builder()
                .data(List.of())
                .total(0L)
                .limit(500L)
                .offset(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    private TransferResponse toResponse(LargeTransfer transfer) {
        return TransferResponse.builder()
                .id(0)
                .signature(transfer.signature().value())
                .slot(transfer.slot())
                .amount(transfer.amount().doubleValue())
                .createdAt(null)
                .build();
    }
}
