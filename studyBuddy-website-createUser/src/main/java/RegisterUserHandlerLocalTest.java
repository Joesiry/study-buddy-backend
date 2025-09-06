import com.amazonaws.services.lambda.runtime.Context;

import register.RegisterUserHandler;

import java.util.HashMap;
import java.util.Map;

public class RegisterUserHandlerLocalTest {
    public static void main(String[] args) {
    
        RegisterUserHandler handler = new RegisterUserHandler();

        // Use normal JSON (fields at the top level)
        Map<String, Object> event = new HashMap<>();
        event.put("first_name", "Jane");
        event.put("last_name", "Doe");
        event.put("username", "janedoe");
        event.put("password", "pass123");
        event.put("industry", "IT");
        event.put("user_role", "user");
        // event.put("bio", "Optional bio here"); // Uncomment if you want to test bio

        Context context = null; // You can mock this if needed

        Map<String, Object> response = handler.handleRequest(event, context);

        System.out.println(response);
    }
}


