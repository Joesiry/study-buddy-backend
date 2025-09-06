package certification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.Map;

/**
 * Certification table retriever handler. Returns all certification data and proper HTTP status code.
 */
public class GetCertificationHandler implements RequestHandler<Map<String, Object>, String> {

	@Override
	public String handleRequest(Map<String, Object> event, Context context) {
		JSONObject response = new JSONObject();

		try (Connection conn = DriverManager.getConnection(
				System.getenv("DB_URL"),
				System.getenv("DB_USER"),
				System.getenv("DB_PASSWORD"))) {

			String sql = "SELECT certification_id, cert_name, provider, description, renewal_period FROM certification";
			JSONArray certifications = new JSONArray();

			try (PreparedStatement stmt = conn.prepareStatement(sql);
					ResultSet rs = stmt.executeQuery()) {

				while (rs.next()) {
					JSONObject cert = new JSONObject();
					cert.put("certification_id", rs.getLong("certification_id"));
					cert.put("cert_name", rs.getString("cert_name"));
					cert.put("provider", rs.getString("provider"));
					cert.put("description", rs.getString("description"));
					cert.put("renewal_period", rs.getInt("renewal_period"));
					certifications.put(cert);
				}
			}

			response.put("statusCode", 200);
			response.put("body", new JSONObject()
					.put("certifications", certifications)
					.toString());

			// Log
			System.out.println("Retrieved " + certifications.length() + " certifications.");

		} catch (Exception e) {
			response.put("statusCode", 500);
			response.put("body", new JSONObject()
					.put("error", "Internal server error")
					.put("details", e.getMessage())
					.toString());

			// Log
			System.err.println("Error in GetCertificationHandler: " + e.getMessage());
			e.printStackTrace();
		}

		return response.toString();
	}
}
