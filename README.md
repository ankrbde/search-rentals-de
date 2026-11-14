# search-rentals-de

[![CI](https://github.com/ankrbde/search-rentals-de/actions/workflows/ci.yml/badge.svg)](https://github.com/ankrbde/search-rentals-de/actions/workflows/ci.yml)

A personal, non-commercial project that scrapes apartment and house rental
listings from ImmoScout24's public search results. It's built to be
respectful — rate-limited, single-page, no crawling — and is the first site
in what's planned to become a multi-source rental search tool.

## What it does

Given a search URL, the scraper fetches that one page, finds all listing
entries, and returns them as a plain in-memory list. Each listing carries
its address, monthly cold rent, living area, room count, and a link to the
full listing page. That's it — no database, no notifications, no pagination.

## Project structure

The code follows a hexagonal layout to keep the domain logic free of any
framework or scraping dependencies:

```
com.example.searchrentals.scraper
├── domain/
│   ├── model/          Listing record (pure Java, no imports beyond BigDecimal)
│   ├── port/           ScraperPort interface (the contract)
│   └── exception/      ScraperException, ScraperParseException
├── application/        ListingService — thin Spring @Service that delegates
└── infrastructure/
    └── jsoup/          JsoupScraperAdapter, JsoupScraperConfig,
                        ImmoScoutSelectors, NumericParser
```

The domain layer (`Listing`, `ScraperPort`, exceptions) has zero Spring or
Jsoup imports. All HTTP and HTML parsing lives behind the `ScraperPort`
interface in the infrastructure layer. See [ARCHITECTURE.md](ARCHITECTURE.md)
for the full design.

## Key behaviours

- **Container detection:** If the results container element is missing
  entirely, the scraper throws `ScraperParseException` — that usually means
  a blocked response or a site redesign, not an empty search. An empty but
  present container returns an empty list.

- **URL-required filtering:** Listings without a resolvable URL are skipped
  rather than returned with a null link. All other fields can be null if they
  can't be parsed from the page.

- **German number parsing:** Prices, sizes, and room counts are parsed
  using German locale rules (dot = thousands, comma = decimal), independent
  of the JVM's default locale. `"1.250,00 €"` becomes `1250.00`; `"2,5 Zi."`
  becomes `2.5`.

## Running the tests

```bash
./mvnw test
```

All 17 tests pass, none of them hit ImmoScout24:
- **NumericParserTest** (9) — German-locale parsing edge cases
- **JsoupScraperAdapterTest** (4) — HTML parsing against a local fixture
- **ListingServiceTest** (2) — service delegation via Mockito
- **JsoupScraperAdapterIntegrationTest** (2) — full Spring context with
  WireMock serving fixture HTML

## Configuration

Scraper settings live in `application.properties`:

```properties
scraper.user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...
scraper.timeout-ms=10000
```

## Tech stack

- Java 21
- Spring Boot 3.3.4
- Jsoup 1.18.1 (HTML parsing and HTTP)
- WireMock 3.9.1 (test-scoped HTTP stubbing)
- JUnit 5, Mockito

## Known limitations

- Only the first page of results is scraped per call — no pagination.
- Sponsored/promoted listings that also appear organically are not
  deduplicated. That's expected to be handled downstream.

## License

[MIT](LICENSE)
