package com.example.searchrentals.scraper.infrastructure.jsoup;

import java.math.BigDecimal;

/**
 * Package-private utility for parsing German-locale numeric strings.
 *
 * <p>German formatting uses dot ({@code .}) as the thousands separator and
 * comma ({@code ,}) as the decimal separator (e.g. {@code "1.250,00 €"}).
 * This class normalises such strings into a form that {@link BigDecimal} can
 * parse, without relying on the default JVM locale (REQ-4).
 */
final class NumericParser {

    private NumericParser() {
        // utility class — not instantiable
    }

    /**
     * Parses a German-locale numeric string to a {@link BigDecimal}.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Return {@code null} if {@code text} is {@code null} or blank.</li>
     *   <li>Strip every character that is not a digit, {@code .}, or {@code ,}.</li>
     *   <li>If the stripped string contains a comma, treat the comma as the decimal
     *       separator and all dots as thousands separators: remove dots, replace comma
     *       with {@code .}.</li>
     *   <li>If the stripped string contains only dots (no comma), apply a heuristic:
     *       if the string matches {@code \d{1,3}\.\d{3}} exactly (classic thousands
     *       format such as {@code "1.200"}), remove the dot (→ {@code "1200"});
     *       otherwise treat the last dot as the decimal separator.</li>
     *   <li>Parse the normalised string with {@code new BigDecimal(cleaned)}, returning
     *       {@code null} on {@link NumberFormatException}.</li>
     * </ol>
     *
     * @param text raw text from the page, e.g. {@code "1.250,00 €"} or {@code "2,5 Zi."}
     * @return parsed value, or {@code null} if the input is blank or cannot be parsed
     */
    static BigDecimal parseGerman(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        // Step 1: strip everything except digits, dot, and comma
        String stripped = text.replaceAll("[^0-9.,]", "");

        if (stripped.isEmpty()) {
            return null;
        }

        String normalised;

        if (stripped.contains(",")) {
            // Comma present → comma is decimal separator, dots are thousands separators
            normalised = stripped.replace(".", "").replace(",", ".");
        } else if (stripped.contains(".")) {
            // Only dots present — apply heuristic to distinguish thousands vs. decimal dot
            if (stripped.matches("\\d{1,3}\\.\\d{3}")) {
                // Matches pattern like "1.200" — dot is a thousands separator
                normalised = stripped.replace(".", "");
            } else {
                // e.g. "12.5" — treat last (and only) dot as decimal separator
                normalised = stripped;
            }
        } else {
            // Pure integer string
            normalised = stripped;
        }

        try {
            return new BigDecimal(normalised);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
