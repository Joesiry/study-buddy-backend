package certification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.sql.*;
import java.util.Map;

/**
 * Certification creator handler. Creates certification and returns proper HTTP status code and response.
 */
public class CreateCertificationHandler implements RequestHandler<Map<String, Object>, String> {

	@Override
	public String handleRequest(Map<String, Object> event, Context context) {
		JSONObject response = new JSONObject();

		try (Connection conn = DriverManager.getConnection(
				System.getenv("DB_URL"),
				System.getenv("DB_USER"),
				System.getenv("DB_PASSWORD"))) {

			// Parse request body
			JSONObject body = new JSONObject((String) event.get("body"));

			String certName = body.getString("cert_name");
			String provider = body.optString("provider", null); // optional
			String description = body.optString("description", null); // optional
			int renewalPeriod = body.optInt("renewal_period", 0); // default 0 if not given

			// Insert into certification table
			String sql = "INSERT INTO certification (cert_name, provider, description, renewal_period) VALUES (?, ?, ?, ?) RETURNING certification_id";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, certName);
				stmt.setString(2, provider);
				stmt.setString(3, description);
				stmt.setInt(4, renewalPeriod);

				ResultSet rs = stmt.executeQuery();
				rs.next();
				long newId = rs.getLong("certification_id");

				response.put("statusCode", 200);
				response.put("body", new JSONObject()
						.put("message", "Certification created successfully")
						.put("certification_id", newId)
						.toString());

				// Log
				System.out.println("Created certification: " + certName + " (ID: " + newId + ")");
			}

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
}
