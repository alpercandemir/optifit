# Operations and implementation decisions

## Environment

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPTIFIT_MODE` | `demo` | Explicit `demo` or `live`; no automatic fallback |
| `GEMINI_API_KEY` | empty | Required for live analysis |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Configurable vision/structured-output model |
| `TAVILY_API_KEY` | empty | Required for live product discovery |
| `ALLOWED_MERCHANTS` | Three research domains in `.env.example` | Exact HTTPS hosts permitted for verification |
| `SESSION_HOURLY_LIMIT` | 5 | Per-session paid-analysis admission cap |
| `IP_HOURLY_LIMIT` | 20 | Additional abuse limit |
| `DAILY_ANALYSIS_LIMIT` | 100 | Process-wide maximum daily analyses |
| `COOKIE_SECURE` | false | Set true under HTTPS |
| `DATABASE_URL` | in-memory H2 | Optional external JDBC database |
| `DATABASE_USER`, `DATABASE_PASSWORD` | local H2 defaults | Database credentials |
| `PORT` | 8080 | Backend port |

Provider-side spending limits must also be set. Analysis-count limits are not a currency budget; model and search costs vary. Token metrics are available to an operator-configured protected metrics exporter. No prompt or photo content should be enabled in observability logs.

## Decisions relative to the PRD

1. **Temporary photos stay in process memory.** S3 was a suggested architecture, not necessary for the one-hour anonymous flow. Avoiding a photo store removes public URL, lifecycle configuration and backup risks. Original input is re-encoded with EXIF orientation; application-owned bytes are cleared after use. No guarantee is made about physical RAM forensics or provider-side retention.
2. **Local default is H2 in memory.** A PostgreSQL JDBC option is present. No permanent user or product catalogue exists; live product pages are verified anew. The JVM restart discards sessions and local results. Do not use filesystem H2 or backup personal result tables while promising ephemeral operation.
3. **Ranking is deterministic and grounded.** Spring AI supplies image-quality assessment and proposed frame shapes. Backend filtering/ranking selects verified products, merges same-model offers and generates explanations from known attributes. The model cannot invent URLs or prices.
4. **Demo is explicit.** It has three researched sunglass examples, no fabricated prices, and no face analysis. Optical demo is intentionally empty. All personal recommendation claims are reserved for live analysis. Demo fixtures do not establish catalogue completeness.
5. **Single-node deployment.** Worker queue, session and rate limits are in-process. Before horizontal scaling, use a shared bounded job queue, shared expiring sessions/limits, safe photo handoff, and coordinated cancellation. Startup should not attempt to resurrect photo jobs after a crash because the input has been destroyed.
6. **Styles/colours are ranking preferences; budget/category are hard filters.** An unknown shape is not silently labelled with an inferred frame shape.
7. **Product photographs come from verified product metadata.** Missing or failed images use labelled placeholders. Confirm merchant image usage rights before public launch. Fonts are bundled locally; no Google Fonts request is needed at runtime.

## PostgreSQL option

Use a dedicated ephemeral database/schema with backups, statement logging of parameters, replication and snapshots disabled for personal-result data. Set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` and `SPRING_PROFILES_ACTIVE=postgres`. The postgres profile creates `recommendation_jobs` as UNLOGGED and removes expired rows. UNLOGGED alone does not exempt a table from logical dumps: exclude this entire database from backups. The default local/container configuration requires no PostgreSQL server.

If switching from an existing logged table, provision a fresh dedicated database; `CREATE TABLE IF NOT EXISTS` cannot change an existing table's logging policy. Servlet sessions remain in memory, so old results are not accessible after a backend restart even with an external database.

## Proxy and limits

The compose backend has no published port; Nginx applies per-client upload limits and overwrites `X-Forwarded-For`. The backend intentionally uses the socket peer address by default and does not blindly trust forwarded headers. This means its IP cap is shared for clients behind that proxy until trusted proxy handling is configured. Configure Spring/Tomcat trusted internal proxy addresses for the deployment, and only then enable forwarded-address handling. Do not trust arbitrary Internet-provided headers.

Public deployment also needs a TLS terminator, `COOKIE_SECURE=true`, protected provider keys, request rate/body limits before image decoding, and an egress policy denying private/metadata networks. App-level hostname allowlisting and DNS checks reduce SSRF exposure; egress controls provide defense against DNS rebinding. Do not add arbitrary user-provided hosts to `ALLOWED_MERCHANTS`.

## Live launch gates

- Both API credentials and a selected account-accessible vision model.
- Live end-to-end calls, quality evaluation on consented diverse photos, and measured latency/token costs.
- Two permitted and reliable product sources with three-model coverage for **both** product categories. Current optical-frame coverage is not verified.
- Merchant metadata parser checks against current pages; denied/missing/mismatched pages produce fewer/no recommendations.
- Source usage agreements, provider/data processing disclosures and the chosen hosting region reviewed for the intended deployment.
- Operational monitoring of errors, timeouts, cleanup, quotas and spend; protected metrics collection.
- Container and deployment smoke test. Container definitions are supplied, but only local execution was verified here.
