package com.example.searchrentals.scraper.domain.port;

import com.example.searchrentals.scraper.domain.exception.ScraperException;
import com.example.searchrentals.scraper.domain.exception.ScraperParseException;
import com.example.searchrentals.scraper.domain.model.Listing;

import java.util.List;

/**
 * Port defining the contract for fetching rental listings from a search results page.
 */
public interface ScraperPort {

    /**
     * Fetches all rental listings from the given search results URL.
     *
     * @param searchUrl the absolute URL of the search results page to scrape
     * @return a list of {@link Listing} records extracted from the page; returns an empty list
     *         when the results container element is present but contains no listing entries
     * @throws ScraperParseException if the results container element is absent from the
     *                               parsed HTML document, indicating a blocked response or
     *                               an unexpected DOM structure change
     * @throws ScraperException      if an HTTP error or IO failure occurs while fetching
     *                               the page
     */
    List<Listing> fetchListings(String searchUrl);
}
