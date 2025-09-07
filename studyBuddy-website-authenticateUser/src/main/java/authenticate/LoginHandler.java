package authenticate;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;

import utils.HashingHelper;

/**
 * Login handler. Checks if user information is correct and returns HTTP status code and response.
 */
public class LoginHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
	private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(System.getenv("JWT_KEY").getBytes());

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		Map<String, Object> responseMap = new HashMap<>();
		JSONObject responseBody = new JSONObject();

		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
			// Accept both API Gateway (body as string) and direct JSON (fields at top level)
			JSONObject body;
			Object bodyObj = event.get("body");
			if (bodyObj instanceof String) {
				// API Gateway: body is a JSON string
				body = new JSONObject((String) bodyObj);
			} else if (bodyObj instanceof Map) {
				// Local/direct: body is already a map
				body = new JSONObject((Map<?, ?>) bodyObj);
			} else if (bodyObj == null) {
				// No "body" key, treat event itself as the body (for local direct JSON)
				body = new JSONObject(event);
			} else {
				throw new IllegalArgumentException("Invalid event format");
			}


			String username = body.getString("username");
			String password = body.getString("password");

			String query = "SELECT user_id, hashed_password FROM app_user WHERE username = ?";
			try (PreparedStatement stmt = conn.prepareStatement(query)) {
				stmt.setString(1, username);
				ResultSet rs = stmt.executeQuery();

				if (rs.next()) {
					String storedHash = rs.getString("hashed_password");
					int userId = rs.getInt("user_id");

					if (HashingHelper.verifyPassword(password, storedHash)) {
						// Generate JWT
						String jwt = Jwts.builder()
								.subject(String.valueOf(userId))
								.claim("username", username)
								.issuedAt(new Date())
								.expiration(new Date(System.currentTimeMillis() + 3600_000)) // 1 hour
								.signWith(SECRET_KEY)
								.compact();

						responseBody.put("message", "Login successful");
						responseBody.put("username", username);
						responseBody.put("user_id", userId);
						responseBody.put("token", jwt);
						buildResponse(responseMap, 200, responseBody.toString());

						// Log
						System.out.println("Login successful for user: " + username);
					} else {
						responseBody.put("error", "Invalid credentials");
						buildResponse(responseMap, 401, responseBody.toString());

						// Log
						System.out.println("Invalid password for user: " + username);
					}
				} else {
					responseBody.put("error", "User not found");
					buildResponse(responseMap, 404, responseBody.toString());

					// Log
					System.out.println("User not found: " + username);
				}
			}
		} catch (Exception e) {
			responseBody.put("error", "Internal server error: " + e.getMessage());
			buildResponse(responseMap, 500, responseBody.toString());

			// Log
			System.out.println("Error in LoginHandler: " + e.getMessage());
			e.printStackTrace();
		}

		return responseMap;
	}

	private void buildResponse(Map<String, Object> responseMap, int statusCode, String body) {
		responseMap.put("statusCode", statusCode);
		responseMap.put("headers", Map.of("Content-Type", "application/json"));
		responseMap.put("body", body);
	}
}
