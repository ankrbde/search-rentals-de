package com.example.searchrentals.scraper.application;

import com.example.searchrentals.scraper.domain.model.Listing;
import com.example.searchrentals.scraper.domain.port.ScraperPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service that orchestrates retrieval of rental listings.
 *
 * <p>This service is a thin delegation layer: all HTTP and parsing logic
 * lives exclusively in the {@link ScraperPort} implementation. Future
 * cross-cutting concerns (deduplication, filtering, sorting) belong here.
 */
@Service
public class ListingService {

    private final ScraperPort scraperPort;

    public ListingService(ScraperPort scraperPort) {
        this.scraperPort = scraperPort;
    }

    /**
     * Fetches all rental listings from the given search results URL.
     *
     * @param searchUrl the absolute URL of the search results page to scrape
     * @return ordered list of listings found on the page
     */
    public List<Listing> getListings(String searchUrl) {
        return scraperPort.fetchListings(searchUrl);
    }
}
