package com.example.aibuild.exception;

/**
 * Thrown when parsing a build plan from JSON fails
 */
public class PlanParseException extends Exception {
    private final String jsonSnippet;
    
    public PlanParseException(String message, String jsonSnippet) {
        super(message);
        this.jsonSnippet = jsonSnippet;
    }
    
    public PlanParseException(String message, Throwable cause, String jsonSnippet) {
        super(message, cause);
        this.jsonSnippet = jsonSnippet;
    }
    
    public String getJsonSnippet() {
        return jsonSnippet;
    }
}
