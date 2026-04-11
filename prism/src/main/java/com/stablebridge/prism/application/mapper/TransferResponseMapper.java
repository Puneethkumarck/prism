package com.stablebridge.prism.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.prism.api.TransferResponse;
import com.stablebridge.prism.domain.model.LargeTransfer;

@Mapper(componentModel = "jsr330")
public interface TransferResponseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "signature", expression = "java(transfer.signature().value())")
    @Mapping(target = "amount", expression = "java(transfer.amount().doubleValue())")
    @Mapping(target = "createdAt", ignore = true)
    TransferResponse toResponse(LargeTransfer transfer);
}
