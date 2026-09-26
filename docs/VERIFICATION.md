# Verification

Local verification on 26 September 2026:

- Spring Boot backend compilation and executable JAR packaging.
- 19 Java tests: photo parsing/normalization, session/CSRF API flow, access isolation, deduplication, budgets/categories, rate limiting, expiry, cancellation and failure cleanup.
- 5 frontend unit tests: requests, errors, outbound-link safety and sharing behavior.
- Vue TypeScript check and Vite production bundle.
- Frontend formatting check, shell script syntax and Docker Compose configuration validation.
- In-app browser: landing page, demo collection, upload of the generated editorial test image, consent, anonymous submission, three-result rendering, reload persistence.
- Responsive DOM checks at 360, 390, 768 and 1440 px: three result cards present and no horizontal document overflow.

The Playwright regression suite and CI workflow are supplied for repeatable browser testing. Manual browser checks use the local demo backend. At initial verification, live provider credentials were absent, so those checks do not establish live product coverage or personal-fit accuracy. Docker builds and PostgreSQL operation have not been executed in this session.

## Backend code conventions and language refactor

Follow-up verification on 26 September 2026:

- `mvn -f backend/pom.xml -Djava.awt.headless=true verify`: 25 Java tests passed; executable JAR packaged. This includes API English-message contracts, recommendation presentation codes, provider-response validation, credential redaction and existing lifecycle/security tests.
- `npm --prefix frontend test`: 8 tests passed, including Turkish UI rendering of English API messages and stable recommendation codes.
- `npm --prefix frontend run build`: TypeScript and production bundle passed.
- Maven Spotless and frontend Prettier checks passed. Java formatting is now part of Maven validation; frontend formatting is included in `scripts/check.sh`.
- Shell syntax and `git diff --check` passed.

This refactor was verified with automated tests and builds. The browser checks above refer to the initial implementation and were not repeated for this refactor. Live providers and container execution remain unverified.

## Tavily search migration

Follow-up verification on 26 September 2026:

- `mvn -f backend/pom.xml -Djava.awt.headless=true verify`: 38 tests passed, Spotless passed, executable JAR rebuilt.
- Search tests cover Tavily authentication and request parameters, merchant verification, duplicate and unsafe URLs, malformed responses, empty results, provider authorization/rate/credit-limit failures, and no search calls in demo mode.
- Spring integration tests now explicitly select demo mode so local live settings cannot cause provider calls during tests.
- A live basic Tavily request with product terms and the configured merchant filter returned HTTP 200, twelve results from Atasun Optik, and reported one credit used. Credentials were read locally and not logged.
- Credentials were moved out of application source into ignored `.env`; local mode is live. The source and packaged configuration contain no inline API credentials.

The search smoke test validates Tavily access and URL discovery only. A full live photo analysis and merchant-page verification flow was not run for this migration. Frontend code was unchanged.

## Gemini photo analysis migration

Follow-up verification on 26 September 2026:

- Clean Maven verification and executable JAR packaging passed: 42 Java tests, no failures or errors.
- A local mock Gemini API verifies API-key authentication, the selected Flash-Lite endpoint, inline JPEG input, structured JSON schema, response parsing and token metrics.
- Configuration tests verify live Gemini construction, missing-key rejection and demo mode without provider credentials.
- Frontend unit tests (8), Prettier, TypeScript and production build passed.
- Consent text, privacy links, environment examples and Docker Compose now use Gemini.

No live Gemini call was made because `GEMINI_API_KEY` is not configured locally. Add it to ignored `.env` and restart with `scripts/dev.sh` to use live analysis.

## Turkish merchant search diagnosis

Follow-up verification on 26 September 2026:

- Compared four basic Tavily queries with the configured three merchant domains and `country=turkey`: `sunglasses Round`, `yuvarlak güneş gözlüğü`, `optical eyeglass frames Round`, and `yuvarlak optik gözlük çerçevesi`. Each returned HTTP 200, 12 results, and one credit used (four credits total).
- All returned URLs were from Atasun; most were category pages. The optical queries also returned sunglasses pages. These tests establish search access, not adequate optical-product coverage.
- `answer` was null as expected with `include_answer=false`; search hits are in `results`.
- The returned Atasun RB3447 product page was accessible and supplied Product JSON-LD, but used `model: Yuvarlak` for shape and `name: RB 3447 001 50*21*145` for the product code. The parser previously rejected this metadata. It now falls back to the structured name when the model field has no code, still requiring a matching page heading and the requested category. Shape recognition also reads the merchant model field.
- Added a regression fixture matching those relevant fields, with wrong-category and conflicting-model rejection checks. Maven verification and JAR packaging passed: 43 tests, no failures or errors.

The English search query remains unchanged: the Turkish comparison did not improve product-page discovery in this sample. No full photo-to-recommendation request was run, and budgets or merchant coverage can still produce no matches. Restart the IDE backend to load the parser fix.

## Product verification interruption handling

Follow-up verification on 26 September 2026:

- Replayed the twelve merchant URLs from the supplied Tavily response using the application's Java `SafePageFetcher` and parser, without another Tavily or Gemini request. The batch completed in approximately 1.76 seconds, no tasks were cancelled, and no products were verified. These URLs are category, brand, guide or home pages. The reported live interruption was not reproduced.
- Removed the catch-all that converted page-fetch failures and interruptions into empty product results. Failed/incomplete verification with no products now reports `SEARCH_UNAVAILABLE`; completed products survive other page failures/timeouts. Successfully inspected non-product pages can still yield a legitimate empty result.
- Executor cleanup requests cancellation without waiting indefinitely in `ExecutorService.close()`. Caller cancellation preserves the interrupt flag. The fetcher checks cancellation before network work and between processing stages.
- Logs distinguish Tavily hit counts, allowed URLs, verified/rejected pages, failures, deadline cancellations and calling-job cancellation, without logging keys, photos, response bodies or exception messages.
- Maven verification and executable JAR packaging passed: 49 tests, no failures or errors. New tests cover an unresponsive fetch alongside a successful product, all-timeout/all-failure behavior, parent cancellation, pre-interrupted fetches, and category-page rejection.

The 16-second verification budget and 60-second job deadline remain in place. This change improves cancellation and failure handling; it does not turn category pages or search snippets into verified products or add category-page crawling. Restart the IDE backend to load it.

## Live rectangular-sunglasses discovery fix

Follow-up verification on 26 September 2026:

- Started the Spring application on a random localhost port and invoked only `WebProductSearch.search` with fixed SUNGLASSES/RECTANGULAR preferences. The actual Tavily query is `sunglasses rectangular`; Gemini was not called.
- Before the fix: Tavily returned 12 allowed URLs; verification returned no products (7 rejected pages, 5 failed pages, no deadline cancellations), causing `SEARCH_UNAVAILABLE`.
- `verifyPages` now processes completions as they arrive and prioritizes discovered product details. Candidate links come from structured ItemList/Product markup or observed Atasun `a.p-name` cards. Navigation links, pagination, foreign domains, unsafe URLs, duplicate fragments and a second level of crawling are excluded. The batch is bounded to 12 search pages plus 24 detail pages, four concurrent requests and the existing shared 16-second deadline.
- After the fix, the same live query returned 12 search hits; 24 detail URLs were discovered, 19 products were verified, 12 pages were rejected and 5 failed. No timeout or skipped work occurred. Verification took approximately 2.9 seconds. Examples included RB2140, RB4187, RB4340 and RB3539. These are product pages/variants, not 19 guaranteed distinct models or a guarantee of current stock.
- Two live Tavily requests were made for the before/after checks, each using basic search. No full photo-analysis request was made.
- Added an explicit opt-in `TavilySearchLiveTest` for this exact flow. Normal Maven verification passed 54 automated tests and skipped this one live test; formatting and executable JAR packaging passed. Regression tests cover listing-to-detail verification, merchant/HTTPS restrictions, URL deduplication, one-hop and request-count limits, and preserved cancellation/deadline behavior.

Restart the IDE backend to load the updated classes. The live test can be rerun using the command in README; it uses one Tavily search credit per run.


## Direct product links and AI model candidates

Follow-up verification on 26 September 2026:

- Product verification now requires a single Product record with URL evidence matching the fetched page and any canonical link, an exact model-code match in the heading, and the requested category. CollectionPage/ItemList and multi-product pages are excluded. Known tracking parameters are ignored without dropping query-based product identity. Colour codes such as C002/C014 are no longer mistaken for model codes; vendor prefixes such as 0M018 normalize to M018.
- Gemini's existing image-analysis call now includes preferences and requests up to three brand/model candidates. Ray-Ban, Osse, Inesta and Mustang seed the Turkish-market brands. Candidate validation, normalization, deduplication, query construction and verified-match ranking have regression coverage. The real SDK against a local mock Gemini API verifies the new structured response and request fields. No live Gemini/photo request was made for this change.
- The Spring application started on a random localhost port for two live Tavily-only tests. `sunglasses rectangular` verified 18 product pages (24 discovered, 13 rejected, 5 failed); the RB2140/RB2132/RB4340 query verified 24 product pages (24 discovered, 10 rejected, 2 failed), including exact matches for those models. Neither verification timed out or skipped pending work. Counts include variants and do not guarantee current stock. Two basic searches were used.
- Product-card store links and sharing now target the same direct product URL as the primary action.
- Frontend tests passed (8 tests), TypeScript/production build passed, and Prettier checks passed.
- Final Maven verification and executable JAR packaging passed: 59 automated tests passed, with the two live tests skipped in the normal suite (both passed in the explicit live run). Spotless and whitespace checks passed. Restart the backend from the IDE to load these changes.

## Product images

Follow-up verification on 26 September 2026:

- Added `imageUrl` from the verified Product JSON-LD record through product ranking to recommendation responses. Handles URL strings, arrays and ImageObject metadata, resolves relative URLs against the fetched page, and permits HTTPS merchant images or Atasun's observed CDN. Missing/invalid images do not discard otherwise valid products. The displayed image belongs to the primary offer's variant.
- Product cards render the photo with contained sizing, lazy loading and no referrer; failed or absent images retain the existing placeholder. Production Nginx CSP includes supported merchant image origins and `stn-atasun.mncdn.com`.
- Inspected a real Atasun RB2140 page's image metadata and received HTTP 200/image/png from the CDN. A Playwright test with mocked recommendation responses loaded that real CDN image in installed Chrome, verified nonzero natural width, tested broken/missing image fallbacks, and passed desktop/mobile overflow checks. Screenshots were inspected. No Tavily or Gemini calls were used.
- Maven verification/JAR packaging passed: 60 automated tests, two opt-in search tests skipped. Eight frontend unit tests and the TypeScript/production build passed. The browser test supports deterministic image fixtures by default and `OPTIFIT_LIVE_IMAGES=1` for the live CDN check; `PLAYWRIGHT_CHANNEL=chrome` uses installed Chrome.
- Restart the IDE backend and create a new recommendation: previously saved responses do not contain image URLs.
