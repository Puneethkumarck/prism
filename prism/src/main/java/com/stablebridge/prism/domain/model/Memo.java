package com.stablebridge.prism.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record Memo(
        Signature signature,
        String memoText
) {

    public Memo {
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(memoText, "memoText must not be null");
    }
}
