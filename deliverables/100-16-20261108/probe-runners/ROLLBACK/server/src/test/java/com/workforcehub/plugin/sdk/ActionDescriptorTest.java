package com.workforcehub.plugin.sdk;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionDescriptorTest {

    @Test
    void timeoutMustBePositiveAndBounded() {
        assertThrows(IllegalArgumentException.class, () -> descriptor(null));
        assertThrows(IllegalArgumentException.class, () -> descriptor(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> descriptor(Duration.ofMillis(-1)));
        assertDoesNotThrow(() -> descriptor(Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class, () -> descriptor(Duration.ofMinutes(5).plusMillis(1)));
    }

    private ActionDescriptor descriptor(Duration timeout) {
        return new ActionDescriptor(
            "test.action",
            "test",
            "test",
            Map.of(),
            Map.of(),
            "TEST",
            timeout,
            true
        );
    }
}
