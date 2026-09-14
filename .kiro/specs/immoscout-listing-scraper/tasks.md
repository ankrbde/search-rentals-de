# Tasks: ImmoScout24 Listing Scraper

## Implementation Tasks

- [-] 1. Initialise Maven project structure
  - Create a Maven `pom.xml` with Spring Boot parent, `spring-boot-starter`, `spring-boot-starter-test`, and `jsoup` dependencies.
  - Create the base package `com.example.searchrentals.scraper` and the sub-packages: `domain/model`, `domain/port`, `domain/exception`, `application`, `infrastructure/jsoup`.
  - **Acceptance:** `mvn compile` succeeds with no errors.

- [ ] 2. Define the `Listing` domain record
  - Create `Listing.java` as a pure Java `record` with fields: `String address`, `BigDecimal priceEur`, `BigDecimal sizeSqm`, `BigDecimal roomCount`, `String listingUrl`.
  - Add Javadoc noting `listingUrl` is non-null (entries without a URL are excluded before this record is created).
  - Zero framework or library imports.
  - **Acceptance:** Class compiles; no Spring/Jsoup imports present.

- [ ] 3. Define domain exceptions
  - Create `ScraperException extends RuntimeException` with `(String message)` and `(String message, Throwable cause)` constructors.
  - Create `ScraperParseException extends ScraperException` with the same two constructors — used exclusively when the results-container element is absent (REQ-2).
  - Zero framework imports in either class.
  - **Acceptance:** Both classes compile; `ScraperParseException` is a subtype of `ScraperException`.

- [ ] 4. Define the `ScraperPort` interface
  - Create `ScraperPort.java` in `domain/port` with a single method: `List<Listing> fetchListings(String searchUrl)`.
  - Add Javadoc declaring: returns empty list when container present but no items; throws `ScraperParseException` when container absent; throws `ScraperException` on HTTP/IO failures.
  - Zero framework imports.
  - **Acceptance:** Interface compiles; Javadoc is present and accurate.

- [ ] 5. Implement `ListingService`
  - Create `ListingService.java` annotated `@Service` with a `ScraperPort` constructor argument.
  - Implement `public List<Listing> getListings(String searchUrl)` delegating to `scraperPort.fetchListings(searchUrl)`.
  - No Jsoup or HTTP logic; only `@Service` and constructor injection from Spring.
  - **Acceptance:** Service compiles; only Spring stereotype annotation present as framework dependency.

- [ ] 6. Implement `JsoupScraperConfig`
  - Create `JsoupScraperConfig.java` annotated `@Component` and `@ConfigurationProperties(prefix = "scraper")`.
  - Fields: `String userAgent` (default: desktop Chrome UA string), `int timeoutMs` (default: `10_000`).
  - Add `application.properties` (or `application.yml`) with `scraper.user-agent` and `scraper.timeout-ms` keys.
  - **Acceptance:** Spring context loads; config values are injectable.

- [ ] 7. Define CSS selector constants
  - Create a package-private constants class (or interface) `ImmoScoutSelectors` in `infrastructure/jsoup`.
  - Define string constants for: `CONTAINER` (`#resultListItems`), `ITEM` (`article[data-is24-qa="resultlist-entry"]`), `ADDRESS`, `PRICE`, `SIZE`, `ROOMS`, `URL`.
  - **Acceptance:** All selectors referenced in `JsoupScraperAdapter` via these constants — no inline selector strings.

- [ ] 8. Implement German-locale numeric parser utility
  - Create a package-private `NumericParser` utility class in `infrastructure/jsoup`.
  - Implement `static BigDecimal parseGerman(String text)` that: strips non-numeric characters except `.` and `,`; removes dot thousands separator; replaces comma decimal separator with `.`; returns `new BigDecimal(cleaned)`, or `null` on blank/unparseable input.
  - **Acceptance:** Unit tests pass for inputs `"1.250,00 €"` → `1250.00`, `"2,5 Zi."` → `2.5`, `"1.200"` → `1200`, blank/null → `null`.

- [ ] 9. Implement `JsoupScraperAdapter` — container detection and item selection
  - Create `JsoupScraperAdapter.java` annotated `@Component`, implementing `ScraperPort`.
  - Inject `JsoupScraperConfig`; build `Jsoup.connect(url).userAgent(...).timeout(...).get()`.
  - Wrap `IOException` and non-200 HTTP status in `ScraperException`.
  - **Container detection (REQ-2):** call `document.selectFirst(CONTAINER)`. If `null`, throw `ScraperParseException("Results container not found — possible blocked response or DOM change. URL: " + searchUrl)`.
  - Select listing items with `container.select(ITEM)`. If the resulting `Elements` is empty, return `Collections.emptyList()`.
  - **Acceptance:** When given a fixture HTML with no container selector, `ScraperParseException` is thrown. When container is present but empty, empty list is returned.

- [ ] 10. Implement `JsoupScraperAdapter` — field extraction and URL-exclusion
  - For each item `Element` from Task 9, extract fields using the selector constants and `NumericParser`.
  - **URL-exclusion logic (REQ-3):** resolve URL via `element.selectFirst(URL).absUrl("href")`. If the result is `null` or blank, log a `WARN` and `continue` — do not add a `Listing` for this entry.
  - For all other fields (`address`, `priceEur`, `sizeSqm`, `roomCount`), set `null` if the element is absent or unparseable.
  - Construct `new Listing(address, priceEur, sizeSqm, roomCount, listingUrl)` and add to result list.
  - Return the collected list.
  - **Acceptance:** Listings with a valid URL are included; entries missing a URL are excluded; entries with other missing fields are included with `null` values.

- [ ] 11. Unit tests — `NumericParser`
  - Write `NumericParserTest` covering: standard German price string, fractional room count, integer-only value with thousands dot, blank input, null input.
  - Add one test case that sets a non-German default JVM locale before calling the parser, to explicitly verify REQ-4's "must not rely on default locale" requirement.
  - **Acceptance:** All cases pass; no Spring context required.

- [ ] 12. Unit tests — `JsoupScraperAdapter` HTML parsing
  - Create an HTML fixture file in `src/test/resources` containing a realistic but minimal ImmoScout24-style search results page with: a results container, two complete listing entries, one listing entry with no URL anchor, and a fourth listing entry with a missing address element (URL present) so the "address == null" assertion has something to test against.
  - Write `JsoupScraperAdapterTest` using `Jsoup.parse(fixtureHtml, "https://www.immobilienscout24.de")` to instantiate the adapter's parsing logic:
    - Full fixture → assert 3 listings returned (third entry excluded due to missing URL).
    - Fixture with container element removed → assert `ScraperParseException` is thrown.
    - Fixture with container present but no item elements → assert empty list returned.
    - Fixture with one entry missing address element → assert listing included with `address == null`.
  - **Acceptance:** All cases pass; no live HTTP calls made.

- [ ] 13. Unit tests — `ListingService`
  - Write `ListingServiceTest` using a Mockito mock of `ScraperPort`.
  - Test: mock returns a list of two `Listing` objects → `getListings()` returns the same list unchanged.
  - Test: mock throws `ScraperParseException` → exception propagates unchanged.
  - **Acceptance:** Tests pass without starting a Spring context.

- [ ] 14. Integration smoke test
  - Write `JsoupScraperAdapterIntegrationTest` annotated `@SpringBootTest` using WireMock (or a local `MockWebServer`) to serve a fixture HTML response.
  - Assert that `ListingService.getListings(stubUrl)` returns the expected list when the stub returns a valid page, and throws `ScraperParseException` when the stub returns an unrecognized HTML page.
  - **Acceptance:** Tests pass; no real network calls to ImmoScout24.