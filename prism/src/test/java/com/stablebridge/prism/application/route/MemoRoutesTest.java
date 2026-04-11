package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.TransactionFixtures.memoBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.api.MemoResponse;
import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.application.mapper.MemoResponseMapper;
import com.stablebridge.prism.domain.model.Memo;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.port.MemoRepository;

@ExtendWith(MockitoExtension.class)
class MemoRoutesTest {

    @Mock
    private MemoRepository memoRepository;

    @Spy
    private final MemoResponseMapper mapper = Mappers.getMapper(MemoResponseMapper.class);

    @InjectMocks
    private MemoRoutes routes;

    @Test
    void shouldReturnPageOfMemos() {
        // given
        var memo = memoBuilder()
                .signature(new Signature("5Kx7aEwMbMemoUnit00001"))
                .memoText("unit test memo")
                .build();
        given(memoRepository.findAll(50L, 0L)).willReturn(List.of(memo));
        given(memoRepository.countAll()).willReturn(8L);

        // when
        var result = routes.listMemos(50L, 0L);

        // then
        var expected = Page.<MemoResponse>builder()
                .data(List.of(toResponse(memo)))
                .total(8L)
                .limit(50L)
                .offset(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldClampLimitWhenBelowMinimum() {
        // given
        given(memoRepository.findAll(1L, 0L)).willReturn(List.of());
        given(memoRepository.countAll()).willReturn(0L);

        // when
        var result = routes.listMemos(0L, 0L);

        // then
        var expected = Page.<MemoResponse>builder()
                .data(List.of())
                .total(0L)
                .limit(1L)
                .offset(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    private MemoResponse toResponse(Memo memo) {
        return MemoResponse.builder()
                .id(0)
                .signature(memo.signature().value())
                .memo(memo.memoText())
                .createdAt(null)
                .build();
    }
}
