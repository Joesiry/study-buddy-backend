package example;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class BackAndForthHandler implements RequestHandler<Object, String> {
	
	private static final String DB_URL = "jdbc:postgresql://database-1.c8tga68k2zvm.us-east-1.rds.amazonaws.com:5432/postgres";
	private static final String DB_USER = "postgres";
	private static final String DB_PASSWORD = "DATA4y0u!";

    @Override
    public String handleRequest(Object input, Context context) {
        JSONObject response = new JSONObject();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT NOW() AS current_time")) {

            if (rs.next()) {
                String dbTime = rs.getString("current_time");
                response.put("message", "Connected successfully!");
                response.put("db_time", dbTime);
            }

        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return response.toString();
    }
}
