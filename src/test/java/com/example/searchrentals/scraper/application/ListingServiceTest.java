package com.example.searchrentals.scraper.application;

import com.example.searchrentals.scraper.domain.exception.ScraperParseException;
import com.example.searchrentals.scraper.domain.model.Listing;
import com.example.searchrentals.scraper.domain.port.ScraperPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListingService}.
 *
 * <p>Uses a Mockito mock of {@link ScraperPort} so the service's thin
 * delegation behaviour can be verified in isolation — no Spring context is
 * started and no real scraping or HTTP is performed.
 */
@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    private static final String SEARCH_URL = "https://www.immobilienscout24.de/Suche/de/berlin/wohnung-mieten";

    @Mock
    private ScraperPort scraperPort;

    @InjectMocks
    private ListingService listingService;

    @Test
    void getListings_delegatesToScraperPort() {
        // Arrange: mock returns a list of two Listing objects
        List<Listing> expectedListings = List.of(
                new Listing("Berlin Mitte", new BigDecimal("1250.00"),
                        new BigDecimal("75.50"), new BigDecimal("2.5"),
                        "https://www.immobilienscout24.de/expose/1"),
                new Listing("Berlin Kreuzberg", new BigDecimal("980.00"),
                        new BigDecimal("60.00"), new BigDecimal("2"),
                        "https://www.immobilienscout24.de/expose/2")
        );
        when(scraperPort.fetchListings(anyString())).thenReturn(expectedListings);

        // Act
        List<Listing> result = listingService.getListings(SEARCH_URL);

        // Assert: same list returned unchanged (thin delegation)
        assertSame(expectedListings, result);
    }

    @Test
    void getListings_propagatesScraperParseException() {
        // Arrange: mock throws ScraperParseException when the container is absent
        ScraperParseException toThrow =
                new ScraperParseException("Results container not found — possible blocked response or DOM change. URL: " + SEARCH_URL);
        when(scraperPort.fetchListings(anyString())).thenThrow(toThrow);

        // Act + Assert: exception propagates unchanged to the caller
        ScraperParseException thrown = assertThrows(
                ScraperParseException.class,
                () -> listingService.getListings(SEARCH_URL)
        );

        // The exact same exception instance must propagate (no wrapping)
        assertSame(toThrow, thrown);
    }
}
