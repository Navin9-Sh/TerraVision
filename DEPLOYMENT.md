# Deploying TerraVision

The backend is a single Docker image (see `backend/Dockerfile`) plus a managed PostgreSQL
instance. Render is the recommended target for this project (reasoning below); Railway steps
are included too since the setup is nearly identical.

## Pre-deployment security checklist

### 1. No secrets are committed anywhere

Confirmed by direct search of the repo: `docker-compose.yml` and `application.yml` contain only
placeholder values (e.g. `local-dev-jwt-secret-please-override-with-32-plus-random-chars`,
`change-me-immediately`) or empty defaults (`${TERRAVISION_JWT_SECRET:}`) — every real value is
supplied as an environment variable at runtime, on whichever platform you deploy to. A repo-wide
search for the real Brevo credentials and test passwords used during development turned up zero
matches in any tracked file.

### 2. Required environment variables

Set every one of these to a **real** value before deploying — none of them are optional for a
working, secure deployment:

| Variable | Purpose | What happens if you forget it |
|---|---|---|
| `TERRAVISION_JWT_SECRET` | Signs/verifies login JWTs. Must be 32+ random characters — generate with `openssl rand -base64 32`. | **Fails fast at startup** (`JwtService` throws `IllegalStateException`) — the app will not start with a missing or short secret. Cannot be silently forgotten. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Seeds the one ADMIN account, first startup only. | **Fails fast** on a fresh database (`AdminSeeder` throws if unset and no admin exists yet). A no-op (safe to leave unset) on every startup after the first admin is created. |
| `MAIL_FROM_ADDRESS` | The "from" address on verification emails — must be a Brevo-verified sender. | Not a security risk, but registration emails will fail to send (logged, not fatal — accounts still get created, just unverifiable until you fix this and use resend-verification). |
| `BREVO_SMTP_USERNAME` / `BREVO_SMTP_PASSWORD` | Brevo SMTP login + generated SMTP key (from Brevo's **SMTP & API** tab — the SMTP login is `something@smtp-brevo.com`, not your account email). | Same as above — emails silently fail to send, nothing insecure happens. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Your managed Postgres connection details. | **This is the one gap that does *not* fail fast** — if left unset, the `prod` profile falls back to `terravision`/`terravision`. Against a real managed Postgres instance this simply fails to authenticate (not a silent security hole against your actual data), but you must still set these explicitly; don't rely on the fallback existing as a safety net. |
| `APP_BASE_URL` | Used to build the verification link emailed to users, and its redirect target. | Must be your real public URL (e.g. `https://terravision.onrender.com`) or verification links will point at `localhost` and be useless to real users. |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` so the app connects to your managed Postgres by service host, not `localhost`. | Wrong profile entirely if left as default (`dev`) — the app will try to reach `localhost:5432`, which doesn't exist in a deployed container. |

### 3. CORS

No CORS configuration exists in the codebase, and none is needed: the frontend (static HTML/JS)
and the API are served from the **same Spring Boot process, on the same origin**, both locally
and in the deployed container (the Dockerfile bakes the frontend into the same jar). CORS exists
specifically to govern *cross*-origin requests — since nothing here ever crosses an origin
boundary, there's nothing to configure. This would only become relevant if the frontend were
ever split out to a separately-hosted domain (a different Render/Vercel/Netlify service, for
example) — noted here so you know what would trigger needing it, not because it's needed now.

### 4. `.gitignore`

Fixed as part of this check: there was no repo-root `.gitignore` before now (only
`backend/.gitignore`), which meant `model-export/venv/` — a full Python virtual environment with
torch/torchvision binaries — had no exclusion at all. Added a root `.gitignore` covering that,
plus pre-emptive exclusions for `.env`/`application-local.yml`-style local override files (none
currently exist, but they must never be committed if created later), build output, and IDE/OS
junk. `backend/.gitignore` already correctly excludes `target/` and the model artifacts
(`backend/model/*.pt`, `classes.json`).

## How the model file gets into the container

`backend/model/terravision-resnet50.pt` and `backend/model/classes.json` (produced once,
locally, by `model-export/export_model.py`) are **baked into the Docker image** by
`backend/Dockerfile`'s `COPY model ./model` step. That means:

1. You must run the export script and ensure `backend/model/` is populated **before**
   building/pushing the image. `backend/model/*.pt` and `classes.json` are **committed directly
   to the repo** (not gitignored) specifically so that Render/Railway building straight from
   GitHub have it in the build context automatically, with no extra upload step.
2. The file is 94,044,996 bytes (~89.7 MiB) — safely under GitHub's 100 MiB hard limit, though
   over its 50 MiB soft-warning threshold, so `git push` prints a Git-LFS suggestion that's safe
   to ignore here.
3. The trade-off, worth being able to name in an interview: committing a 90 MB binary directly
   means (a) every image rebuild re-ships it even when only application code changed, and
   (b) every future retrain adds another ~90 MB to the repo's permanent git history rather than
   replacing it in place, since plain git doesn't diff binaries efficiently. A real production
   system would instead pull the model from object storage (S3/GCS) at container startup, keyed
   by a model-version tag, and/or use Git LFS if it must stay in the repo. For a single-model
   portfolio deployment, committing it directly is the right trade-off — it keeps the deployment
   to "push to GitHub, connect the repo" with zero extra infrastructure, and this repo will not
   be retrained often enough for the history bloat to matter.

**One more operational thing worth knowing before you deploy:** DJL downloads its native
libtorch binary and a small JNI shim from `publish.djl.ai` **at first startup**, not at image
build time (you saw this in the container logs: `Downloading jni https://publish.djl.ai/...`).
That means every fresh container start needs outbound internet access and takes an extra
~10-15 seconds the first time. This has worked in every test so far, but if your deployment
platform blocks outbound requests at startup, this is the first thing to check.

## Why Render over Railway for this project

Both build directly from a Dockerfile in a connected repo and support managed Postgres, so
either works. Render is the better fit here specifically because:

- Render's free tier includes a genuinely free managed Postgres instance (90-day limit, then a
  small monthly cost) and a free web service tier — enough to fully demo this project at no
  cost, which matters for a portfolio project.
- Railway moved to usage-based billing with a small monthly credit rather than an indefinite
  free tier — fine, but it's easier to accidentally leave a service running and accrue cost than
  it is on Render, where the free web service tier simply spins down when idle instead of
  charging you.
- The trade-off you're accepting with Render's free tier: the web service spins down after 15
  minutes of inactivity, so the first request after a period of no traffic pays the ~25-30
  second JVM+DJL+model-loading cold start you've already seen locally. For a demo you spin up
  when someone wants to look at it, that's a reasonable trade for zero cost; for something that
  needs to always feel instant, Railway (always-on, but metered) or a paid Render tier would be
  the better call.

## Render, step by step

1. Push this repository to GitHub — `backend/model/*.pt` and `classes.json` are committed
   directly (see above), so they're already in the build context with no extra step.
2. In the Render dashboard: **New > PostgreSQL**. Note the internal connection details (host,
   port, database, username, password) it gives you.
3. **New > Web Service** → connect the repo → Render detects `backend/Dockerfile`
   automatically (set **Root Directory** to `backend` if it doesn't).
4. Set every environment variable from the checklist above on the web service, using step 2's
   real Postgres connection details for `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`,
   your own generated value for `TERRAVISION_JWT_SECRET`, your real admin credentials, your real
   Brevo credentials, and `APP_BASE_URL` set to the `https://<your-app>.onrender.com` URL Render
   assigns (you'll see this after the first deploy, then update the env var and redeploy).
5. Set **Health Check Path** to `/actuator/health`.
6. Render builds the Dockerfile and deploys. Once live, hit
   `https://<your-app>.onrender.com/api/v1/health` (no auth needed) to confirm the model loaded,
   then open the root URL for the UI.

## Railway, step by step

1. Push this repository to GitHub (model files are already committed, per above).
2. **New Project > Deploy from GitHub repo** → select this repo.
3. **New > Database > Add PostgreSQL** in the same project — Railway shows that service's
   connection details under its own **Variables** tab as `PGHOST`, `PGPORT`, `PGDATABASE`,
   `PGUSER`, `PGPASSWORD`.
4. On the app service: **Settings > Root Directory** = `backend`, then set every environment
   variable from the checklist above under **Variables**, mapping `DB_HOST=${{Postgres.PGHOST}}`
   etc. using Railway's variable-reference syntax for the Postgres ones, and your own real
   values for the JWT/admin/Brevo/`APP_BASE_URL` ones.
5. Railway builds and deploys automatically on push. Confirm via `/api/v1/health`.

## Local production-like run

To exercise the exact same image/config path before deploying anywhere:

```bash
docker compose up --build
```

This builds `backend/Dockerfile`, starts Postgres, runs Flyway migrations automatically on
app startup, and serves the app at `http://localhost:8080` with the `prod` Spring profile
(app-to-Postgres over the Docker network, not localhost). This exact path has been run and
verified, including confirming the DJL native binary resolves to the Linux build
(`pytorch-native-cpu-2.3.1-linux-x86_64`, not the Windows one used for local `mvn
spring-boot:run`) automatically inside the container build stage.
