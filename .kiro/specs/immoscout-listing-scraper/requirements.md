# Requirements: ImmoScout24 Listing Scraper

## Overview

A Java/Spring Boot module that fetches apartment listings from ImmoScout24's public search results page, parses each listing into a structured domain object, and returns an in-memory list from a service method. No persistence, no notifications, no framework dependencies in the domain layer.

---

## Functional Requirements

### REQ-1: Fetch Search Results Page

WHEN the listing service is called with a search URL,
THE SYSTEM SHALL fetch the HTML content of the ImmoScout24 search results page at that URL.

WHEN the HTTP response status is not 200 OK,
THE SYSTEM SHALL throw a domain exception indicating the fetch failed, including the status code.

WHEN the HTTP request times out or a network error occurs,
THE SYSTEM SHALL throw a domain exception wrapping the underlying cause.

---

### REQ-2: Parse Listing Items from Search Results

WHEN the search results page HTML is successfully retrieved,
THE SYSTEM SHALL identify all individual listing entries present on the page.

WHEN the page HTML does not contain a recognizable search-results container element at all,
THE SYSTEM SHALL treat this as a parse failure and throw a domain exception (e.g. ScraperParseException) — this signals a blocked/anti-bot response or a structural site change, not a genuine zero-result search.

WHEN a valid search-results container element is present but contains zero listing entries,
THE SYSTEM SHALL return an empty list.

---

### REQ-3: Extract Listing Fields

WHEN a listing entry is parsed,
THE SYSTEM SHALL extract the following fields:

| Field        | Type            | Description                                   |
|--------------|-----------------|-----------------------------------------------|
| `address`    | `String`        | Street address or district name as shown      |
| `priceEur`   | `BigDecimal`    | Monthly cold rent in EUR                      |
| `sizeSqm`    | `BigDecimal`    | Living area in square metres                  |
| `roomCount`  | `BigDecimal`    | Number of rooms (may be fractional, e.g. 2.5) |
| `listingUrl` | `String`        | Absolute URL to the individual listing page   |

WHEN `listingUrl` cannot be extracted from a listing entry (missing element or unparseable value),
THE SYSTEM SHALL exclude that listing entry entirely from the result list — `listingUrl` is a required field.

WHEN the extracted listing URL is relative (not absolute),
THE SYSTEM SHALL resolve it to an absolute URL using ImmoScout24's base domain (https://www.immobilienscout24.de) before setting it on the Listing object.

WHEN any field other than `listingUrl` cannot be parsed from the HTML (missing element or unparseable text),
THE SYSTEM SHALL set that field to `null` on the resulting `Listing` object rather than discarding the listing entirely.

---

### REQ-4: German-Locale Numeric Parsing

WHEN parsing `priceEur`, `sizeSqm`, or `roomCount` from page text,
THE SYSTEM SHALL apply German-locale number formatting rules: dot (`.`) as thousands separator and comma (`,`) as decimal separator.

WHEN a number string such as `"1.200"` is encountered,
THE SYSTEM SHALL parse it as `1200`, not `1.2`.

WHEN a number string such as `"2,5"` is encountered,
THE SYSTEM SHALL parse it as `2.5`.

THE SYSTEM SHALL NOT rely on default JVM locale or US-locale number parsing for these fields.

---

### REQ-5: Return In-Memory List

WHEN all listing entries on the page have been processed,
THE SYSTEM SHALL return a `List<Listing>` containing one `Listing` object per parsed entry, in the order they appear on the page.

---

### REQ-6: Support Configurable Search URL

WHEN the service method is invoked,
THE SYSTEM SHALL accept the target search URL as a parameter, allowing callers to specify city, filters, and pagination without hardcoding.

---

### REQ-7: Single-Page Scope

WHEN the service method is invoked,
THE SYSTEM SHALL scrape only the single page identified by the provided URL and SHALL NOT automatically follow pagination links.

---

## Non-Functional Requirements

### NFR-1: Hexagonal Architecture

THE SYSTEM SHALL define scraping behaviour behind a `ScraperPort` interface in the domain layer.
THE SYSTEM SHALL ensure the domain model (`Listing`) and application service (`ListingService`) have zero dependencies on Spring, Jsoup, or any other framework or library.
THE SYSTEM SHALL implement `ScraperPort` in an infrastructure adapter (`JsoupScraperAdapter`) that may depend on Jsoup.

### NFR-2: HTTP Politeness

THE SYSTEM SHALL send a `User-Agent` header that mimics a common desktop browser to reduce the likelihood of being blocked.
THE SYSTEM SHALL support a configurable request timeout (default: 10 seconds).

### NFR-3: Resilience

THE SYSTEM SHALL not propagate raw Jsoup or I/O exceptions out of the `ScraperPort` contract; all exceptions crossing the port boundary SHALL be wrapped in domain-defined exception types.

### NFR-4: Testability

THE SYSTEM SHALL be structured such that the application service and domain logic can be unit-tested by providing a mock or stub `ScraperPort` implementation without starting a Spring context.

THE SYSTEM SHALL include unit tests for `JsoupScraperAdapter`'s HTML parsing logic that load a saved local HTML fixture file rather than making live network calls, ensuring tests are deterministic and parser regressions are distinguishable from actual site-structure changes.

---

## Out of Scope

- Pagination / multi-page crawling
- Persistence (database, file system)
- Notifications (email, webhook, etc.)
- Authentication or login flows
- Proxy rotation or CAPTCHA handling
- Scheduling / cron triggers

---

## Known Limitations

- **Missed listings between polls:** Because only a single page is scraped per invocation, any new listings that appear beyond page one between two consecutive polls will not be captured. This is an inherent constraint of single-page scope (see REQ-7).
- **Sponsored/promoted listing duplicates:** ImmoScout24 surfaces promoted listings at the top of search results in addition to their organic position. These duplicates are not detected or deduplicated at this stage; deduplication by listing URL is expected to be handled by a downstream consumer.
