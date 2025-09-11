package register;

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
import utils.JwtHelper;

/**
 * Registration handler. Creates user, returning proper HTTP status code and response
 */
public class RegisterUserHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(System.getenv("JWT_KEY").getBytes());

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		Map<String, Object> responseMap = new HashMap<>();
		JSONObject responseBody = new JSONObject();

		try (Connection conn = DriverManager.getConnection(
				System.getenv("DB_URL"), 
				System.getenv("DB_USER"), 
				System.getenv("DB_PASSWORD"))) {

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

			String firstName = body.getString("first_name");
			String lastName = body.getString("last_name");
			String username = body.getString("username");
			String password = body.getString("password");
			String industry = body.getString("industry");
			String userRole = body.getString("user_role");
			String bio = body.optString("bio", null); // optional

			// Hash password
			String hashedPassword = HashingHelper.hashPassword(password);

			// Insert into database
			String sql = "INSERT INTO app_user (first_name, last_name, username, hashed_password, industry, user_role, bio) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING user_id";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, firstName);
				stmt.setString(2, lastName);
				stmt.setString(3, username);
				stmt.setString(4, hashedPassword);
				stmt.setString(5, industry);
				stmt.setString(6, userRole);
				stmt.setString(7, bio);

				ResultSet rs = stmt.executeQuery();
				if (rs.next()) {
					// Retrieve user_id for JWT
					int userId = rs.getInt("user_id");

					// Generate JWT
					String jwt = JwtHelper.generateToken(userId, username);

					responseBody.put("message", "User registered successfully"); // User registered successfully
					responseBody.put("username", username);
					responseBody.put("user_id", userId);
					responseBody.put("token", jwt);
				}
			}
			buildResponse(responseMap, 201, responseBody.toString());

			// Log
			System.out.println("User registered successfully: " + username);

		} catch (SQLException e) {
			if ("23505".equals(e.getSQLState())) { // unique_violation in PostgreSQL
				responseBody.put("error", "Username already exists");
				buildResponse(responseMap, 409, responseBody.toString());

				// Log
				System.err.println("Failed to register user. Username already exists");
			} else {
				responseBody.put("error", "Database error: " + e.getMessage());
				buildResponse(responseMap, 500, responseBody.toString());

				// Log
				System.err.println("Failed to register user. Error in database: " + e.getMessage());
				e.printStackTrace();
			}
		} catch (Exception e) {
			responseBody.put("error", "Internal server error: " + e.getMessage());
			buildResponse(responseMap, 500, responseBody.toString());

			// Log
			System.err.println("Failed to register user. Error in RegisterUserHandler: " + e.getMessage());
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