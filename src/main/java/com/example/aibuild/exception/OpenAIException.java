package com.example.aibuild.exception;

import java.io.IOException;

/**
 * Thrown when OpenAI API calls fail
 */
public class OpenAIException extends IOException {
    private final int httpCode;
    
    public OpenAIException(String message) {
        super(message);
        this.httpCode = -1;
    }
    
    public OpenAIException(String message, int httpCode) {
        super(message);
        this.httpCode = httpCode;
    }
    
    public OpenAIException(String message, Throwable cause) {
        super(message, cause);
        this.httpCode = -1;
    }
    
    public int getHttpCode() {
        return httpCode;
    }
    
    public boolean isTimeout() {
        return getMessage() != null && getMessage().contains("timeout");
    }
    
    public boolean isAuthError() {
        return httpCode == 401 || httpCode == 403;
    }
    
    public boolean isRateLimited() {
        return httpCode == 429;
    }
}
