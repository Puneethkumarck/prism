package com.stablebridge.prism.application.mapper;

import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.stablebridge.prism.api.AccountResponse;
import com.stablebridge.prism.domain.model.Pubkey;

class AccountResponseMapperTest {

    private final AccountResponseMapper mapper = Mappers.getMapper(AccountResponseMapper.class);

    @Test
    void shouldMapAccount() {
        // given
        var account = accountBuilder()
                .pubkey(new Pubkey("7xKXtg2CAccountMap0001"))
                .lamports(555_000_000L)
                .slot(999_999L)
                .executable(true)
                .rentEpoch(42L)
                .build();

        // when
        var result = mapper.toResponse(account);

        // then
        var expected = AccountResponse.builder()
                .pubkey("7xKXtg2CAccountMap0001")
                .lamports(555_000_000L)
                .slot(999_999L)
                .executable(true)
                .rentEpoch(42L)
                .createdAt(null)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
