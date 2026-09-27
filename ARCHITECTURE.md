# Architecture & Design Decisions

This document exists so every choice in this system can be explained and defended, not just
described. It's organized as a series of "why" questions, in the order a reviewer or
interviewer would naturally ask them.

## Why Spring Boot + DJL instead of a Python backend?

The constraint driving this whole rebuild was: one JVM process, no Python running live. The
open question at the start was whether Deep Java Library could actually execute this specific
model correctly — if it couldn't, the fallback would have been a small internal Python
inference microservice, called out explicitly rather than silently substituted.

It turned out to be a clean fit, for a specific reason: the model is a **stock
`torchvision.models.resnet50`** with only the final `fc` layer replaced — no custom modules,
no data-dependent control flow (no `if` branching on tensor values, no loops with
data-dependent bounds). That matters because `torch.jit.trace` (as opposed to
`torch.jit.script`) records one concrete execution path through the model; it produces a
graph that silently diverges from the original whenever the model's behavior depends on the
actual tensor values rather than just their shapes. A model without that kind of branching
traces cleanly, and DJL's PyTorch engine — which embeds libtorch via JNI, the same runtime
PyTorch itself uses in production — executes a traced graph directly, with no reimplementation
of the model's logic in Java. `model-export/export_model.py` also asserts the traced output
matches the eager model's output (< 1e-5 max difference) before ever saving the artifact, so a
silent divergence would fail loudly at export time, not at inference time in production.

The trade-off this accepts: retraining or architecture changes require re-running the export
script and rebuilding the Docker image, rather than just restarting a Python service pointed
at new weights. For a single-model project, that's a worthwhile trade for eliminating an
entire runtime (and its own scaling/deployment/dependency-management story) from the live
system.

## Why letterbox resize instead of the original center-crop?

The original Flask prototype center-cropped each uploaded image to a square before resizing to
224×224. A center crop discards whatever falls outside that square — for a satellite tile
where relevant land-use signal can extend to the edges of the frame, that's a real information
loss. The Java preprocessing (`ImagePreprocessor`) instead scales the image to fit within
224×224 while preserving aspect ratio, then pads the leftover space with the ImageNet mean
pixel color (rather than black, which would introduce a stark edge the network never saw in
training). The cost is some padding pixels for non-square inputs; the benefit is that no part
of the original image is thrown away. This is a deliberate improvement over the original
behavior, not a faithful port of it.

## Why flag low-confidence predictions instead of just returning the top class?

This model was trained exclusively on Sentinel-2 tiles — a narrow, specific image
distribution (top-down, 10 m/pixel, atmospherically-corrected). It will legitimately struggle
on anything outside that distribution: a phone photo, a drone image, a screenshot. A system
that silently returns "Forest, 34% confidence" as if it were a normal answer misrepresents what
the model actually knows. `ClassificationService` compares the top-1 probability against a
configurable threshold (`terravision.inference.confidence-threshold`) and, when it's below
that threshold, returns `lowConfidence: true` plus an explicit domain-shift warning — in the
JSON response and in the UI banner. This is a correctness/honesty decision, not a UX
afterthought.

## Why a custom API key filter instead of JWT or OAuth2?

There's no concept of separate user accounts in this system — it's a single-consumer
portfolio API, not a multi-tenant product. JWT and OAuth2 exist to solve problems this project
doesn't have: authenticating individual humans, issuing/refreshing tokens, managing sessions
across a login flow. Building that machinery here would be complexity added for its own sake.

A shared-secret header (`X-API-Key`, checked in `ApiKeyAuthFilter` using
`MessageDigest.isEqual` for constant-time comparison) does the one thing this project actually
needs: deter casual scraping/abuse of a publicly deployed inference endpoint. It's explicitly
**not** real per-user security — the key is visible to anyone who opens browser dev tools on
the frontend, since the static JS embeds it to call the API. If this project grew into
something with real user accounts, JWT-based auth issued after a login flow would replace this
outright, not extend it.

## Why Flyway instead of Liquibase?

Flyway migrations are plain, ordered SQL files — what you read is exactly the DDL that runs,
with nothing to translate through an XML/YAML changelog abstraction. Liquibase's extra layer
(database-agnostic changesets, per-changeset rollback) earns its cost on projects targeting
multiple database vendors or needing fine-grained rollback of individual schema changes. This
project has one schema, one database engine, and a linear migration history — Liquibase's
abstraction would be complexity without a corresponding problem to solve.

## Why `JpaSpecificationExecutor` instead of derived query methods for history filtering?

`/api/v1/history` supports four independent, optional filters (class, date-from, date-to,
low-confidence-only). A derived query method covering every combination of "some filters
present, others absent" would require either one method per combination (combinatorial
explosion) or manual `if` branching to pick which repository method to call. Specifications
compose instead: `PredictionSpecifications.predictedClassEquals(...)` returns a predicate that
is `null` (meaning "always true", so it drops out of the query) when its argument is absent,
and `Specification.allOf(...)` combines all four independently. Adding a fifth filter later
means adding one more specification to the list, not restructuring existing query methods.

## Why is the ML model exported to the image instead of mounted as a volume?

`backend/Dockerfile` copies `backend/model/` into the image at build time. The alternative —
pulling the model from object storage at container startup — is the more scalable pattern for
a system with multiple models or frequent retraining, but it adds infrastructure (a storage
bucket, a startup download step, versioning/caching of that download) this project doesn't
otherwise need for one model that changes infrequently. The trade-off is documented in
[DEPLOYMENT.md](DEPLOYMENT.md): every image rebuild re-ships the model even when only
application code changed.

## Does the DJL/JVM inference path have latency issues worth addressing?

One real one, worth flagging honestly rather than glossing over: `ClassificationService`
creates a new DJL `Predictor` per request (`model.newPredictor()` inside try-with-resources).
This is necessary because a DJL `Predictor` is **not thread-safe** — a single shared instance
reused across concurrent requests would corrupt state under load. DJL's own guidance is that
predictor creation is cheap relative to the forward pass itself, but it's not free, and under
sustained concurrent load, pooling predictors (e.g. via Apache Commons Pool, sized to the
available CPU cores) would reduce that per-request allocation overhead. This project doesn't
add that pooling layer, since its expected load doesn't warrant the extra dependency and
complexity — but it's the specific next optimization to reach for if latency under concurrency
became a problem.

Separately, repeat uploads of *identical* image bytes are cached by SHA-256 hash
(`ClassificationService.predictCached`, backed by Spring's default in-memory
`ConcurrentMapCacheManager`) — common during manual testing and demos, where re-running
inference on bytes already classified is pure waste. That cache is unbounded, which is fine
for this project's expected traffic but would need a bounded/TTL'd implementation (e.g.
Caffeine) in a production deployment with sustained unique traffic.

## Why plain static HTML/CSS/JS instead of Thymeleaf or a JS framework?

The frontend calls the REST API via `fetch()` rather than being server-rendered, so Thymeleaf
(which shines when the server injects data directly into HTML on render) wouldn't be doing
anything Thymeleaf is actually for here — the API already exists and already returns JSON. A
separate JS framework (React/Vue/etc.) would add a build pipeline (bundler, transpiler, node
toolchain) for four static pages with straightforward DOM updates. Plain HTML/CSS/JS served
directly from `src/main/resources/static/` keeps the entire system inside the Spring Boot
stack with zero additional tooling, at the cost of doing DOM manipulation by hand instead of
with a framework's reactivity — an acceptable trade for this project's scope.
