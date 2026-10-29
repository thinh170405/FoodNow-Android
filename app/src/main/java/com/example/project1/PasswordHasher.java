package com.example.project1;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted, slow password hashes; never persist the password itself. */
public final class PasswordHasher {
    private static final int ITERATIONS = 210000;
    private PasswordHasher() {}
    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return ITERATIONS + ":" + hex(salt) + ":" + hex(derive(password, salt, ITERATIONS));
    }
    public static boolean verify(String password, String encoded) {
        try {
            String[] parts = encoded.split(":");
            if (parts.length != 3) return false;
            int iterations = Integer.parseInt(parts[0]);
            if (iterations != ITERATIONS) return false;
            return MessageDigest.isEqual(unhex(parts[2]), derive(password, unhex(parts[1]), iterations));
        } catch (IllegalArgumentException e) { return false; }
    }
    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Password hashing unavailable", e); }
        finally { spec.clearPassword(); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    private static byte[] unhex(String value) {
        if (value.length() % 2 != 0) throw new IllegalArgumentException();
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
