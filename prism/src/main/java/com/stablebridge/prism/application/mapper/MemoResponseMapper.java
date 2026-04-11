package com.stablebridge.prism.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.prism.api.MemoResponse;
import com.stablebridge.prism.domain.model.Memo;

@Mapper(componentModel = "jsr330")
public interface MemoResponseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "signature", expression = "java(memo.signature().value())")
    @Mapping(target = "memo", source = "memoText")
    @Mapping(target = "createdAt", ignore = true)
    MemoResponse toResponse(Memo memo);
}
