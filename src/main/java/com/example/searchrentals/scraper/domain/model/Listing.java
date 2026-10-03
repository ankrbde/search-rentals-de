package com.example.searchrentals.scraper.domain.model;

import java.math.BigDecimal;

/**
 * Immutable domain record representing a single apartment listing scraped from
 * an ImmoScout24 search results page.
 *
 * <p>All fields except {@code listingUrl} are nullable: if a field cannot be
 * extracted or parsed from the HTML, it is set to {@code null} rather than
 * causing the entry to be discarded.
 *
 * <p>{@code listingUrl} is <strong>non-null</strong>: any listing entry for
 * which a URL cannot be extracted is excluded entirely before this record is
 * constructed (see REQ-3).
 *
 * @param address    street address or district name as shown on the results page, or {@code null} if absent
 * @param priceEur   monthly cold rent in EUR, or {@code null} if absent/unparseable
 * @param sizeSqm    living area in square metres, or {@code null} if absent/unparseable
 * @param roomCount  number of rooms (may be fractional, e.g. {@code 2.5}), or {@code null} if absent/unparseable
 * @param listingUrl absolute URL to the individual listing page — never {@code null}
 */
public record Listing(
        String address,
        BigDecimal priceEur,
        BigDecimal sizeSqm,
        BigDecimal roomCount,
        String listingUrl
) {}
