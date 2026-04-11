package com.stablebridge.prism.application.route;

import io.helidon.cors.CrossOriginConfig;
import io.helidon.webserver.cors.CorsSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CorsConfiguration {

    @SuppressWarnings("removal")
    public static CorsSupport permissive() {
        return CorsSupport.builder()
                .addCrossOrigin(CrossOriginConfig.create())
                .build();
    }
}
