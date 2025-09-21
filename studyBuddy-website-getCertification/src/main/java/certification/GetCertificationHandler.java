package certification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import io.jsonwebtoken.Claims;

import java.sql.*;
import java.util.Map;

import utils.JwtHelper;
import utils.JwtValidationException;

/**
 * Retrieves user_cert data tied to the authenticated user.
 * Requires a JWT token in the request header.
 * Optionally filters by user_cert_id if provided in query params.
 */
public class GetCertificationHandler implements RequestHandler<Map<String, Object>, String> {

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

			// Optional user_cert_id from query string
			Long userCertId = null;
			@SuppressWarnings("unchecked")
			Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
			if (queryParams != null && queryParams.get("user_cert_id") != null) {
				try {
					userCertId = Long.parseLong(queryParams.get("user_cert_id"));
				} catch (NumberFormatException nfe) {
					return errorResponse(400, "Invalid user_cert_id parameter").toString();
				}
			}

			// Build SQL with join
			String sql = "SELECT uc.user_cert_id, uc.user_id, uc.certification_id, uc.status, " +
					"uc.earned_on, uc.expires_on, uc.ce_hours_required, uc.ce_hours_completed, " +
					"c.cert_name, c.provider, c.cert_description, c.renewal_period_months " +
					"FROM user_cert uc " +
					"JOIN certification c ON uc.certification_id = c.certification_id " +
					"WHERE uc.user_id = ?";

			if (userCertId != null) {
				sql += " AND uc.user_cert_id = ?";
			}

			JSONArray results = new JSONArray();
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setLong(1, userId);
				if (userCertId != null) {
					stmt.setLong(2, userCertId);
				}

				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						JSONObject cert = new JSONObject();
						cert.put("user_cert_id", rs.getLong("user_cert_id"));
						cert.put("user_id", rs.getLong("user_id"));
						cert.put("certification_id", rs.getLong("certification_id"));
						cert.put("status", rs.getString("status"));
						cert.put("earned_on", rs.getDate("earned_on"));
						cert.put("expires_on", rs.getDate("expires_on"));
						cert.put("ce_hours_required", rs.getInt("ce_hours_required"));
						cert.put("ce_hours_completed", rs.getInt("ce_hours_completed"));
						// joined fields
						cert.put("cert_name", rs.getString("cert_name"));
						cert.put("provider", rs.getString("provider"));
						cert.put("cert_description", rs.getString("cert_description"));
						cert.put("renewal_period_months", rs.getInt("renewal_period_months"));
						results.put(cert);
					}
				}
			}

			response.put("statusCode", 200);
			response.put("body", new JSONObject()
					.put("user_certifications", results)
					.toString());

			System.out.println("Retrieved " + results.length() + " user_certifications for user " + userId);

		} catch (JwtValidationException e) {
			// Expired token
		    response = new JSONObject();
		    response.put("statusCode", e.getStatusCode());
		    response.put("body", new JSONObject()
		            .put("error", e.getMessage())
		            .toString());
		    // Log
		    System.out.println("JWT error: ");
		    e.printStackTrace();
		    
		    return response.toString();
		} catch (Exception e) {
			response = errorResponse(500, e.getMessage());
			
			// Log
			System.err.println("Error in LoginHandler: " + e.getMessage());
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