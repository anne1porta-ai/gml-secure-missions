package com.gml.shield.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretEncryptionServiceErrorTest {

  private static final String KEY =
      Base64.getEncoder()
          .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  @Test
  void keyThatIsNotBase64IsRejectedAtStartup() {
    assertThatThrownBy(() -> new SecretEncryptionService("cle-non-base64!"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Base64 valide");
  }

  @Test
  void nullValueIsNeitherEncryptedNorDecrypted() {
    SecretEncryptionService service = new SecretEncryptionService(KEY);

    assertThat(service.encrypt(null)).isNull();
    assertThat(service.decrypt(null)).isNull();
  }

  @Test
  void encryptedValueTooShortToContainTheIvIsRejected() {
    SecretEncryptionService service = new SecretEncryptionService(KEY);
    String tooShort = Base64.getEncoder().encodeToString(new byte[5]);

    assertThatThrownBy(() -> service.decrypt(tooShort))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Échec du déchiffrement AES-256-GCM");
  }
}
