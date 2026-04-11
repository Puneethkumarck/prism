package com.stablebridge.prism.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.prism.api.AccountResponse;
import com.stablebridge.prism.domain.model.Account;

@Mapper(componentModel = "jsr330")
public interface AccountResponseMapper {

    @Mapping(target = "pubkey", expression = "java(account.pubkey().value())")
    @Mapping(target = "createdAt", ignore = true)
    AccountResponse toResponse(Account account);
}
