package com.workforcehub.plugin.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceResultTest {

    @Test
    void failureMustNotUseSuccessCode() {
        assertThrows(IllegalArgumentException.class, () -> ServiceResult.fail(0, "failed"));
        assertThrows(IllegalArgumentException.class, () -> ServiceResult.fail(0, "retry", true));
    }
}
