import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RdsLambdaHandler implements RequestHandler<Object, String> {

    @Override
    public String handleRequest(Object input, Context context) {
        String vercelUrl = "https://study-buddy-two-gamma.vercel.app/";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(vercelUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return "Status: " + response.statusCode() + 
                   " | Body: " + response.body().substring(0, Math.min(200, response.body().length()));
        } catch (Exception e) {
            return "Error connecting to frontend: " + e.getMessage();
        }
    }
}
