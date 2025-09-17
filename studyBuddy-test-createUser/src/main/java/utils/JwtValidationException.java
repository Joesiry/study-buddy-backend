package utils;

/**
 * Exception indicating an expired JWT token.
 */
public class JwtValidationException extends Exception {
	private static final long serialVersionUID = 1L;
	private final int statusCode;

    public JwtValidationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
