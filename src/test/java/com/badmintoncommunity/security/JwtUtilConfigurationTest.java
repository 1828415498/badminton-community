package com.badmintoncommunity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilConfigurationTest {

    @Test
    void missingSigningSecretFailsWithActionableMessage() {
        JwtProperties properties = new JwtProperties();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new JwtUtil(properties));

        assertTrue(error.getMessage().contains("APP_JWT_SECRET"));
    }

    @Test
    void shortSigningSecretIsRejectedBeforeJwtInitialization() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new JwtUtil(properties));

        assertTrue(error.getMessage().contains("32"));
    }
}
