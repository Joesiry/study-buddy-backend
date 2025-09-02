package utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utility class for hashing and verifying passwords.
 * Uses SHA-256 with Base64 encoding (matches RegisterUserHandler).
 */
public class HashingHelper {

    // Hash a plain password using SHA-256
    public static String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    // Verify a plain password against a stored hash
    public static boolean verifyPassword(String plainPassword, String storedHash) throws NoSuchAlgorithmException {
        String hashedInput = hashPassword(plainPassword);
        return hashedInput.equals(storedHash);
    }
}
