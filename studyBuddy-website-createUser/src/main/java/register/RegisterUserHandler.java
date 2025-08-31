package register;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Base64;
import java.util.Map;

public class RegisterUserHandler implements RequestHandler<Map<String, Object>, String> {

    // 🔹 Hash password with SHA-256 (not as secure as BCrypt, but built-in)
    private String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hash); // store as Base64 string
    }

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        JSONObject response = new JSONObject();

        try (Connection conn = DriverManager.getConnection(
                System.getenv("DB_URL"), 
                System.getenv("DB_USER"), 
                System.getenv("DB_PASSWORD"))) {

            // Parse input from API Gateway
            JSONObject body = new JSONObject((String) event.get("body"));
            String username = body.getString("username");
            String password = body.getString("password");

            // Hash password
            String hashedPassword = hashPassword(password);

            // Insert into database
            String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, hashedPassword);
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