package com.example.aibuild.exception;

/**
 * Thrown when a build plan fails validation (bounds, foundation, floating blocks, etc.)
 */
public class BuildValidationException extends Exception {
    public BuildValidationException(String message) {
        super(message);
    }
}
