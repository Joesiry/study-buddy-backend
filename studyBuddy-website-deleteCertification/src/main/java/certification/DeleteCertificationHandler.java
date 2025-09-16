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
 * Deletes user_certs or certifications.
 * If type=user_cert: requires user_cert_id (or none to delete all for user).
 * If type=certification: requires certification_id.
 */
public class DeleteCertificationHandler implements RequestHandler<Map<String, Object>, String> {

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

            String type = body.optString("type", null);
            if (type == null) {
                return errorResponse(400, "Missing type field").toString();
            }

            int rowsAffected = 0;

            if (type.equals("user_cert")) {
                // Delete a specific user_cert or all user_certs for this user
                if (body.has("user_cert_id")) {
                    long userCertId = body.getLong("user_cert_id");
                    String sql = "DELETE FROM user_cert WHERE user_id = ? AND user_cert_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setLong(1, userId);
                        stmt.setLong(2, userCertId);
                        rowsAffected = stmt.executeUpdate();
                    }
                } else {
                    // Delete all user_certs for this user
                    String sql = "DELETE FROM user_cert WHERE user_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setLong(1, userId);
                        rowsAffected = stmt.executeUpdate();
                    }
                }

            } else if (type.equals("certification")) {
                // Delete a certification (should be an admin-level action ONLY)
                if (!body.has("certification_id")) {
                    return errorResponse(400, "Missing certification_id").toString();
                }
                long certificationId = body.getLong("certification_id");
                String sql = "DELETE FROM certification WHERE certification_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, certificationId);
                    rowsAffected = stmt.executeUpdate();
                }
            } else {
                return errorResponse(400, "Invalid type. Must be 'user_cert' or 'certification'").toString();
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
            System.out.println("Error: Token expired");
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
