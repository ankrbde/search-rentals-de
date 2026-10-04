package com.example.searchrentals.scraper.domain.exception;

/**
 * Unchecked exception thrown when the scraper encounters an error during
 * an HTTP request or IO operation.
 */
public class ScraperException extends RuntimeException {

    public ScraperException(String message) {
        super(message);
    }

    public ScraperException(String message, Throwable cause) {
        super(message, cause);
    }
}
