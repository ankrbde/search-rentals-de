package com.example.searchrentals.scraper.infrastructure.jsoup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Spring-bound configuration for the Jsoup HTTP adapter.
 *
 * <p>Properties are read from {@code application.properties} (or {@code application.yml})
 * under the {@code scraper} prefix:
 * <ul>
 *   <li>{@code scraper.user-agent} — the User-Agent header sent with each HTTP request</li>
 *   <li>{@code scraper.timeout-ms} — connection/read timeout in milliseconds</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "scraper")
public class JsoupScraperConfig {

    /**
     * HTTP User-Agent header value.
     * Defaults to a common desktop Chrome UA to reduce likelihood of bot-blocking.
     */
    private String userAgent =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    /**
     * HTTP connection/read timeout in milliseconds. Defaults to 10 seconds.
     */
    private int timeoutMs = 10_000;

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
