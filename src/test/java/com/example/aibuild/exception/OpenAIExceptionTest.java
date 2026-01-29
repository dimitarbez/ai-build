package com.example.aibuild.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenAIException
 */
class OpenAIExceptionTest {

    @Test
    void testBasicConstructor() {
        OpenAIException ex = new OpenAIException("Test error");
        assertEquals("Test error", ex.getMessage());
        assertEquals(-1, ex.getHttpCode());
        assertFalse(ex.isTimeout());
        assertFalse(ex.isAuthError());
        assertFalse(ex.isRateLimited());
    }

    @Test
    void testConstructorWithHttpCode() {
        OpenAIException ex = new OpenAIException("Test error", 500);
        assertEquals("Test error", ex.getMessage());
        assertEquals(500, ex.getHttpCode());
    }

    @Test
    void testConstructorWithCause() {
        IOException cause = new IOException("Network error");
        OpenAIException ex = new OpenAIException("Wrapper error", cause);
        assertEquals("Wrapper error", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertEquals(-1, ex.getHttpCode());
    }

    @Test
    void testIsTimeout() {
        OpenAIException ex1 = new OpenAIException("Request timeout occurred");
        assertTrue(ex1.isTimeout());

        OpenAIException ex2 = new OpenAIException("Connection timed out");
        assertTrue(ex2.isTimeout());

        OpenAIException ex3 = new OpenAIException("Other error");
        assertFalse(ex3.isTimeout());
    }

    @Test
    void testIsAuthError() {
        OpenAIException ex401 = new OpenAIException("Unauthorized", 401);
        assertTrue(ex401.isAuthError());

        OpenAIException ex403 = new OpenAIException("Forbidden", 403);
        assertTrue(ex403.isAuthError());

        OpenAIException ex500 = new OpenAIException("Server error", 500);
        assertFalse(ex500.isAuthError());
    }

    @Test
    void testIsRateLimited() {
        OpenAIException ex429 = new OpenAIException("Too many requests", 429);
        assertTrue(ex429.isRateLimited());

        OpenAIException ex200 = new OpenAIException("Success", 200);
        assertFalse(ex200.isRateLimited());
    }

    @Test
    void testExtendsIOException() {
        OpenAIException ex = new OpenAIException("Test");
        assertTrue(ex instanceof IOException);
    }
}
