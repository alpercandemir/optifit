# OptiFit — Product Requirements Document

- Version: 0.4
- Date: 26 September 2026
- Status: initial product decisions captured; proposed assumptions remain explicitly identified.
- Implementation has been authorized. Implementation decisions and launch conditions are tracked in `docs/OPERATIONS.md`; executed checks are tracked in `docs/VERIFICATION.md`. The application has not been publicly deployed.
- Language convention: source code, API messages and project documentation are English. User-facing interface copy is Turkish; localization belongs to the frontend.

## 1. Product summary

OptiFit analyzes a user-uploaded face photo with an Google Gemini model supporting image input and recommends **three distinct eyewear models** available from online stores serving Turkey. Users choose optical frames or sunglasses. Each recommendation includes product details, a short explanation, a product page, a merchant link and sharing actions.

Users can upload a photo and receive recommendations without registering or signing in. Account management is outside the MVP.

The product narrows a large catalog into a personal shortlist based on visual style and preferences. It must not infer exact physical measurements, prescriptions or medical suitability from a photo. The interface explains that these are visual-style suggestions and that fit and comfort must be checked by trying on the frames.

## 2. Decisions and assumptions

### Confirmed user decisions

| Topic | Decision |
| --- | --- |
| Market and interface | Turkey; Turkish user experience |
| Categories | Prescription eyewear frames and sunglasses |
| Successful result | Three distinct models per completed analysis |
| Product source | Real online products with shareable product URLs |
| Optician links | Online stores selling the products; no nearby physical-store search |
| Access | Anonymous access; no registration or login flows in the MVP |
| Technology | Spring Boot 4, Spring AI, Vue 3 |
| Revenue | Free initial version; affiliate commerce and payments deferred |
| Preferences | Required eyewear category; optional budget, style and color |
| Design | Modern, striking, carefully crafted and comfortable to use on mobile |

### Proposed assumptions

These are starting assumptions rather than separately confirmed requirements:

- Each analysis targets one eyewear category; budgets use TRY.
- Anonymous access does not include a permanent photo library or recommendation history. The earlier account-based opt-in retention design is superseded; any future anonymous persistence needs its own design.
- Results remain available in the originating temporary browser session for one hour from analysis creation. Photos are deleted as soon as the analysis no longer needs them.
- The MVP targets adult users; age is not inferred from photos.
- At least two Turkish online eyewear sources must have verified access and usage terms before launch. No merchant agreement is presumed.

## 3. Target users and success measures

The target user wants to compare potentially suitable eyewear before purchasing and finds large store catalogs difficult to navigate.

The following are proposed pilot targets, not measured outcomes:

| Metric | Target / measurement |
| --- | --- |
| Complete results | At least 90% of analyses with valid photos and supported filters return three products; partial results are reported separately |
| Recommendation usefulness | At least 70% of 50 or more pilot respondents find one recommendation useful |
| Link accuracy | At least 95% model-to-page agreement in a pre-launch sample; zero fabricated URLs |
| Latency | p95 at most 30 seconds after upload; terminal result or explanatory error within 60 seconds |
| Product interest | Measure the baseline proportion of result views leading to a product-page visit |
| Cost | Record model/search usage per successful analysis and establish a spending limit before launch |

## 4. MVP scope

Included: anonymous sessions; single-photo selection, preview, replacement and validation; photo usability assessment; visible facial geometry and style analysis; category/preferences; online product discovery and verification; ranking three distinct models; product information and explanations; product/store links and sharing; temporary result access/deletion; partial, empty, failure and retry states.

Product images may be shown only when available and permitted. Product attributes must come from source evidence. A missing product image receives a clear placeholder rather than an invented product photograph.

Deferred: virtual try-on/AR, drawing frames onto photos, maps, geolocation, carts, payments, prescription uploads, lens selection, precise face/pupillary-distance measurements, native mobile apps and merchant administration. Public personal-result pages are also excluded; sharing uses product/store URLs directly.

Registration, login, password resets, account management, permanent photo libraries and recommendation history are outside the MVP.

## 5. User journey

1. Understand the service and where the photo will be processed; start without an account.
2. Select eyewear category and optionally enter budget, style and color.
3. Upload a well-lit, front-facing image containing one unobstructed face; preview or replace it.
4. Review photo processing, deletion and temporary-result access information.
5. Start the analysis and see actual photo-check, product-search and recommendation stages.
6. Compare three recommendations and their explanations; visit or share a product/store.
7. Reopen results within the same session before expiry, or delete them. Losing the session or reaching expiry requires a new analysis. Product links can be copied for later use.

### 5.1. Visual direction

A modern, striking interface is a core requirement. Strong editorial imagery, expressive typography and careful product presentation should emphasize eyewear and personal style. The value proposition and photo-upload action must be immediately apparent.

The initial design direction is:

- Fashion/eyewear editorial styling: warm off-white backgrounds, graphite text, cobalt accents, generous spacing and controlled rounded corners.
- Expressive headings and readable body type supporting Turkish characters, with a clear hierarchy of headings, explanations and actions.
- A concise first-screen value proposition, large editorial image and prominent upload action; legible free/anonymous/deletion information; clearly labelled promotional imagery.
- Desktop drag-and-drop and universal file selection; clean photo preview, easy replacement and practical photo guidance. Optional filters must not overshadow the primary action.
- Real processing stages with restrained transitions. Do not invent progress percentages or animate measurements that do not occur.
- Three product cards emphasizing model, explanation, available price and store action; side-by-side comparison on desktop and a vertical mobile flow with every alternative reachable.
- Consistent image areas without cropping frames. Missing images use labelled placeholders; AI imagery must not masquerade as actual product photography.
- Hover, focus and pressed states; explicit sharing feedback; purposeful brief motion disabled where reduced motion is preferred.
- Consistent first-visit, upload, processing, success, partial, empty, failure and expiry screens, each with a clear next action.

### 5.2. Design quality

Shared tokens define color, typography, spacing, radii, shadows and motion. Validate 360, 390, 768 and 1440 px widths and 200% text scaling: no horizontal overflow, clipped content or overlapping actions in the primary flow.

Body copy is at least 16 px; primary touch targets are at least 44 by 44 px. Normal-text contrast is at least 4.5:1. Focus is visible and state is not communicated by color alone.

Reserve media dimensions to prevent layout shifts and serve appropriately sized images. The primary action must not wait for image loading or animation. Prepare detailed desktop/mobile landing, upload and result designs, then verify implementation using realistic content, long names and absent images.

## 6. Functional requirements

### FR-01 — Anonymous access and session isolation

- Do not require email, passwords, registration or login for upload, analysis, results or sharing.
- Automatically create a secure, unpredictable temporary server session.
- Bind every job to its originating session and enforce ownership for status, results and deletion. A job ID alone does not grant access.
- Refresh resumes an unexpired job within the same session. A lost cookie does not transfer results to another session.
- Explain that a new analysis is required after session/result expiry.

### FR-02 — Photo upload

- Accept JPEG, PNG and WebP, at most 10 MB and 20 megapixels.
- Validate MIME type, actual file signature and decodability on the server; never trust the extension alone.
- Apply orientation, remove EXIF/location metadata and create a temporary analysis image with a maximum 1600 px longest edge. Apply the same deletion policy to it.
- Stop for no face, multiple faces or severely blurred/obscured faces and provide retake guidance.
- Never send an invalid file to the model. Any model-based quality check counts toward the analysis time/cost budget.

### FR-03 — Visual analysis

- Inspect only visible facial contours, approximate proportions and frame-style features; allow unknown or uncertain attributes.
- Do not identify people, perform biometric matching or infer health, ethnicity, personality or gender from photos.
- Do not present face shape as a certain classification or estimate millimeter measurements or pupillary distance.
- Use Spring AI and validate the response schema server-side. Never execute raw model output in the UI.
- Configure the model externally. Select the final model through image/structured-output compatibility, Spring AI integration, explanation quality, latency and cost evaluation. Provider prompts and backend explanations are English; the frontend owns Turkish presentation.

### FR-04 — Online product discovery

- Model memory alone is not a product source. `ProductSearchProvider` must return real search/API/catalog evidence.
- Prefer merchant APIs or permitted feeds. Otherwise use a licensed search service and permitted product-page parsing.
- Search services receive product criteria only, never photos, user identifiers or raw face analysis.
- Record brand/model, category, merchant, product URL and verification time. Populate price, stock, color and dimensions only when supported by evidence.
- Product links must lead to individual products, not search/category pages.
- HTTP 200 is insufficient: model identity and page content must match. Blocked/unreadable pages are not verified.
- Validate URLs including redirects and prevent private-network access. Treat page instructions as untrusted data.
- Price/availability must be based on checks at most 24 hours old, with timestamps. Refresh or hide older data.
- Isolate source failures and never fill missing results with fabricated products.

### FR-05 — Three alternatives

- `COMPLETED` contains exactly three distinct brand/model identities. Color/size variants and multiple sellers of the same model are not separate alternatives.
- Apply category and explicit budget constraints first, then visual-style fit, preferences, evidence reliability and diversity.
- Unknown prices do not satisfy an explicit budget. Never silently relax filters.
- Any model selection is restricted to discovered candidate IDs. The backend supplies verified URLs, prices and merchant information.
- Each card has a short Turkish explanation rendered by the frontend. Do not show uncalibrated certainty scores.
- Include brand/model, category, known attributes, explanation, merchant, product/store URLs and verification time.
- Optical recommendations concern frames. Do not assume prescription lenses are included unless the source explicitly states it.
- Return `PARTIAL` for one or two valid candidates and `NO_MATCH` for none, with filter/retry guidance. Neither counts as a complete success.

### FR-06 — Sharing

Share the verified product or merchant URL separately. Use Web Share API where available, otherwise clipboard copying, with clear feedback. Never share the photo, facial assessment or session token.

### FR-07 — Retention and deletion

- Photos and personal results are temporary; no permanent retention option is provided.
- Delete original/processed photos when the model no longer needs them, on success or failure. Abandoned jobs must be cleaned within one hour of analysis creation.
- Retain personal job/results data for at most one hour from creation. Polling and refresh do not extend expiry; show `expiresAt` in the interface.
- Do not persist raw model responses. Remove detailed facial attributes after completion; retain only necessary recommendation explanations and product matches.
- Deletion immediately revokes access, cancels ongoing work and clears temporary data. Late provider callbacks cannot restore it. Cleanup must remain within the one-hour bound.
- Exclude temporary photos, personal results and sessions from backups. A shared product catalog can be retained separately.
- Application deletion does not guarantee zero provider-side retention. Explain the selected provider's current retention terms separately.

## 7. Technical approach

| Layer | Requirement / initial proposal |
| --- | --- |
| Backend | Spring Boot 4.x; Java 21, Maven and a modular monolith proposed |
| AI | Spring AI with a Boot 4-compatible 2.0.x release; calls through its abstractions |
| Frontend | Vue 3; TypeScript, Vite and Vue Router initially proposed |
| Data | PostgreSQL product catalog proposed; separate temporary, non-backed-up session/job/results storage |
| Photos | Private S3-compatible temporary storage with expiry initially proposed |
| Access control | Spring Security, automatic sessions, Secure/HttpOnly/SameSite cookies, CSRF and per-job ownership |
| Search | Replaceable `ProductSearchProvider` and merchant adapters |
| Operations | Configurable quotas, timeouts, bounded retries, cost and failure metrics |

These are initial architecture proposals, not claims about installed infrastructure. The implementation uses in-memory photo processing and H2 by default, optional PostgreSQL, Tavily Search and a single-page Vue flow. See `docs/OPERATIONS.md` for the implemented choices and their limits.

Spring AI's 2.0.x getting-started documentation describes Boot 4.0.x/4.1.x support. Pin exact patch versions through the BOM and verify dependency compatibility. [Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)

Image analysis and web search are separate capabilities. Do not assume image support automatically provides internet search. [Spring AI Google Gemini Chat](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html)

Flow: Vue → Spring Boot → photo validation → Spring AI assessment → product provider → source/filter verification → ranking → three cards. Track long-running work by job ID rather than keeping the upload request open. A bounded worker pool and expiring job records are sufficient for the initial MVP; a separate queue is a scaling option.

### Service contract

| Operation | Endpoint | Behavior |
| --- | --- | --- |
| Temporary session | `GET /api/v1/session` | Automatically provides session cookie and CSRF token without requesting user identity |
| Start analysis | `POST /api/v1/recommendations` | Photo/category/preferences; `202`, `jobId`, `expiresAt`; binds the job to the temporary session |
| Status/result | `GET /api/v1/recommendations/{id}` | Owner session only; status, explanations and products |
| Delete result | `DELETE /api/v1/recommendations/{id}` | Owner session only; cancellation and temporary-data deletion |

States: `QUEUED`, `ANALYZING`, `SEARCHING`, `COMPLETED`, `PARTIAL`, `NO_MATCH`, `FAILED`. Polling a terminal result must not start a paid analysis. POST supports session-scoped idempotency throughout the validity period; an explicit new analysis requires user action and quota checks. Deleted, expired and other-session jobs return a non-disclosing `404`; the frontend explains that a new analysis is required.

Results include `jobId`, `status`, `recommendations[]`, `warnings[]`, `expiresAt`. Recommendations contain `productId`, `brand`, `modelCode`, `category`, `reasonCode`, English `reason`, `attributes`, `offers[]`. Offers contain `merchantName`, `productUrl`, `merchantUrl`, optional `price`, `currency`, `availability`, `checkedAt`. Omit products missing required evidence. Backend errors and warning messages are English; frontend translations provide Turkish UI copy.

## 8. Reliability, data and performance

- Keep API keys in the backend. Do not expose photos through public buckets or permanent public URLs.
- Enforce server-side session ownership; unpredictable job IDs do not replace authorization.
- Do not log images, base64, facial assessments, session tokens or API keys. Do not use session-recording analytics on photo/results screens.
- Respect product-image usage permissions and evaluate delivery to avoid unnecessary disclosure of user information to merchants.
- Proposed quota: five analyses per session/hour, with additional IP and service-wide limits. Cookie deletion can bypass session limits, so session limits alone are insufficient. Tune IP limits for shared networks during the pilot.
- Allow at most two retries for transient provider failures within the 60-second total budget. Never loop automatically on permanent failures, invalid files or model refusals.
- Monitor cleanup failures. Expired records must be inaccessible even if cleanup is delayed; temporary personal data must not enter backups.
- Support keyboard use, labelled forms and accessible errors on desktop and mobile.
- Before launch, assess processing notices, retention, cross-border providers and source usage rights for the target market. This PRD does not certify legal compliance.

Image models may make mistakes, so retake and uncertainty states are essential. [Images and vision](https://ai.google.dev/gemini-api/docs/image-understanding)

Gemini API data handling depends on the service tier and account settings; review Google’s current terms before processing personal photos. Do not promise that data is never retained anywhere. Verify the selected endpoint's settings during implementation. [Data controls](https://ai.google.dev/gemini-api/terms)

## 9. Source research examples

These were researched on 26 September 2026 and are not personal recommendations. Integration permission, API access, continuing stock and commercial partnerships were not established. No fixed prices are included because prices change.

| Model | Category | Product | Store | Research status |
| --- | --- | --- | --- | --- |
| Ray-Ban RB4171 Erika Classic | Sunglasses | [Product](https://www.atasunoptik.com.tr/rayban-rb-4171-6228g-5418-unisex-gunes-gozlukleri_78158) | [Atasun Optik](https://www.atasunoptik.com.tr/) | Product page opened; model and attributes inspected |
| Ray-Ban RB2140 Wayfarer Tortoise | Sunglasses | [Product](https://www.atasunoptik.com.tr/ray-ban-rb-x1-2140-902-5022150-erkek-gunes-gozlukleri_81552) | [Atasun Optik](https://www.atasunoptik.com.tr/) | Product page opened; model inspected |
| DB DB7128/S 6C59O 58-18-150 | Sunglasses | [Product](https://www.istanbuloptik.com.tr/DB-DB7128-S-6C59O-58-18-150-Erkek-Gunes-Gozlugu) | [Istanbul Optik](https://www.istanbuloptik.com.tr/) | Found in search index; direct opening failed and re-verification is required |

These are research examples, not a personal recommendation catalog or a fixed trio for every user. Research favored sunglasses. **Adequate optical-frame coverage, access and usage permission are separate mandatory launch gates.** Never substitute sunglasses for optical frames. The third example is unverified until it passes FR-04.

## 10. Acceptance criteria

| ID | Scenario and expected result |
| --- | --- |
| AC-01 | Valid photo/category and sufficient catalog evidence produce exactly three distinct models with real product/store URLs |
| AC-02 | Three color variants of the same model do not count as three alternatives |
| AC-03 | Invalid type/size receives a clear error without starting product recommendations |
| AC-04 | No face, multiple faces or poor quality triggers retake guidance rather than fabricated certainty |
| AC-05 | Two budget-matching products yield `PARTIAL`; zero yield `NO_MATCH`; filters stay intact |
| AC-06 | No invented prices/measurements; reject model products or URLs outside discovered candidates |
| AC-07 | Broken or wrong-model URLs are not successful results; explain provider outages |
| AC-08 | Product/store sharing is separate and excludes photos and personal analysis |
| AC-09 | Delete photos after their required stage; results expire/clean after an hour; late callbacks cannot restore deleted data |
| AC-10 | Other sessions cannot read/delete a job; knowing its ID is insufficient |
| AC-11 | Duplicate submission produces one job; timeout/quota errors have clear feedback |
| AC-12 | Optical requests never complete with sunglasses or claims about lens/prescription suitability |
| AC-13 | Upload, three cards, store navigation and sharing are keyboard-accessible on mobile/desktop |
| AC-14 | A new user can upload, receive recommendations and share without registration, login or email |
| AC-15 | Refresh preserves same-session access before expiry; expiry/session loss offers a new analysis |
| AC-16 | Landing, upload and result screens consistently implement section 5.1 with clear actions |
| AC-17 | No overflow/occlusion at target widths and 200% text scaling; all mobile results remain reachable |
| AC-18 | Contrast, touch, focus and reduced-motion requirements hold across loading/error/missing-image states |

Validation includes service integration, merchant-adapter contracts, end-to-end flows and model evaluation using diverse consented photos. Evaluate uncertainty, explanation consistency, invalid-photo handling and unsupported inferences. Schema-valid JSON alone does not demonstrate recommendation quality.

## 11. Delivery and open decisions

1. Confirm proposed TTL/quotas; anonymous access, free use and optional filters are already decided.
2. Verify Boot 4/Spring AI image and structured-output compatibility, model cost/latency and two permitted sources covering both categories.
3. Design shared tokens, desktop/mobile screens and every processing state.
4. Implement sessions, photos, jobs, source discovery, three cards, navigation and sharing.
5. Verify session isolation, deletion, expiry and anonymous quotas.
6. Validate acceptance, visual quality, mobile use, recommendations, performance, spending and source rights before pilot/launch.

| Open topic | Resolution |
| --- | --- |
| Merchant/provider access | Select during feasibility; no existing agreements assumed |
| Final model and spending budget | Determine through quality, latency and cost evaluation |
| Result TTL and quotas | Proposed one-hour access and five analyses/session/hour; finalize before pilot |
| Hosting and service region | Choose based on traffic, budget and processing requirements |

The largest feasibility risk is sufficient current, permitted product coverage in both categories. The three-recommendation service is not production-ready until source verification is complete.
