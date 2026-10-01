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
| `BREVO_API_KEY` | Brevo API key (**SMTP & API** tab > **API Keys**). Sends verification emails over HTTPS; **required on Railway Hobby and Render free**, where SMTP ports are blocked. | Emails fall back to SMTP, which those hosts block, so verification emails will not arrive. |
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

## Choosing a host (read this first)

Two facts about this app decide where it can run:

1. **Memory.** The JVM plus libtorch plus the ResNet50 model uses roughly **600 MB at idle**
   (measured with `docker stats`), more during inference. Any 512 MB plan, including Render's free
   and Starter tiers, will be killed for running out of memory. Use a host with **1 GB or more**.
2. **Outbound SMTP.** Render's free tier (since Sept 2025) and Railway's Free/Hobby plans block
   SMTP ports, so verification emails sent through Brevo's SMTP relay would silently fail. The
   app therefore supports **Brevo's HTTPS API**: set `BREVO_API_KEY` and emails go out over
   port 443, which no host blocks. (SMTP is still used locally when the key is unset.)

Recommended: **Railway (Hobby plan, about $5/month)**. It offers up to 8 GB per service, builds
straight from the Dockerfile and has managed Postgres. Render works too, but only on a paid plan
with at least 1 GB of RAM, and its free Postgres is deleted after 30 days.

## Free hosting: Hugging Face Space + Neon (no card)

A Gradio-type Hugging Face Space is free, has 16 GB of RAM and 2 vCPUs, and serves whatever listens on
port 7860. Docker-type Spaces are paid, so this uses a documented-by-nobody workaround: the Space's
`app.py` (see [deploy/huggingface/app.py](deploy/huggingface/app.py)) downloads Java, the model and the
application jar, then replaces itself with Spring Boot on port 7860. It was verified end to end in a
clean non-root Linux container (login, prediction, pages), but not on Hugging Face itself, and Hugging
Face could change the rules. Free Spaces sleep after about 48 hours without a visit and wake in about
a minute.

**Pieces**

| Piece | Where | Cost |
|---|---|---|
| App | Hugging Face Space (Gradio SDK) | free |
| Database | Neon Postgres (free plan) | free |
| Email | Brevo HTTPS API | free (300/day) |
| Jar | built by `.github/workflows/release-jar.yml`, published as the `deploy-latest` release | free |

**Steps**

1. **Neon:** create a project at neon.tech. Copy the *direct* connection details (host without
   `-pooler`), the database name, user and password.
2. **Jar:** push to `main`. The "Publish deployable jar" workflow builds the Linux jar and publishes it
   at `https://github.com/Navin9-Sh/TerraVision/releases/tag/deploy-latest`. Confirm `terravision.jar` is
   attached (Actions tab, then Releases).
3. **Space:** on huggingface.co choose *New Space*, name `terravision`, SDK **Gradio**, hardware
   *CPU basic* (free). Keep the generated `README.md`; replace the generated `app.py` with
   [deploy/huggingface/app.py](deploy/huggingface/app.py) (Files tab, then edit or upload).
4. **Secrets:** Space *Settings*, *Variables and secrets*. Add these as **secrets**:

   | Name | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<neon-host>/<db>?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | Neon user |
   | `SPRING_DATASOURCE_PASSWORD` | Neon password |
   | `TERRAVISION_JWT_SECRET` | 32+ random characters |
   | `ADMIN_EMAIL`, `ADMIN_PASSWORD` | the admin account to seed |
   | `MAIL_FROM_ADDRESS` | your Brevo-verified sender |
   | `BREVO_API_KEY` | Brevo API key (`xkeysib-...`) |
   | `APP_BASE_URL` | `https://<owner>-terravision.hf.space` |

5. The Space builds and starts (first boot downloads about 350 MB and takes a few minutes). Open
   `https://<owner>-terravision.hf.space`, then `/api/v1/health`.

**Notes**

- **ZeroGPU:** accounts without Hugging Face PRO can only create ZeroGPU Spaces, which are shut down
  with "No @spaces.GPU function detected during startup" unless the code registers one. `app.py`
  registers an unused `@spaces.GPU` function for that reason (it does nothing on other hardware) and
  runs Java as a child process so the Python process stays alive. The model itself runs on the CPU.
- Every restart of the Space downloads the latest jar, so pushing to `main` and restarting the Space
  deploys a new version.
- The Space's disk is not persistent. That is fine: users, predictions and tokens live in Neon, and
  Neon scales to zero when idle (the first request after a pause is a little slower).
- Rate limiting identifies clients by IP. If the logs show every user sharing one key, the Space's proxy is
  not being trusted as a forwarder; see `server.forward-headers-strategy` in `application.yml`.

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
5. Set `SPRING_PROFILES_ACTIVE=prod` and `BREVO_API_KEY`, then under **Settings > Networking** click
   **Generate Domain** to get the public URL. Put that URL (with `https://`) in `APP_BASE_URL`.
6. Railway builds and deploys automatically on push. Confirm via `/api/v1/health`, and set the health
   check path to `/actuator/health` under **Settings > Deploy**.

## Render, step by step (paid plan with 1 GB+ RAM only)

1. **New > PostgreSQL**, then note its internal host, port, database, user and password.
2. **New > Web Service**, connect the repo, set **Root Directory** to `backend` (Render detects the
   Dockerfile), and choose an instance type with at least 1 GB of RAM.
3. Set the environment variables from the checklist above (including `BREVO_API_KEY`,
   `SPRING_PROFILES_ACTIVE=prod` and the `DB_*` values from step 1). Set `APP_BASE_URL` to the
   `https://<your-app>.onrender.com` URL once Render assigns it.
4. Set **Health Check Path** to `/actuator/health`, deploy, and confirm `/api/v1/health`.

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
