package com.example.searchrentals.scraper.infrastructure.jsoup;

/**
 * CSS selector constants for parsing ImmoScout24 search results pages.
 *
 * <p>All selectors are centralised here so that DOM-structure changes require
 * a single-file update rather than a scattered search through the adapter code.
 */
final class ImmoScoutSelectors {

    private ImmoScoutSelectors() {
        // utility class — not instantiable
    }

    /** Root container that wraps all search result entries. */
    static final String CONTAINER = "#resultListItems";

    /** Each individual listing entry element within the container. */
    static final String ITEM = "article[data-is24-qa=\"resultlist-entry\"]";

    /** Address text element within a listing entry. */
    static final String ADDRESS = "[data-is24-qa=\"resultlist-address\"]";

    /** Price element within a listing entry. */
    static final String PRICE = "[data-is24-qa=\"price\"]";

    /** Living space (size in m²) element within a listing entry. */
    static final String SIZE = "[data-is24-qa=\"living-space\"]";

    /** Room count element within a listing entry. */
    static final String ROOMS = "[data-is24-qa=\"rooms\"]";

    /** Anchor element carrying the listing's detail-page URL. */
    static final String URL = "a[data-is24-qa=\"resultlist-entry-link\"]";
}
