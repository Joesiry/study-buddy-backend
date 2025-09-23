package user;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.jsonwebtoken.Claims;
import utils.JwtHelper;
import utils.JwtValidationException;

import org.json.JSONObject;

import java.sql.*;
import java.util.Map;

/**
 * Handler to fetch and return a user's information.
 */
public class UserInfoHandler implements RequestHandler<Map<String, Object>, String> {

	@Override
	public String handleRequest(Map<String, Object> event, Context context) {
		JSONObject response = new JSONObject();

		try (Connection conn = DriverManager.getConnection(
				System.getenv("DB_URL"),
				System.getenv("DB_USER"),
				System.getenv("DB_PASSWORD"))) {

			// Extract JWT token from headers
			@SuppressWarnings("unchecked")
			Map<String, String> headers = (Map<String, String>) event.get("headers");
			if (headers == null) {
				return errorResponse(400, "Missing headers").toString();
			}

			String token = headers.get("Authorization");
			if (token == null) {
				return errorResponse(400, "Missing JWT token in Authorization header").toString();
			}

			Claims claims = JwtHelper.parseToken(token);
			long userId = Long.parseLong(claims.getSubject());

			// Query app_user for user
			String sql = "SELECT user_id, first_name, last_name, username, industry, user_role, bio " +
					"FROM app_user WHERE user_id = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setLong(1, userId);
				ResultSet rs = stmt.executeQuery();

				if (rs.next()) {
					JSONObject userJson = new JSONObject()
							.put("user_id", rs.getLong("user_id"))
							.put("first_name", rs.getString("first_name"))
							.put("last_name", rs.getString("last_name"))
							.put("username", rs.getString("username"))
							.put("industry", rs.getString("industry"))
							.put("user_role", rs.getString("user_role"))
							.put("bio", rs.getString("bio"));

					response.put("statusCode", 200);
					response.put("body", userJson.toString());

					// Log
					System.out.println("Fetched user info for user_id: " + userId);
				} else {
					System.err.println("Failed to fetch user info for user_id: " + userId);
					return errorResponse(404, "User not found").toString();
				}
			}

		} catch (JwtValidationException e) {
		    response = new JSONObject();
		    response.put("statusCode", e.getStatusCode());
		    response.put("body", new JSONObject()
		            .put("error", e.getMessage())
		            .toString());
		    
		    // Log
		    System.out.println("JWT error: " + e.getMessage());
		    
		    return response.toString();

		} catch (Exception e) {
			response.put("statusCode", 500);
			response.put("body", new JSONObject()
					.put("error", "Internal server error")
					.put("details", e.getMessage())
					.toString());

			//Log
			System.err.println("Error in GetUserHandler: " + e.getMessage());
			e.printStackTrace();
		}

		return response.toString();
	}

	private JSONObject errorResponse(int code, String message) {
		JSONObject resp = new JSONObject();
		resp.put("statusCode", code);
		resp.put("body", new JSONObject()
				.put("error", message)
				.toString());
		return resp;
	}
}
