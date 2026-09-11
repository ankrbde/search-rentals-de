# Requirements: ImmoScout24 Listing Scraper

## Overview

A Java/Spring Boot module that scrapes new apartment listings from ImmoScout24's public search results page and returns them as an in-memory list of `Listing` domain objects. No persistence or notification concerns are in scope for this spec.

---

## Functional Requirements

### REQ-1: Scrape Search Results Page

WHEN the scraper service is invoked with a search URL,  
THE SYSTEM SHALL fetch the HTML content of the ImmoScout24 public search results page at that URL.

**Acceptance Criteria:**
- The system accepts a full ImmoScout24 search URL (e.g. `https://www.immoscout24.de/Wohnung-mieten/...`) as input.
- The system performs an HTTP GET request to that URL using Jsoup.
- If the HTTP response status is not 2xx, the system throws a `ScraperException` with the status code.
- The system sets a realistic User-Agent header on every request to avoid bot-detection blocks.

---

### REQ-2: Parse Listings from HTML

WHEN the HTML of a search results page has been fetched,  
THE SYSTEM SHALL parse each apartment listing entry from the page into a `Listing` domain object.

**Acceptance Criteria:**
- Each parsed `Listing` contains: address, price (EUR), size in sqm, room count, and listing URL.
- Fields that cannot be parsed from the HTML are represented as `null` (not blank string, not zero).
- Numeric fields (price, size, room count) are parsed from the raw text by stripping non-numeric characters before conversion.
- The listing URL is an absolute URL (prefixed with `https://www.immoscout24.de` if relative).
- Listings where both price and size are `null` are silently skipped.

---

### REQ-3: Return All Listings as In-Memory List

WHEN parsing is complete,  
THE SYSTEM SHALL return all parsed `Listing` objects as a `List<Listing>` to the caller.

**Acceptance Criteria:**
- The return type is `java.util.List<Listing>`.
- The list preserves the order in which listings appear on the page.
- An empty list is returned (not null, not an exception) when the page contains zero parseable listings.

---

### REQ-4: Support Pagination

WHEN the search results span multiple pages,  
THE SYSTEM SHALL scrape all pages and return a combined list of listings.

**Acceptance Criteria:**
- The system detects a "next page" link in the search results HTML.
- The system follows the next-page link and repeats the fetch-and-parse cycle.
- A configurable `maxPages` parameter caps the number of pages scraped (default: 5).
- If `maxPages` is reached before the last page, the system stops and returns results collected so far.

---

### REQ-5: Domain Isolation

THE SYSTEM SHALL ensure the `Listing` domain class and the `ScraperPort` interface have zero dependencies on any framework (Spring, Jsoup, etc.).

**Acceptance Criteria:**
- `Listing` is a plain Java record or POJO in a `domain` package.
- `ScraperPort` is a plain Java interface in the `domain` package.
- Neither `Listing` nor `ScraperPort` import classes from `org.springframework.*`, `org.jsoup.*`, or any other third-party library.

---

### REQ-6: Configuration via Application Properties

WHEN the application starts,  
THE SYSTEM SHALL read scraper configuration from Spring application properties.

**Acceptance Criteria:**
- The following properties are supported:  
  - `immoscout.scraper.base-url` — base URL for search (required)  
  - `immoscout.scraper.max-pages` — maximum pages to scrape (default: 5)  
  - `immoscout.scraper.request-delay-ms` — delay between requests in milliseconds (default: 1000)
- Missing required properties cause a `BeanCreationException` on startup with a descriptive message.

---

## Non-Functional Requirements

- **NFR-1 Politeness:** The scraper introduces a configurable delay between consecutive HTTP requests (default 1000 ms) to avoid overloading the target server.
- **NFR-2 Resilience:** A single unparseable listing must not abort the entire scrape; the error is logged and the listing is skipped.
- **NFR-3 Testability:** The Jsoup HTTP call is hidden behind `ScraperPort` so the service can be unit-tested with a mock or stub port.
- **NFR-4 No persistence:** This module must not depend on any database, cache, or messaging infrastructure.

---

## Out of Scope

- Persisting listings to a database
- Sending notifications (email, push, etc.)
- Authenticated / login-required pages
- Image or document downloading
- Price history or change detection
