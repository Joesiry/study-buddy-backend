import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class RdsLambdaHandler implements RequestHandler<Object, String> {

	private static final String DB_URL = "jdbc:postgresql://database-1.c8tga68k2zvm.us-east-1.rds.amazonaws.com:5432/postgres";
	private static final String DB_USER = "postgres";
	private static final String DB_PASSWORD = "DATA4y0u!";

	@Override
	public String handleRequest(Object input, Context context) {
		context.getLogger().log("Connecting to PostgreSQL RDS...\n");

		try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
				Statement stmt = conn.createStatement()) {

			ResultSet rs = stmt.executeQuery("SELECT NOW();");
			if (rs.next()) {
				String currentTime = rs.getString(1);
				context.getLogger().log("Connected! Current time from DB: " + currentTime + "\n");
				return "Success: Connected to PostgreSQL RDS at " + currentTime;
			}

		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage() + "\n");
			return "Failed: " + e.getMessage();
		}

		return "Done.";
	}
}
