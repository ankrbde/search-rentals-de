# Architecture

## Hexagonal layout

The domain layer knows nothing about Spring, Jsoup, or HTTP. It defines a
port (`ScraperPort`) that the infrastructure layer implements. This keeps
the scraping contract testable and swappable without touching business
logic.

```
┌──────────────────────────────────────────────────┐
│  Application                                      │
│                                                  │
│  ListingService (@Service)                       │
│  delegates to ScraperPort                        │
└──────────────────┬───────────────────────────────┘
                   │ depends on
                   ▼
┌──────────────────────────────────────────────────┐
│  Domain (zero framework imports)                  │
│                                                  │
│  Listing (record)        ScraperPort (interface)  │
│  ScraperException       ScraperParseException     │
└──────────────────┬───────────────────────────────┘
                   │ implemented by
                   ▼
┌──────────────────────────────────────────────────┐
│  Infrastructure                                  │
│                                                  │
│  JsoupScraperAdapter (@Component, implements     │
│  ScraperPort)                                    │
│  JsoupScraperConfig (@ConfigurationProperties)    │
│  ImmoScoutSelectors (constants)                  │
│  NumericParser (utility)                         │
└──────────────────────────────────────────────────┘
```

Dependency direction flows inward: infrastructure depends on domain, never
the other way around. `ListingService` sits at the application boundary and
only depends on the port interface, so it can be unit-tested with a mock.

## Packages

```
com.example.searchrentals.scraper
├── domain/
│   ├── model/            Listing.java
│   ├── port/             ScraperPort.java
│   └── exception/        ScraperException.java, ScraperParseException.java
├── application/          ListingService.java
└── infrastructure/
    └── jsoup/
        ├── JsoupScraperAdapter.java    ScraperPort implementation
        ├── JsoupScraperConfig.java     scraper.* config binding
        ├── ImmoScoutSelectors.java     CSS selector constants
        └── NumericParser.java          German-locale number parsing
```

## Request flow

```
Caller ──getListings(url)──▶ ListingService
                                │
                                ▼ fetchListings(url)
                          JsoupScraperAdapter
                                │
                                ▼ HTTP GET (Jsoup)
                         ImmoScout24 page
                                │
                                ▼
                          1. Find #resultListItems container
                             (missing → ScraperParseException)
                          2. Select article entries
                             (none → return empty list)
                          3. For each entry:
                             - extract URL → skip if blank
                             - extract address, price, size, rooms
                             - build Listing record
                          4. Return List<Listing>
```

## Domain model

`Listing` is a Java record with five fields. Only `listingUrl` is required;
the rest are nullable when the page doesn't expose them:

| Field        | Type         | Nullable | Notes                              |
|--------------|--------------|----------|------------------------------------|
| `address`    | `String`     | yes      | Street or district from the page   |
| `priceEur`   | `BigDecimal` | yes      | Monthly cold rent                  |
| `sizeSqm`    | `BigDecimal` | yes      | Living area                         |
| `roomCount`  | `BigDecimal` | yes      | May be fractional (e.g. 2.5)       |
| `listingUrl` | `String`     | no       | Entries without a URL are excluded |

## Exception hierarchy

```
RuntimeException
  └── ScraperException              HTTP errors, IO failures, timeouts
        └── ScraperParseException   Container element absent (blocked/changed)

```

Callers can catch `ScraperParseException` separately to distinguish "the site
structure changed or blocked us" from "the network request failed." Both are
unchecked, so they don't pollute the calling code's signature.

## Error handling

| Scenario                          | What happens                                    |
|-----------------------------------|-------------------------------------------------|
| HTTP status != 200                | `ScraperException` with status code and URL     |
| Network timeout or IO error      | `ScraperException` wrapping the cause           |
| Results container not in HTML     | `ScraperParseException` (blocked or DOM change) |
| Container present, zero entries  | Empty list (genuine zero-result search)         |
| Listing entry has no URL          | Entry skipped, WARN logged                       |
| Other field missing/unparseable   | Field set to null, listing still included       |

## CSS selectors

All selectors live in `ImmoScoutSelectors` as constants. If ImmoScout24
changes their DOM, this is the only file that needs updating.

| Constant   | Selector                              | Used for            |
|------------|---------------------------------------|---------------------|
| `CONTAINER` | `#resultListItems`                   | Results wrapper     |
| `ITEM`     | `article[data-is24-qa="resultlist-entry"]` | Listing entry  |
| `ADDRESS`  | `[data-is24-qa="resultlist-address"]`  | Street/district     |
| `PRICE`    | `[data-is24-qa="price"]`              | Monthly rent        |
| `SIZE`     | `[data-is24-qa="living-space"]`       | Area in m²          |
| `ROOMS`    | `[data-is24-qa="rooms"]`              | Room count          |
| `URL`      | `a[data-is24-qa="resultlist-entry-link"]` | Detail page link |

## German-locale number parsing

`NumericParser.parseGerman` handles ImmoScout24's German number format
without relying on the JVM's default locale:

1. Strip everything except digits, dots, and commas.
2. If a comma is present, it's the decimal separator; dots are thousands
   separators. Remove dots, replace comma with `.`.
3. If only dots are present, a `\d{1,3}\.\d{3}` heuristic decides whether the
   dot is a thousands separator (`"1.200"` → `1200`) or a decimal point.
4. Parse with `new BigDecimal(...)`. Return `null` on blank or unparseable.

## Configuration

```properties
scraper.user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...
scraper.timeout-ms=10000
```

Bound via `JsoupScraperConfig` with `@ConfigurationProperties(prefix = "scraper")`.
Defaults are baked into the class so it works even without the properties file.

## Testing strategy

| Test class                            | Scope        | Spring? | HTTP?    | Tests |
|---------------------------------------|-------------|---------|----------|-------|
| `NumericParserTest`                   | Unit         | No      | No       | 9     |
| `JsoupScraperAdapterTest`            | Unit         | No      | No       | 4     |
| `ListingServiceTest`                  | Unit         | No      | No       | 2     |
| `JsoupScraperAdapterIntegrationTest`  | Integration  | Yes     | WireMock | 2     |

Unit tests parse a local HTML fixture via `Jsoup.parse()` — no network calls,
deterministic results. The integration test starts the full Spring context and
uses WireMock to serve the fixture over HTTP, exercising the complete stack
from `ListingService` through `JsoupScraperAdapter` to the HTTP layer.
