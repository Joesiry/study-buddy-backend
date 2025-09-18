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
 * Handler to update a user's profile information.
 */
public class UpdateUserHandler implements RequestHandler<Map<String, Object>, String> {

	@Override
	public String handleRequest(Map<String, Object> event, Context context) {
		JSONObject response = new JSONObject();

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

			// Extract JWT and decode
			String token = body.optString("token", null);
			if (token == null) {
				return errorResponse(400, "Missing JWT token").toString();
			}

			Claims claims = JwtHelper.parseToken(token);
			long userId = Long.parseLong(claims.getSubject());

			// Collect update fields
			String firstName = body.optString("first_name", null);
			String lastName = body.optString("last_name", null);
			String industry = body.optString("industry", null);
			String userRole = body.optString("user_role", null);
			String bio = body.optString("bio", null);

			// Dynamically build SQL
			StringBuilder sql = new StringBuilder("UPDATE app_user SET ");
			boolean first = true;

			if (firstName != null) {
				sql.append("first_name = ?");
				first = false;
			}
			if (lastName != null) {
				if (!first) sql.append(", ");
				sql.append("last_name = ?");
				first = false;
			}
			if (industry != null) {
				if (!first) sql.append(", ");
				sql.append("industry = ?");
				first = false;
			}
			if (userRole != null) {
				if (!first) sql.append(", ");
				sql.append("user_role = ?");
				first = false;
			}
			if (bio != null) {
				if (!first) sql.append(", ");
				sql.append("bio = ?");
				first = false;
			}

			if (first) {
				// Nothing to update
				return errorResponse(400, "No fields provided to update").toString();
			}

			sql.append(" WHERE user_id = ?");

			try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
				// Add parameters as needed
				int paramIndex = 1;
				if (firstName != null) stmt.setString(paramIndex++, firstName);
				if (lastName != null) stmt.setString(paramIndex++, lastName);
				if (industry != null) stmt.setString(paramIndex++, industry);
				if (userRole != null) stmt.setString(paramIndex++, userRole);
				if (bio != null) stmt.setString(paramIndex++, bio);

				stmt.setLong(paramIndex, userId);

				int rowsUpdated = stmt.executeUpdate();
				if (rowsUpdated == 0) {
					return errorResponse(404, "User not found").toString();
				}
			}

			// Fetch updated record
			String fetchSql = "SELECT user_id, first_name, last_name, username, industry, user_role, bio FROM app_user WHERE user_id = ?";
			try (PreparedStatement stmt = conn.prepareStatement(fetchSql)) {
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
					System.out.println("Updated user info for user_id: " + userId);
				} else {
					return errorResponse(404, "User not found after update").toString();
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

			// Log
			System.err.println("Error in UpdateUserHandler: " + e.getMessage());
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
