package com.example.searchrentals.scraper.infrastructure.jsoup;

import com.example.searchrentals.scraper.application.ListingService;
import com.example.searchrentals.scraper.domain.exception.ScraperParseException;
import com.example.searchrentals.scraper.domain.model.Listing;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration smoke test for the ImmoScout24 scraper wired through the full
 * Spring context.
 *
 * <p>Uses WireMock (standalone) to serve a fixture HTML page over real HTTP so
 * the complete path — {@link ListingService} delegating to the Jsoup
 * {@code ScraperPort} adapter — can be exercised end-to-end without making any
 * real network calls to ImmoScout24 (NFR-4).
 *
 * <p>WireMock serves two scenarios:
 * <ul>
 *   <li>a valid search results page (the {@code immoscout-fixture.html} fixture)
 *       on {@code /Suche/test} — asserts the expected list of listings is returned;</li>
 *   <li>an unrecognised HTML page (no {@code #resultListItems} container) on
 *       {@code /blocked} — asserts {@link ScraperParseException} is thrown (REQ-2).</li>
 * </ul>
 *
 * <p>Because the adapter resolves listing URLs against the fetched document's
 * base URI, the relative {@code href="/expose/..."} values in the fixture resolve
 * to the WireMock server's base URL (e.g. {@code http://localhost:PORT/expose/...}).
 */
@SpringBootTest
class JsoupScraperAdapterIntegrationTest {

    /**
     * Standalone WireMock server on a dynamically allocated port.
     *
     * <p>Marked {@code static} so the server is started once before any test runs
     * and shared across both test methods. Each test registers its own stub on a
     * distinct path so there is no cross-test interference.
     */
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private ListingService listingService;

    // ------------------------------------------------------------------
    // Valid page → expected list of listings
    // ------------------------------------------------------------------

    /**
     * Stubs the search URL to return the ImmoScout24 fixture HTML and asserts
     * that {@link ListingService#getListings(String)} returns the expected
     * listings through the full Spring-wired stack.
     *
     * <p>Validates: Requirements REQ-1, REQ-2, REQ-3, REQ-4, REQ-5
     */
    @Test
    void getListings_validStubbedPage_returnsExpectedListings() throws Exception {
        // Load the fixture HTML from the classpath (src/test/resources/immoscout-fixture.html)
        String fixtureHtml = new String(
                getClass().getClassLoader().getResourceAsStream("immoscout-fixture.html").readAllBytes(),
                StandardCharsets.UTF_8);

        String baseUrl = wireMock.getRuntimeInfo().getHttpBaseUrl();
        String searchPath = "/Suche/test";

        // Stub the search URL to return the fixture HTML with HTTP 200
        wireMock.stubFor(get(urlPathEqualTo(searchPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody(fixtureHtml)));

        // Act — exercise the full Spring-wired stack (ListingService → ScraperPort → Jsoup)
        List<Listing> listings = listingService.getListings(baseUrl + searchPath);

        // Assert: 3 listings (entry 3 excluded due to missing URL anchor — REQ-3)
        assertEquals(3, listings.size(), "Entry 3 (no URL anchor) must be excluded");

        // First listing has the expected address, price, size, rooms and URL
        Listing first = listings.get(0);
        assertEquals("Musterstraße 1, 10115 Berlin", first.address());
        assertEquals(new BigDecimal("1250.00"), first.priceEur());
        assertEquals(new BigDecimal("75.50"), first.sizeSqm());
        assertEquals(new BigDecimal("3"), first.roomCount());
        // Relative href resolved against the WireMock base URL
        assertEquals(baseUrl + "/expose/123456789", first.listingUrl());

        // Second listing — full data
        Listing second = listings.get(1);
        assertEquals("Beispielweg 42, 20095 Hamburg", second.address());
        assertEquals(new BigDecimal("980.00"), second.priceEur());
        assertEquals(new BigDecimal("60.00"), second.sizeSqm());
        assertEquals(new BigDecimal("2"), second.roomCount());
        assertEquals(baseUrl + "/expose/987654321", second.listingUrl());

        // Fourth entry — no address element; included with address == null (REQ-3)
        Listing fourth = listings.get(2);
        assertEquals(baseUrl + "/expose/111222333", fourth.listingUrl());
        assertEquals(null, fourth.address(), "Listing from entry 4 must have null address");
    }

    // ------------------------------------------------------------------
    // Unrecognised HTML (no results container) → ScraperParseException
    // ------------------------------------------------------------------

    /**
     * Stubs a URL returning HTML that lacks the {@code #resultListItems}
     * container and asserts {@link ScraperParseException} is thrown.
     *
     * <p>Validates: Requirements REQ-2
     */
    @Test
    void getListings_unrecognisedHtml_throwsScraperParseException() {
        String baseUrl = wireMock.getRuntimeInfo().getHttpBaseUrl();
        String blockedPath = "/blocked";

        // Stub a page that lacks the #resultListItems container — e.g. a bot-block / 404 page
        wireMock.stubFor(get(urlPathEqualTo(blockedPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody("<html><body><h1>404 Not Found</h1></body></html>")));

        // Act + Assert — missing container must surface as ScraperParseException (REQ-2)
        assertThrows(ScraperParseException.class,
                () -> listingService.getListings(baseUrl + blockedPath),
                "Unrecognised HTML (no results container) must throw ScraperParseException");
    }
}
