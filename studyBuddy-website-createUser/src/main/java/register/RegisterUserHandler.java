package register;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import utils.HashingHelper;

public class RegisterUserHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    	Map<String, Object> responseMap = new HashMap<>();
        JSONObject responseBody = new JSONObject();

        try (Connection conn = DriverManager.getConnection(
                System.getenv("DB_URL"), 
                System.getenv("DB_USER"), 
                System.getenv("DB_PASSWORD"))) {

            // Parse input from API Gateway
            JSONObject body = new JSONObject((String) event.get("body"));
            
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
            String sql = "INSERT INTO app_user (first_name, last_name, username, hashed_password, industry, user_role, bio) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, firstName);
                stmt.setString(2, lastName);
                stmt.setString(3, username);
                stmt.setString(4, hashedPassword);
                stmt.setString(5, industry);
                stmt.setString(6, userRole);
                stmt.setString(7, bio);
                stmt.executeUpdate();
            }

            responseBody.put("message", "User registered successfully"); // User registered successfully
            buildResponse(responseMap, 200, responseBody.toString());

        } catch (SQLException e) {
        	if ("23505".equals(e.getSQLState())) { // unique_violation in PostgreSQL
        		responseBody.put("error", "Username already exists");
        		buildResponse(responseMap, 409, responseBody.toString());
        	} else {
        		responseBody.put("error", "Database error: " + e.getMessage());
        		buildResponse(responseMap, 500, responseBody.toString());
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