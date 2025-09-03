package authenticate;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import utils.HashingHelper;

/**
 * Login handler. Checks if user information is correct and returns HTTP status code and response.
 */
public class LoginHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final String DB_URL = System.getenv("DB_URL");
	private static final String DB_USER = System.getenv("DB_USER");
	private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> responseMap = new HashMap<>();
        JSONObject responseBody = new JSONObject();

		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
			JSONObject body = new JSONObject((String) event.get("body"));
			String username = body.getString("username");
			String password = body.getString("password");

			String query = "SELECT hashed_password FROM app_user WHERE username = ?";
			try (PreparedStatement stmt = conn.prepareStatement(query)) {
				stmt.setString(1, username);
				ResultSet rs = stmt.executeQuery();

				if (rs.next()) {
                    String storedHash = rs.getString("hashed_password");

                    if (HashingHelper.verifyPassword(password, storedHash)) {
                        responseBody.put("message", "Login successful");
                        buildResponse(responseMap, 200, responseBody.toString());
                    } else {
                        responseBody.put("error", "Invalid credentials");
                        buildResponse(responseMap, 401, responseBody.toString());
                    }
                } else {
                    responseBody.put("error", "User not found");
                    buildResponse(responseMap, 404, responseBody.toString());
                }
            }
        } catch (Exception e) {
            responseBody.put("error", "Internal server error: " + e.getMessage());
            buildResponse(responseMap, 500, responseBody.toString());
        }

        return responseMap;
	}
	
	private void buildResponse(Map<String, Object> responseMap, int statusCode, String body) {
        responseMap.put("statusCode", statusCode);
        responseMap.put("headers", Map.of("Content-Type", "application/json"));
        responseMap.put("body", body);
    }
}
