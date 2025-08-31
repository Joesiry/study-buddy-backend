package authenticate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Helper function for LoginHandler class. Handles the hashing of passwords.
 */
public class HashingHelper {

	public static String hashPassword(String password, String salt) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = md.digest((salt + password).getBytes());
			return Base64.getEncoder().encodeToString(hashBytes);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Hashing algorithm not found", e);
		}
	}

	public static boolean checkPassword(String password, String salt, String storedHash) {
		String hashOfInput = hashPassword(password, salt);
		return storedHash.equals(hashOfInput);
	}
}
