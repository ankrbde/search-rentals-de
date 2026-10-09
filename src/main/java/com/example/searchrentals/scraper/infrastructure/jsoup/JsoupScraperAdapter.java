package com.example.searchrentals.scraper.infrastructure.jsoup;

import com.example.searchrentals.scraper.domain.exception.ScraperException;
import com.example.searchrentals.scraper.domain.exception.ScraperParseException;
import com.example.searchrentals.scraper.domain.model.Listing;
import com.example.searchrentals.scraper.domain.port.ScraperPort;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Jsoup-backed implementation of {@link ScraperPort} for ImmoScout24 search results pages.
 *
 * <p>Fetches the page via HTTP, detects the results container, and selects listing
 * item elements. Field extraction is handled in a subsequent step.
 */
@Component
public class JsoupScraperAdapter implements ScraperPort {

    private static final Logger log = LoggerFactory.getLogger(JsoupScraperAdapter.class);

    private final JsoupScraperConfig config;

    public JsoupScraperAdapter(JsoupScraperConfig config) {
        this.config = config;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ScraperParseException if the results container element ({@code #resultListItems})
     *                               is absent from the parsed HTML document
     * @throws ScraperException      if an HTTP error or IO failure occurs while fetching the page
     */
    @Override
    public List<Listing> fetchListings(String searchUrl) {
        Document document = fetchDocument(searchUrl);

        // REQ-2: Detect results container; absence indicates a blocked/changed page
        Element container = document.selectFirst(ImmoScoutSelectors.CONTAINER);
        if (container == null) {
            throw new ScraperParseException(
                    "Results container not found — possible blocked response or DOM change. URL: " + searchUrl);
        }

        Elements items = container.select(ImmoScoutSelectors.ITEM);
        if (items.isEmpty()) {
            log.debug("Results container present but contains no listing items. URL: {}", searchUrl);
            return Collections.emptyList();
        }

        // Field extraction will be implemented in Task 10.
        // Placeholder: return empty list until extraction logic is added.
        return Collections.emptyList();
    }

    /**
     * Executes the HTTP request and returns the parsed {@link Document}.
     *
     * @param url the absolute URL to fetch
     * @return the parsed HTML document
     * @throws ScraperException if an IO error occurs or the server returns a non-200 status
     */
    private Document fetchDocument(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(config.getUserAgent())
                    .timeout(config.getTimeoutMs())
                    .execute();

            if (response.statusCode() != 200) {
                throw new ScraperException(
                        "HTTP " + response.statusCode() + " fetching URL: " + url);
            }

            return response.parse();
        } catch (IOException e) {
            throw new ScraperException("IO error fetching URL: " + url, e);
        }
    }
}
