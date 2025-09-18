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
 * Certification updater handler. Updates user_certs or certifications.
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

			String type = body.getString("type");

			if ("user_certification".equals(type)) {
				updateUserCert(conn, body, response);
			} else if ("certification".equals(type)) {
				updateCertification(conn, body, response);
			} else {
				return errorResponse(400, "Unknown type: " + type).toString();
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

	private void updateUserCert(Connection conn, JSONObject body, JSONObject response) throws Exception {
		// JWT decoding
		String token = body.optString("token", null);
		if (token == null) {
			throw new IllegalArgumentException("Missing JWT token");
		}
		Claims claims = JwtHelper.parseToken(token);
		long user_id = Long.parseLong(claims.getSubject());

		long user_cert_id = body.getLong("user_cert_id");
		int certification_id = body.getInt("certification_id");
		String status = body.optString("status", null);

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

		String sql = "UPDATE user_cert SET certification_id=?, status=?, earned_on=?, expires_on=?, ce_hours_required=?, ce_hours_completed=? " +
				"WHERE user_cert_id=? AND user_id=?";

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setInt(1, certification_id);
			stmt.setString(2, status);
			if (earned_on != null) stmt.setDate(3, earned_on); else stmt.setNull(3, java.sql.Types.DATE);
			if (expires_on != null) stmt.setDate(4, expires_on); else stmt.setNull(4, java.sql.Types.DATE);
			stmt.setInt(5, ce_hours_required);
			stmt.setInt(6, ce_hours_completed);
			stmt.setLong(7, user_cert_id);
			stmt.setLong(8, user_id);

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
	}

	private void updateCertification(Connection conn, JSONObject body, JSONObject response) throws Exception {
		long certification_id = body.getLong("certification_id");
		int domain_id = body.getInt("domain_id");
		String cert_name = body.getString("cert_name");
		String provider = body.optString("provider", null);
		String cert_description = body.optString("cert_description", null);
		int renewal_period_months = body.optInt("renewal_period_months", 0);

		String sql = "UPDATE certification SET domain_id=?, cert_name=?, provider=?, cert_description=?, renewal_period_months=? " +
				"WHERE certification_id=?";

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setInt(1, domain_id);
			stmt.setString(2, cert_name);
			stmt.setString(3, provider);
			stmt.setString(4, cert_description);
			stmt.setInt(5, renewal_period_months);
			stmt.setLong(6, certification_id);

			int rows = stmt.executeUpdate();
			if (rows > 0) {
				response.put("statusCode", 200);
				response.put("body", new JSONObject()
						.put("message", "Certification updated successfully")
						.put("certification_id", certification_id)
						.toString());
				// Log
				System.out.println("Updated certification (ID: " + certification_id + ")");
			} else {
				//Log
				System.out.println("Failed to update certification, not found.");
				
				throw new Exception("Certification not found");
			}
		}
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
