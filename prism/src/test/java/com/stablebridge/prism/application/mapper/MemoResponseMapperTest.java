package com.stablebridge.prism.application.mapper;

import static com.stablebridge.prism.fixtures.TransactionFixtures.memoBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.stablebridge.prism.api.MemoResponse;
import com.stablebridge.prism.domain.model.Signature;

class MemoResponseMapperTest {

    private final MemoResponseMapper mapper = Mappers.getMapper(MemoResponseMapper.class);

    @Test
    void shouldMapMemo() {
        // given
        var memo = memoBuilder()
                .signature(new Signature("5Kx7aEwMbMemoMapper0001"))
                .memoText("greetings from mars")
                .build();

        // when
        var result = mapper.toResponse(memo);

        // then
        var expected = MemoResponse.builder()
                .id(0)
                .signature("5Kx7aEwMbMemoMapper0001")
                .memo("greetings from mars")
                .createdAt(null)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
