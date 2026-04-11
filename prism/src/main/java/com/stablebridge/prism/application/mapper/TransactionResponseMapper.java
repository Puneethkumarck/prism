package com.stablebridge.prism.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.prism.api.TransactionResponse;
import com.stablebridge.prism.domain.model.SolanaTransaction;

@Mapper(componentModel = "jsr330")
public interface TransactionResponseMapper {

    @Mapping(target = "signature", expression = "java(tx.signature().value())")
    @Mapping(target = "success", expression = "java(!tx.failed())")
    @Mapping(target = "createdAt", ignore = true)
    TransactionResponse toResponse(SolanaTransaction tx);
}
