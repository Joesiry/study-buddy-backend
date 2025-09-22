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
			String sql = "SELECT uc.* FROM user_cert uc WHERE uc.user_id = ?";
			if (userCertId != null) { // Optional filter
				sql += " AND uc.user_cert_id = ?";
			}

			JSONArray results = new JSONArray();
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setLong(1, userId);
				if (userCertId != null) {
					stmt.setLong(2, userCertId);
				}

				try (ResultSet rs = stmt.executeQuery()) {
					ResultSetMetaData meta = rs.getMetaData();
					int columnCount = meta.getColumnCount();

					while (rs.next()) {
						JSONObject cert = new JSONObject();
						for (int i = 1; i <= columnCount; i++) {
							String columnName = meta.getColumnLabel(i);
							Object value = rs.getObject(i);
							cert.put(columnName, value);
						}
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