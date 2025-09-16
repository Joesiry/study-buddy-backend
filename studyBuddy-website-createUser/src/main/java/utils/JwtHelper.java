package utils;

import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Utility class for generating and validating JWT tokens.
 * Uses plain text secret key.
 */
public class JwtHelper {

	private static final String SECRET_ENV_VAR = "JWT_KEY"; // Environment variable name
	private static final SecretKey SECRET_KEY;

	static {
		String secret = System.getenv(SECRET_ENV_VAR);

		if (secret == null || secret.isEmpty()) {
			throw new IllegalStateException("JWT_KEY environment variable is not set");
		}

		// Since JWT_KEY is plain text, use .getBytes()
		// If switching to Base64 encoding, use Base64.getDecoder().decode(secret)
		SECRET_KEY = Keys.hmacShaKeyFor(secret.getBytes());
	}

	/**
	 * Generate a JWT with userId, username, and optional claims
	 * @param userId
	 * @param username
	 * @param extraClaims
	 * @return
	 */
	public static String generateToken(int userId, String username, Map<String, Object> extraClaims) {
		long expirationMs = 3600_000; // 1 hour

		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("username", username)
				.claims(extraClaims) // optional additional claims (for future)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + expirationMs))
				.signWith(SECRET_KEY)
				.compact();
	}

	/**
	 * Overload method if no extra claims are needed
	 * @param userId
	 * @param username
	 * @return
	 */
	public static String generateToken(int userId, String username) {
		return generateToken(userId, username, Map.of());
	}

	// Parse & validate JWT, return Claims
	public static Claims parseToken(String token) throws JwtValidationException {
		try {
			return Jwts.parser()
					.verifyWith(SECRET_KEY)  // validates the signature
					.build()
					.parseSignedClaims(token) // throws JwtException if invalid/expired
					.getPayload();
		} catch (ExpiredJwtException e) {
			// Token expired 401 Unauthorized
			throw new JwtValidationException("Token expired", 401);

		} catch (JwtException e) {
			throw new JwtValidationException("Invalid token", 403);
		}
	}

	// Validate token
	public static boolean validateToken(String token) throws JwtValidationException {
		try {
			Jwts.parser()
			.verifyWith(SECRET_KEY)
			.build()
			.parseSignedClaims(token);
			return true;
		} catch (ExpiredJwtException e) {
			// Token expired 401 Unauthorized
			throw new JwtValidationException("Token expired", 401);

		} catch (JwtException e) {
			throw new JwtValidationException("Invalid token", 403);
		}
	}
}
