package authenticate;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Login handler. Checks if user information is correct and returns HTTP status code and response.
 */
public class LoginHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		Map<String, Object> response = new HashMap<>();

		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
			JSONObject body = new JSONObject((String) input.get("body"));
			String username = body.getString("username");
			String password = body.getString("password");

			String query = "SELECT password_hash, salt FROM users WHERE username = ?";
			try (PreparedStatement stmt = conn.prepareStatement(query)) {
				stmt.setString(1, username);
				ResultSet rs = stmt.executeQuery();

				if (rs.next()) {
					String storedHash = rs.getString("password_hash");
					String salt = rs.getString("salt");

					if (HashingHelper.checkPassword(password, salt, storedHash)) {
						response.put("statusCode", 200);
						response.put("body", "{\"message\":\"Login successful\"}"); // Credentials correct
					} else {
						response.put("statusCode", 401);
						response.put("body", "{\"message\":\"Invalid credentials\"}"); // Credentials incorrect
					}
				} else {
					response.put("statusCode", 404);
					response.put("body", "{\"message\":\"User not found\"}"); // No user found
				}
			}
		} catch (Exception e) {
			response.put("statusCode", 500);
			response.put("body", "{\"error\":\"" + e.getMessage() + "\"}"); // Big error in code
		}

		return response;
	}
}
