package com.example.searchrentals.scraper.infrastructure.jsoup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link NumericParser#parseGerman(String)}.
 *
 * <p>No Spring context required — plain JUnit 5.
 */
class NumericParserTest {

    private Locale originalLocale;

    @BeforeEach
    void captureLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    // --- acceptance-criterion cases ---

    @Test
    void germanPriceString_parsesCorrectly() {
        // "1.250,00 €" → 1250.00
        BigDecimal result = NumericParser.parseGerman("1.250,00 €");
        assertEquals(new BigDecimal("1250.00"), result);
    }

    @Test
    void fractionalRoomCount_parsesCorrectly() {
        // "2,5 Zi." → 2.5
        BigDecimal result = NumericParser.parseGerman("2,5 Zi.");
        assertEquals(new BigDecimal("2.5"), result);
    }

    @Test
    void integerWithThousandsDot_parsesCorrectly() {
        // "1.200" → 1200
        BigDecimal result = NumericParser.parseGerman("1.200");
        assertEquals(new BigDecimal("1200"), result);
    }

    @Test
    void blankInput_returnsNull() {
        assertNull(NumericParser.parseGerman("   "));
        assertNull(NumericParser.parseGerman(""));
    }

    @Test
    void nullInput_returnsNull() {
        assertNull(NumericParser.parseGerman(null));
    }

    // --- REQ-4: must not rely on default JVM locale ---

    @Test
    void withNonGermanDefaultLocale_stillParsesGermanFormat() {
        // Set a locale that uses different numeric conventions
        Locale.setDefault(Locale.US);

        // German price format must still parse correctly regardless of JVM locale
        assertEquals(new BigDecimal("1250.00"), NumericParser.parseGerman("1.250,00 €"));
        assertEquals(new BigDecimal("2.5"),     NumericParser.parseGerman("2,5 Zi."));
        assertEquals(new BigDecimal("1200"),    NumericParser.parseGerman("1.200"));
        assertNull(NumericParser.parseGerman(null));
    }

    // --- additional edge cases ---

    @Test
    void squareMetreString_parsesCorrectly() {
        // e.g. "75,50 m²" → 75.50
        assertEquals(new BigDecimal("75.50"), NumericParser.parseGerman("75,50 m²"));
    }

    @Test
    void pureInteger_parsesCorrectly() {
        assertEquals(new BigDecimal("3"), NumericParser.parseGerman("3"));
    }

    @Test
    void onlyNonNumericCharacters_returnsNull() {
        assertNull(NumericParser.parseGerman("€ m² Zi."));
    }
}
