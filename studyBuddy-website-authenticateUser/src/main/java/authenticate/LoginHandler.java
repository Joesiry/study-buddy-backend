package authenticate;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import utils.HashingHelper;
import utils.JwtHelper;
import utils.JwtValidationException;

/**
 * Login handler. Checks if user information is correct and returns HTTP status code and response.
 */
public class LoginHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		Map<String, Object> response = new HashMap<>();

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
                        String jwt = JwtHelper.generateToken(userId, username);

                        response.put("statusCode", 200);
                        response.put("body", new JSONObject()
                                .put("message", "Login successful")
                                .put("token", jwt)
                                .toString());

						// Log
						System.out.println("Login successful for user: " + username);
					} else {
                        response.put("statusCode", 401);
                        response.put("body", "{\"message\":\"Invalid credentials\"}");

						// Log
						System.out.println("Invalid password for user: " + username);
					}
				} else {
                    response.put("statusCode", 404);
                    response.put("body", "{\"message\":\"User not found\"}");

					// Log
					System.out.println("User not found: " + username);
				}
			}
		}  /*catch (JwtValidationException e) { // FUTURE
            response.put("statusCode", e.getStatusCode());
            response.put("body", new JSONObject()
                    .put("error", e.getMessage())
                    .toString());

        }*/ catch (Exception e) {
            response.put("statusCode", 500);
            response.put("body", new JSONObject()
                    .put("error", "Internal server error")
                    .put("details", e.getMessage())
                    .toString());
        }

        return response;
	}

}
