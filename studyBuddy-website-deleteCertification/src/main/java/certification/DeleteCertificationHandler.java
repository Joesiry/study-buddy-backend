package certification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.jsonwebtoken.Claims;
import org.json.JSONObject;
import utils.JwtHelper;
import utils.JwtValidationException;

import java.sql.*;
import java.util.Map;

/**
 * Deletes user_certs.
 * Optionally takes user_cert_id to delete specific one.
 */
public class DeleteCertificationHandler implements RequestHandler<Map<String, Object>, String> {

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
				token = headers.get("authorization");
			}
			if (token == null) {
				return errorResponse(400, "Missing JWT token in Authorization header").toString();
			}

			Claims claims = JwtHelper.parseToken(token);
			long user_id = Long.parseLong(claims.getSubject());

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

			int rowsAffected = 0;

			if (userCertId != null) { // Delete a specific user_cert
				String sql = "DELETE FROM user_cert WHERE user_id = ? AND user_cert_id = ?";
				try (PreparedStatement stmt = conn.prepareStatement(sql)) {
					stmt.setLong(1, user_id);
					stmt.setLong(2, userCertId);
					rowsAffected = stmt.executeUpdate();
				}
			} else { // Delete all user_certs for this user
				String sql = "DELETE FROM user_cert WHERE user_id = ?";
				try (PreparedStatement stmt = conn.prepareStatement(sql)) {
					stmt.setLong(1, user_id);
					rowsAffected = stmt.executeUpdate();
				}
			}

			if (rowsAffected > 0) {
				response.put("statusCode", 200);
				response.put("body", new JSONObject()
						.put("message", "Delete successful")
						.put("rows_deleted", rowsAffected)
						.toString());
				System.out.println("Rows deleted: " + rowsAffected);
			} else {
				return errorResponse(404, "No records found to delete").toString();
			}

		} catch (JwtValidationException e) {
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
			System.err.println("Error in DeleteCertificationHandler: " + e.getMessage());
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
