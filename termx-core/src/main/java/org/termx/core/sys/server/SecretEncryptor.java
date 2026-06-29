package org.termx.core.sys.server;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Encrypts terminology-server secrets (OAuth2 client secret, basic-auth password, api key) at rest.
 *
 * <p>Encryption is opt-in: configure {@code termx.server.secret-encryption-key} with a Base64-encoded
 * 128/192/256-bit AES key. When the key is absent the encryptor is a no-op (values are stored as-is),
 * preserving the previous plaintext behaviour and existing installations.
 *
 * <p>Encrypted values are tagged with the {@link #PREFIX} marker so {@link #encrypt} is idempotent and
 * {@link #decrypt} can transparently pass through legacy plaintext values.
 */
@Singleton
@Slf4j
public class SecretEncryptor {
  static final String PREFIX = "enc:v1:";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SecretEncryptor(@Value("${termx.server.secret-encryption-key:}") String base64Key) {
    if (StringUtils.isBlank(base64Key)) {
      this.key = null;
      log.info("termx.server.secret-encryption-key not set; terminology-server secrets stored unencrypted");
    } else {
      this.key = new SecretKeySpec(Base64.getDecoder().decode(base64Key.trim()), "AES");
    }
  }

  public boolean isEnabled() {
    return key != null;
  }

  /** Encrypts a plaintext value. No-op if encryption is disabled or the value is blank/already encrypted. */
  public String encrypt(String plain) {
    if (key == null || StringUtils.isBlank(plain) || plain.startsWith(PREFIX)) {
      return plain;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
      return PREFIX + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new RuntimeException("Failed to encrypt secret", e);
    }
  }

  /** Decrypts a value produced by {@link #encrypt}. Returns the input unchanged if it is not encrypted. */
  public String decrypt(String stored) {
    if (StringUtils.isBlank(stored) || !stored.startsWith(PREFIX)) {
      return stored;
    }
    if (key == null) {
      throw new IllegalStateException("Encrypted secret found but termx.server.secret-encryption-key is not configured");
    }
    try {
      byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      byte[] iv = new byte[IV_LENGTH];
      System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("Failed to decrypt secret", e);
    }
  }
}
