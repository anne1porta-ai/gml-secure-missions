package com.gml.shield.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretEncryptionServiceTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptedSecretCanBeDecrypted() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);

        String encrypted = service.encrypt("WAREHOUSE-42");

        assertThat(encrypted).isNotEqualTo("WAREHOUSE-42");
        assertThat(service.decrypt(encrypted)).isEqualTo("WAREHOUSE-42");
    }

    @Test
    void sameSecretProducesDifferentCiphertextBecauseIvIsRandom() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);

        String first = service.encrypt("WAREHOUSE-42");
        String second = service.encrypt("WAREHOUSE-42");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("WAREHOUSE-42");
        assertThat(service.decrypt(second)).isEqualTo("WAREHOUSE-42");
    }

    @Test
    void tamperedCiphertextIsRejectedByGcmIntegrityCheck() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);
        byte[] encrypted = Base64.getDecoder().decode(service.encrypt("WAREHOUSE-42"));
        encrypted[encrypted.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(encrypted);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Échec du déchiffrement AES-256-GCM");
    }

    @Test
    void invalidKeyLengthIsRejectedAtStartup() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new SecretEncryptionService(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactement 32 octets");
    }
}
