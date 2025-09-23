package certification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.jsonwebtoken.Claims;
import utils.JwtHelper;
import utils.JwtValidationException;

import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDate;
import java.sql.Date;
import java.util.Map;

/**
 * Certification creator handler. Creates user_certs and returns proper HTTP status code and response.
 */
public class CreateCertificationHandler implements RequestHandler<Map<String, Object>, String> {

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
				return errorResponse(400, "Missing JWT token in Authorization header").toString();
			}

			Claims claims = JwtHelper.parseToken(token);
			long user_id = Long.parseLong(claims.getSubject());
			
			String title = body.getString("title");
			String uid = body.getString("uid");
			String description = body.optString("description", null);
			String cert_level = body.getString("cert_level");
			
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

			// Insert into user_certification table
			String sql = "INSERT INTO user_cert (user_id, title, uid, description, cert_level, earned_on, expires_on, ce_hours_required, ce_hours_completed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING user_cert_id";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setLong(1, user_id);
				stmt.setString(2, title);
				stmt.setString(3, uid);
				stmt.setString(4, description);
				stmt.setString(5, cert_level);
				if (earned_on != null) {
					stmt.setDate(6, earned_on);
				} else {
					stmt.setNull(6, java.sql.Types.DATE);
				}
				if (expires_on != null) {
					stmt.setDate(7, expires_on);
				} else {
					stmt.setNull(7, java.sql.Types.DATE);
				}
				stmt.setInt(8, ce_hours_required);
				stmt.setInt(9, ce_hours_completed);

				ResultSet rs = stmt.executeQuery();
				rs.next();
				long newId = rs.getLong("user_cert_id");

				response.put("statusCode", 200);
				response.put("body", new JSONObject()
						.put("message", "User_cert created successfully")
						.put("user_cert_id", newId)
						.toString());

				// Log
				System.out.println("Created certification (ID: " + newId + ")");
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
			System.err.println("Error in CreateCertificationHandler: " + e.getMessage());
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
