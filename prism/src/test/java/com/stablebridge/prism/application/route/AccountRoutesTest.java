package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.api.AccountResponse;
import com.stablebridge.prism.application.mapper.AccountResponseMapper;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.port.AccountRepository;

@ExtendWith(MockitoExtension.class)
class AccountRoutesTest {

    @Mock
    private AccountRepository accountRepository;

    @Spy
    private final AccountResponseMapper mapper = Mappers.getMapper(AccountResponseMapper.class);

    @InjectMocks
    private AccountRoutes routes;

    @Test
    void shouldReturnAccountByPubkey() {
        // given
        var pubkey = new Pubkey("7xKXtg2CUnit0000000001");
        var account = accountBuilder()
                .pubkey(pubkey)
                .lamports(250_000_000L)
                .slot(1_234L)
                .executable(false)
                .rentEpoch(7L)
                .build();
        given(accountRepository.findByPubkey(pubkey)).willReturn(Optional.of(account));

        // when
        var result = routes.getByPubkey("7xKXtg2CUnit0000000001");

        // then
        var expected = AccountResponse.builder()
                .pubkey("7xKXtg2CUnit0000000001")
                .lamports(250_000_000L)
                .slot(1_234L)
                .executable(false)
                .rentEpoch(7L)
                .createdAt(null)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        // given
        var pubkey = new Pubkey("7xKXtg2CMissing000001");
        given(accountRepository.findByPubkey(pubkey)).willReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> routes.getByPubkey("7xKXtg2CMissing000001"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("7xKXtg2CMissing000001");
    }
}
