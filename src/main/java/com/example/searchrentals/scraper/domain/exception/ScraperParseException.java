package com.example.searchrentals.scraper.domain.exception;

/**
 * Thrown exclusively when the results-container element is absent from the
 * scraped HTML document (REQ-2), indicating either a blocked response or an
 * unexpected DOM structure change.
 *
 * <p>This is a subtype of {@link ScraperException} and may be caught
 * separately when callers need to distinguish parse failures from general
 * HTTP/IO failures.
 */
public class ScraperParseException extends ScraperException {

    public ScraperParseException(String message) {
        super(message);
    }

    public ScraperParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
