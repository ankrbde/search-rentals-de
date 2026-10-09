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
import java.math.BigDecimal;
import java.util.ArrayList;
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

        List<Listing> results = new ArrayList<>();

        for (Element item : items) {
            // REQ-3: URL is mandatory — skip entries without a resolvable URL
            Element urlEl = item.selectFirst(ImmoScoutSelectors.URL);
            if (urlEl == null) {
                log.warn("Listing entry skipped: URL anchor element not found");
                continue;
            }
            String listingUrl = urlEl.absUrl("href");
            if (listingUrl == null || listingUrl.isBlank()) {
                log.warn("Listing entry skipped: URL anchor present but href resolves to blank");
                continue;
            }

            // Optional fields — null when absent or unparseable
            Element addrEl = item.selectFirst(ImmoScoutSelectors.ADDRESS);
            String address = addrEl != null ? addrEl.text() : null;

            Element priceEl = item.selectFirst(ImmoScoutSelectors.PRICE);
            BigDecimal priceEur = priceEl != null ? NumericParser.parseGerman(priceEl.text()) : null;

            Element sizeEl = item.selectFirst(ImmoScoutSelectors.SIZE);
            BigDecimal sizeSqm = sizeEl != null ? NumericParser.parseGerman(sizeEl.text()) : null;

            Element roomsEl = item.selectFirst(ImmoScoutSelectors.ROOMS);
            BigDecimal roomCount = roomsEl != null ? NumericParser.parseGerman(roomsEl.text()) : null;

            results.add(new Listing(address, priceEur, sizeSqm, roomCount, listingUrl));
        }

        return results;
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
