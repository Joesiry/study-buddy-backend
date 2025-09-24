package certification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.jsonwebtoken.Claims;
import utils.JwtHelper;
import utils.JwtValidationException;

import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDate;
import java.util.Map;

/**
 * Certification updater handler. Updates user_certs.
 */
public class UpdateCertificationHandler implements RequestHandler<Map<String, Object>, String> {

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

			// Extract JWT token from headers
			@SuppressWarnings("unchecked")
			Map<String, String> headers = (Map<String, String>) event.get("headers");
			if (headers == null) {
				return errorResponse(400, "Missing headers").toString();
			}

			String token = headers.get("Authorization");
			if (token == null) {
				token = headers.get("authorization");
			}
			if (token == null) {
				return errorResponse(400, "Missing JWT token in Authorization header").toString();
			}

			Claims claims = JwtHelper.parseToken(token);
			long user_id = Long.parseLong(claims.getSubject());

			// Turn dates into correct format
			Date earned_on = null;
			if (body.has("earned_on") && !body.isNull("earned_on")) {
				earned_on = Date.valueOf(LocalDate.parse(body.getString("earned_on")));
			}
			Date expires_on = null;
			if (body.has("expires_on") && !body.isNull("expires_on")) {
				expires_on = Date.valueOf(LocalDate.parse(body.getString("expires_on")));
			}

			int ce_hours_required = body.optInt("ce_hours_required", 0);
			int ce_hours_completed = body.optInt("ce_hours_completed", 0);
			Long user_cert_id = body.getLong("user_cert_id");

			String sql = "UPDATE user_cert SET earned_on=?, expires_on=?, ce_hours_required=?, ce_hours_completed=? " +
					"WHERE user_cert_id=? AND user_id=?";

			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				if (earned_on != null) stmt.setDate(1, earned_on); else stmt.setNull(3, java.sql.Types.DATE);
				if (expires_on != null) stmt.setDate(2, expires_on); else stmt.setNull(4, java.sql.Types.DATE);
				stmt.setInt(3, ce_hours_required);
				stmt.setInt(4, ce_hours_completed);
				stmt.setLong(5, user_cert_id);
				stmt.setLong(6, user_id);

				int rows = stmt.executeUpdate();
				if (rows > 0) {
					response.put("statusCode", 200);
					response.put("body", new JSONObject()
							.put("message", "User_cert updated successfully")
							.put("user_cert_id", user_cert_id)
							.toString());
					// Log
					System.out.println("Updated user_cert (ID: " + user_cert_id + ")");
				} else {
					// Log
					System.out.println("Failed to update user_cert, not found or not owned by user");

					throw new Exception("User_cert not found or not owned by user");
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

			System.err.println("Error in UpdateCertificationHandler: " + e.getMessage());
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
