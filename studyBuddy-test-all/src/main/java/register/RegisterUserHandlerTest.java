package register;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * Unit test ensuring that RegisterUserHandler works as intended.
 */
public class RegisterUserHandlerTest {

	private static Connection connection;

	@BeforeAll
	static void setupDatabase() throws Exception {
		// Use in-memory H2 database for testing
		connection = DriverManager.getConnection("jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");

		// Create schema
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("CREATE TABLE app_user (" +
					"user_id SERIAL PRIMARY KEY, " +
					"first_name VARCHAR(50), " +
					"last_name VARCHAR(50), " +
					"username VARCHAR(50) UNIQUE NOT NULL, " +
					"hashed_password TEXT NOT NULL, " +
					"industry VARCHAR(50), " +
					"user_role VARCHAR(50), " +
					"bio TEXT)");
		}

		// Override env vars for handler
		System.setProperty("DB_URL", "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		System.setProperty("DB_USER", "");
		System.setProperty("DB_PASSWORD", "");
		System.setProperty("JWT_KEY", "12345678901234567890123456789012"); // 32-byte secret
	}

	@Test
	void testUserRegistrationSuccess() {
		RegisterUserHandler handler = new RegisterUserHandler();

		// Build request body
		Map<String, Object> body = new HashMap<>();
		body.put("first_name", "Alice");
		body.put("last_name", "Smith");
		body.put("username", "alice123");
		body.put("password", "password123");
		body.put("industry", "IT");
		body.put("user_role", "student");
		body.put("bio", "Test bio");

		Map<String, Object> event = new HashMap<>();
		event.put("body", body);

		// Fake context
		Context context = new Context() {
			public String getAwsRequestId() { return "test-request"; }
			public String getLogGroupName() { return "test-log-group"; }
			public String getLogStreamName() { return "test-log-stream"; }
			public String getFunctionName() { return "test-function"; }
			public String getFunctionVersion() { return "1"; }
			public String getInvokedFunctionArn() { return "arn:aws:lambda:test"; }
			public CognitoIdentity getIdentity() { return null; }
			public ClientContext getClientContext() { return null; }
			public int getRemainingTimeInMillis() { return 3000; }
			public int getMemoryLimitInMB() { return 512; }
			public LambdaLogger getLogger() {
				return new LambdaLogger() {
					@Override
					public void log(String message) {
						System.out.println(message);
					}

					@Override
					public void log(byte[] message) {
						System.out.println(new String(message));
					}
				};
			}
		};

		// Call handler
		Map<String, Object> response = handler.handleRequest(event, context);

		// Assert status code
		assertEquals(201, response.get("statusCode"));

		String bodyString = (String) response.get("body");
		assertTrue(bodyString.contains("\"username\":\"alice123\""));
		assertTrue(bodyString.contains("\"token\"")); // JWT should be returned
	}
}