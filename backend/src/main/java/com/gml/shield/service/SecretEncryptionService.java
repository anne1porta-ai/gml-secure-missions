package com.gml.shield.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffre et déchiffre les codes secrets des missions avec AES-256-GCM.
 */
@Service
public class SecretEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptionService(@Value("${app.encryption.secret-key}") String secretKeyBase64) {
        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("ENCRYPTION_KEY doit être une valeur Base64 valide", exception);
        }

        // Ne jamais accepter une clé de secours ou tronquée : 32 octets sont
        // nécessaires pour utiliser AES-256 avec la clé fournie par la configuration.
        if (keyBytes.length != 32) {
            throw new IllegalStateException("ENCRYPTION_KEY doit contenir exactement 32 octets pour AES-256");
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String value) {
        if (value == null) {
            return null;
        }

        try {
            // Un IV aléatoire est généré pour chaque chiffrement : deux codes identiques
            // ne produisent ainsi pas le même résultat chiffré.
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            // GCM chiffre le contenu ET produit une étiquette d'intégrité.
            // La modification du texte chiffré devra être détectée au déchiffrement.
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            // L'IV n'est pas secret : il est stocké avec le texte chiffré afin de pouvoir
            // reconstruire exactement les paramètres nécessaires au déchiffrement.
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            // Base64 facilite le stockage des octets dans une colonne texte ;
            // la confidentialité vient d'AES-GCM, pas de cet encodage.
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Échec du chiffrement AES-256-GCM", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Donnée chiffrée invalide");
            }

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            // doFinal vérifie l'étiquette GCM : si les octets ont été altérés,
            // aucun code d'entrepôt n'est restitué.
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(encrypted);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Échec du déchiffrement AES-256-GCM", exception);
        }
    }
}
