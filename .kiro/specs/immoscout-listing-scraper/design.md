# Design: ImmoScout24 Listing Scraper

## Architecture Overview

The module follows **Hexagonal Architecture** (Ports & Adapters). The domain core is pure Java with no framework or library dependencies. Spring Boot and Jsoup live exclusively in the infrastructure layer and are wired together via dependency injection at the application boundary.

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                     │
│                                                         │
│   ListingService                                        │
│   (Spring @Service, depends on ScraperPort)             │
└───────────────────────┬─────────────────────────────────┘
                        │ uses
                        ▼
┌─────────────────────────────────────────────────────────┐
│                     Domain Layer                         │
│                                                         │
│   Listing (record)          ScraperPort (interface)     │
│   ScraperException          ScraperParseException       │
└───────────────────────┬─────────────────────────────────┘
                        │ implemented by
                        ▼
┌─────────────────────────────────────────────────────────┐
│                  Infrastructure Layer                    │
│                                                         │
│   JsoupScraperAdapter (@Component)                      │
│   (depends on Jsoup, Spring config)                     │
└─────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.example.searchrentals
└── scraper
    ├── domain
    │   ├── model
    │   │   └── Listing.java                # Domain value object (record)
    │   ├── port
    │   │   └── ScraperPort.java            # Outbound port interface
    │   └── exception
    │       ├── ScraperException.java       # Base domain exception (I/O, HTTP errors)
    │       └── ScraperParseException.java  # Parse-failure domain exception (REQ-2)
    ├── application
    │   └── ListingService.java             # Application service
    └── infrastructure
        └── jsoup
            ├── JsoupScraperAdapter.java    # ScraperPort implementation
            └── JsoupScraperConfig.java     # Spring @ConfigurationProperties
```

---

## Domain Model

### `Listing` (record)

Pure Java record — no annotations, no framework imports.

```java
package com.example.searchrentals.scraper.domain.model;

import java.math.BigDecimal;

public record Listing(
    String address,
    BigDecimal priceEur,
    BigDecimal sizeSqm,
    BigDecimal roomCount,
    String listingUrl          // non-null: entries with no URL are excluded (REQ-3)
) {}
```

`listingUrl` is the only required field. A listing entry with no extractable URL is **excluded** from the result list entirely (REQ-3). All other fields may be `null` if unparseable.

---

### `ScraperException` (base domain exception)

Wraps I/O and HTTP-level errors so nothing Jsoup-specific leaks across the port boundary (NFR-3).

```java
package com.example.searchrentals.scraper.domain.exception;

public class ScraperException extends RuntimeException {
    public ScraperException(String message) { super(message); }
    public ScraperException(String message, Throwable cause) { super(message, cause); }
}
```

### `ScraperParseException` (parse-failure domain exception)

A distinct subtype of `ScraperException` thrown specifically when the expected results-container element is absent from the fetched HTML — indicating a blocked/anti-bot response or an unrecognized page structure change, not a genuine zero-result search (REQ-2).

```java
package com.example.searchrentals.scraper.domain.exception;

public class ScraperParseException extends ScraperException {
    public ScraperParseException(String message) { super(message); }
    public ScraperParseException(String message, Throwable cause) { super(message, cause); }
}
```

Callers can catch `ScraperParseException` separately from `ScraperException` to distinguish structural failures from network/HTTP failures.

---

## Port Definition

### `ScraperPort`

Outbound port. The domain defines it; the infrastructure fulfils it.

```java
package com.example.searchrentals.scraper.domain.port;

import com.example.searchrentals.scraper.domain.model.Listing;
import java.util.List;

public interface ScraperPort {
    /**
     * Fetches the given search results URL and returns all parsed listings
     * found on that single page.
     *
     * @param searchUrl absolute URL of an ImmoScout24 search results page
     * @return ordered list of listings (empty if container present but no entries found)
     * @throws ScraperParseException if the results container element is absent
     *         (blocked response or unrecognized page structure — REQ-2)
     * @throws ScraperException on HTTP errors or network/timeout failures
     */
    List<Listing> fetchListings(String searchUrl);
}
```

---

## Application Service

### `ListingService`

Thin orchestration layer. Delegates all I/O to `ScraperPort`. Annotated with `@Service` but contains zero Jsoup or HTTP logic.

```java
@Service
public class ListingService {
    private final ScraperPort scraperPort;

    public ListingService(ScraperPort scraperPort) {
        this.scraperPort = scraperPort;
    }

    public List<Listing> getListings(String searchUrl) {
        return scraperPort.fetchListings(searchUrl);
    }
}
```

Future cross-cutting concerns (deduplication, filtering, sorting) belong here, not in the adapter.

---

## Infrastructure Adapter

### `JsoupScraperAdapter`

Implements `ScraperPort` using Jsoup. Responsible for:

1. Building the HTTP request with a browser `User-Agent` and timeout.
2. Fetching and parsing the HTML document.
3. **Locating the results container element** — throwing `ScraperParseException` if absent (REQ-2).
4. Selecting listing item elements from within the container.
5. Extracting and cleaning each field, excluding entries with no URL (REQ-3).
6. Wrapping all Jsoup/IO exceptions in `ScraperException`.

#### CSS Selector Strategy

Two selector tiers are used — the container selector first, then item selectors within it:

| Purpose               | Selector (indicative)                                        | Notes                                                       |
|-----------------------|--------------------------------------------------------------|-------------------------------------------------------------|
| **Results container** | `#resultListItems`                                           | Absence → throw `ScraperParseException` (REQ-2)             |
| Listing item          | `article[data-is24-qa="resultlist-entry"]`                   | Selected within container; empty → return `[]`              |
| Address               | `[data-qa="result-list-entry-address"]`                      | `.text()`                                                   |
| Price                 | `.result-list-entry__criteria span:contains(€)`             | text → German-locale numeric parse                          |
| Size (sqm)            | `.result-list-entry__criteria span:contains(m²)`            | text → German-locale numeric parse                          |
| Rooms                 | `.result-list-entry__criteria span:contains(Zi.)`           | text → German-locale numeric parse                          |
| Listing URL           | `a.result-list-entry__brand-title-container[href]`          | `absUrl("href")` — absent/blank → **exclude entry** (REQ-3) |

> **Note:** ImmoScout24 may change its DOM structure. All selectors are defined as constants in one file so updates require a single-file change.

#### Container-Detection Logic (REQ-2)

```
document = Jsoup.connect(url).userAgent(...).timeout(...).get()
container = document.selectFirst(CONTAINER_SELECTOR)
if container == null:
    throw new ScraperParseException(
        "Results container not found — possible blocked response or DOM change. URL: " + url
    )
items = container.select(ITEM_SELECTOR)
if items.isEmpty():
    return Collections.emptyList()    // valid zero-result search
```

#### URL-Exclusion Logic (REQ-3)

```
for each item in items:
    url = item.selectFirst(URL_SELECTOR)?.absUrl("href")
    if url == null or url.isBlank():
        log.warn("Skipping listing entry — no URL extractable")
        continue                      // exclude entry entirely
    listings.add(new Listing(address, price, size, rooms, url))
```

#### German-Locale Numeric Parsing (REQ-4)

Numeric fields (price, size, rooms) use a shared utility method:

```
input:  "1.250,00 €"
step 1: strip non-numeric characters except ".", "," → "1.250,00"
step 2: remove thousands separator (dot) → "1250,00"
step 3: replace decimal separator (comma) with "." → "1250.00"
step 4: new BigDecimal("1250.00")

input:  "2,5 Zi."  → "2,5" → "2.5" → BigDecimal("2.5")
input:  "1.200"    → "1200" (no decimal comma present)
```

Returns `null` if the element is absent or the cleaned text cannot be converted to `BigDecimal`.

### `JsoupScraperConfig`

Bound to `application.properties` / `application.yml`:

```yaml
scraper:
  user-agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
  timeout-ms: 10000
```

```java
@ConfigurationProperties(prefix = "scraper")
@Component
public class JsoupScraperConfig {
    private String userAgent;
    private int timeoutMs = 10_000;
    // getters / setters
}
```

---

## Sequence Diagram

```
Caller           ListingService        ScraperPort       JsoupScraperAdapter      ImmoScout24
  │                    │                    │                     │                    │
  │  getListings(url)  │                    │                     │                    │
  │──────────────────▶│                    │                     │                    │
  │                    │ fetchListings(url) │                     │                    │
  │                    │────────────────────────────────────────▶│                    │
  │                    │                    │                     │  GET url           │
  │                    │                    │                     │───────────────────▶│
  │                    │                    │                     │  200 HTML          │
  │                    │                    │                     │◀───────────────────│
  │                    │                    │          locate container                │
  │                    │                    │          (absent → ScraperParseException)│
  │                    │                    │          select items in container       │
  │                    │                    │          (empty → return [])             │
  │                    │                    │          for each item: extract fields   │
  │                    │                    │          (no URL → skip entry)           │
  │                    │◀────────────────────────────────────────│                    │
  │                    │   List<Listing>    │                     │                    │
  │◀──────────────────│                    │                     │                    │
```

---

## Error Handling

| Scenario                                          | Behaviour                                                                                  |
|---------------------------------------------------|--------------------------------------------------------------------------------------------|
| HTTP status != 200                                | `ScraperException("HTTP error: <status> for URL: <url>")`                                  |
| Network timeout / IOException                     | `ScraperException("Failed to fetch: <url>", cause)`                                        |
| **Results container element absent** (REQ-2)      | `ScraperParseException("Results container not found …")` — signals bot-block or DOM change |
| Container present, zero listing items (REQ-2)     | Return empty `List<Listing>` — genuine zero-result search                                  |
| `listingUrl` missing or blank on an entry (REQ-3) | Entry **excluded** from result list; warning logged                                        |
| Other field missing or unparseable (REQ-3)        | Field set to `null`; listing still included                                                |

---

## Spring Boot Wiring

`JsoupScraperAdapter` is annotated `@Component` and injected into `ListingService` via constructor injection. No XML, no manual bean definitions required.

---

## Testability

- `ListingService` can be unit-tested by passing a `ScraperPort` lambda or Mockito mock — no Spring context needed.
- `JsoupScraperAdapter` can be unit-tested by loading a local HTML fixture with `Jsoup.parse(html, baseUri)` — no real HTTP calls needed.
  - Fixture: full search-results page HTML with known listing count → assert correct `List<Listing>` size and field values.
  - Fixture: HTML with container absent → assert `ScraperParseException` is thrown.
  - Fixture: HTML with container present but no item elements → assert empty list returned.
  - Fixture: HTML with one listing entry missing the URL anchor → assert that entry is excluded.
- Integration tests can use `@SpringBootTest` with WireMock or a local stub server.