package com.example.searchrentals.scraper.infrastructure.jsoup;

import com.example.searchrentals.scraper.domain.exception.ScraperParseException;
import com.example.searchrentals.scraper.domain.model.Listing;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the HTML-parsing logic in {@link JsoupScraperAdapter}.
 *
 * <p>Tests call the package-private {@code parseDocument(Document, String)} method
 * directly so that no live HTTP requests are made. The base URI
 * {@code https://www.immobilienscout24.de} is supplied to {@link Jsoup#parse} so
 * that relative {@code href} values are resolved to absolute URLs, matching the
 * behaviour of the production code path.
 *
 * <p>No Spring context is started.
 */
class JsoupScraperAdapterTest {

    private static final String BASE_URI = "https://www.immobilienscout24.de";
    private static final String FAKE_URL  = BASE_URI + "/Suche/test";

    private JsoupScraperAdapter adapter;
    private String fixtureHtml;

    @BeforeEach
    void setUp() throws Exception {
        // Construct adapter without a real config — config is only used in fetchDocument,
        // which is not exercised by these tests.
        JsoupScraperConfig config = new JsoupScraperConfig();
        adapter = new JsoupScraperAdapter(config);

        // Load the fixture from the classpath (src/test/resources/immoscout-fixture.html)
        URI resourceUri = getClass().getClassLoader().getResource("immoscout-fixture.html").toURI();
        fixtureHtml = Files.readString(Path.of(resourceUri));
    }

    // -----------------------------------------------------------------------
    // 1. Full fixture — 4 entries; entry 3 excluded (no URL); 3 listings expected
    // -----------------------------------------------------------------------

    @Test
    void fullFixture_returnsThreeListings() {
        Document doc = Jsoup.parse(fixtureHtml, BASE_URI);

        List<Listing> listings = adapter.parseDocument(doc, FAKE_URL);

        assertEquals(3, listings.size(), "Entry 3 (no URL anchor) must be excluded");

        // Entry 1 — full data
        Listing first = listings.get(0);
        assertEquals("Musterstraße 1, 10115 Berlin", first.address());
        assertEquals(new BigDecimal("1250.00"), first.priceEur());
        assertEquals(new BigDecimal("75.50"),   first.sizeSqm());
        assertEquals(new BigDecimal("3"),        first.roomCount());
        assertEquals(BASE_URI + "/expose/123456789", first.listingUrl());

        // Entry 2 — full data
        Listing second = listings.get(1);
        assertEquals("Beispielweg 42, 20095 Hamburg", second.address());
        assertEquals(new BigDecimal("980.00"), second.priceEur());
        assertEquals(new BigDecimal("60.00"),  second.sizeSqm());
        assertEquals(new BigDecimal("2"),       second.roomCount());
        assertEquals(BASE_URI + "/expose/987654321", second.listingUrl());

        // Entry 4 — no address element; address must be null
        Listing fourth = listings.get(2);
        assertNull(fourth.address(), "Listing from entry 4 must have null address");
        assertEquals(BASE_URI + "/expose/111222333", fourth.listingUrl());
    }

    // -----------------------------------------------------------------------
    // 2. No container element → ScraperParseException
    // -----------------------------------------------------------------------

    @Test
    void noContainer_throwsScraperParseException() {
        // Strip the #resultListItems wrapper but keep surrounding HTML structure
        String htmlWithoutContainer = "<html><body><p>No results here.</p></body></html>";
        Document doc = Jsoup.parse(htmlWithoutContainer, BASE_URI);

        assertThrows(ScraperParseException.class,
                () -> adapter.parseDocument(doc, FAKE_URL),
                "Missing container must throw ScraperParseException");
    }

    // -----------------------------------------------------------------------
    // 3. Container present but no item elements → empty list
    // -----------------------------------------------------------------------

    @Test
    void emptyContainer_returnsEmptyList() {
        String htmlEmptyContainer =
                "<html><body><div id=\"resultListItems\"></div></body></html>";
        Document doc = Jsoup.parse(htmlEmptyContainer, BASE_URI);

        List<Listing> listings = adapter.parseDocument(doc, FAKE_URL);

        assertNotNull(listings);
        assertTrue(listings.isEmpty(), "Empty container must yield an empty list");
    }

    // -----------------------------------------------------------------------
    // 4. Entry with URL but no address element → included with address == null
    // -----------------------------------------------------------------------

    @Test
    void missingAddressEntry_includedWithNullAddress() {
        String htmlWithMissingAddress =
                "<html><body>" +
                "<div id=\"resultListItems\">" +
                "  <article data-is24-qa=\"resultlist-entry\">" +
                "    <dd data-is24-qa=\"price\">800,00 €</dd>" +
                "    <dd data-is24-qa=\"living-space\">45,00 m²</dd>" +
                "    <dd data-is24-qa=\"rooms\">1 Zi.</dd>" +
                "    <a data-is24-qa=\"resultlist-entry-link\" href=\"/expose/111222333\">Listing 4</a>" +
                "  </article>" +
                "</div>" +
                "</body></html>";
        Document doc = Jsoup.parse(htmlWithMissingAddress, BASE_URI);

        List<Listing> listings = adapter.parseDocument(doc, FAKE_URL);

        assertEquals(1, listings.size());
        assertNull(listings.get(0).address(), "Listing without address element must have null address");
        assertEquals(BASE_URI + "/expose/111222333", listings.get(0).listingUrl());
    }
}
