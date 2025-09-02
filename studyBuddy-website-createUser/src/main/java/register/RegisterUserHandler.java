package register;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.sql.*;
import java.util.Map;

import utils.HashingHelper;

public class RegisterUserHandler implements RequestHandler<Map<String, Object>, String> {
	
    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        JSONObject response = new JSONObject();

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
            String industry = body.optString("industry", null); // optional
            String userRole = body.optString("user_role", null); // optional
            String bio = body.optString("bio", null); // optional

            // Hash password
            String hashedPassword = HashingHelper.hashPassword(password);

            // Insert into database
            String sql = "INSERT INTO users (first_name, last_name, username, hashed_password, industry, user_role, bio) VALUES (?, ?, ?, ?, ?, ?, ?)";
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

            response.put("message", "User registered successfully"); // User registered successfully

        } catch (SQLIntegrityConstraintViolationException e) {
            response.put("error", "Username already exists"); // Username taken
        } catch (Exception e) {
            response.put("error", e.getMessage()); // Big error
        }

        return response.toString();
    }
}