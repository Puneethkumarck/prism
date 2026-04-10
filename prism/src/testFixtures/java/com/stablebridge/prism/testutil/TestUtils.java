package com.stablebridge.prism.testutil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public final class TestUtils {

    private TestUtils() {}

    public static <T> T eqIgnoringTimestamps(T expected) {
        return eqIgnoring(expected);
    }

    public static <T> T eqIgnoring(T expected, String... fieldsToIgnore) {
        return argThat(it -> isEqualIgnoringTimestampsAnd(it, expected, fieldsToIgnore));
    }

    private static <T> boolean isEqualIgnoringTimestampsAnd(T original, T expected, String... fieldsToIgnore) {
        try {
            assertThat(original)
                    .usingRecursiveComparison()
                    .ignoringFieldsOfTypes(Instant.class, ZonedDateTime.class, LocalDateTime.class)
                    .ignoringFields(fieldsToIgnore)
                    .isEqualTo(expected);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
