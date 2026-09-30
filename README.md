<p align="center">
  <img src="backend/src/main/resources/static/favicon.svg" alt="TerraVision logo" width="88" height="88">
</p>

<h1 align="center">TerraVision</h1>

<p align="center">
  Satellite land-use classification, served end-to-end on the JVM.<br>
  A ResNet50 trained on EuroSAT (Sentinel-2) runs natively inside Spring Boot via Deep Java Library, with no Python in the live system.
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-b5502e?logo=openjdk&logoColor=white">
  <img alt="Spring Boot 3.3" src="https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white">
  <img alt="PostgreSQL 16" src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white">
  <img alt="DJL / PyTorch" src="https://img.shields.io/badge/DJL-PyTorch%202.3-EE4C2C?logo=pytorch&logoColor=white">
  <img alt="Docker" src="https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white">
  <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-yellow.svg">
</p>

---

## Contents

- [Overview](#overview)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Quick start](#quick-start)
- [Local development](#local-development)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Model card](#model-card)
- [Project structure](#project-structure)
- [Testing](#testing)
- [Roadmap](#roadmap)
- [Documentation](#documentation)
- [License](#license)
- [Author](#author)

## Overview

TerraVision classifies a satellite image tile into one of 10 land-use / land-cover classes
(annual crop, forest, highway, residential, river, and so on). It is a full rebuild of an earlier
Flask prototype into a production-shaped system: a Spring Boot API, PostgreSQL persistence,
JWT-based accounts with email verification, an admin dashboard, and a Docker deployment.

The design choice that defines the project: the trained PyTorch model is exported **once** to
TorchScript and then executed **inside the JVM** through Deep Java Library (DJL). Python is only
an offline export step and never runs alongside the application. The reasoning is written up in
[ARCHITECTURE.md](ARCHITECTURE.md).

## Features

**Classification**
- Upload a satellite tile and get the predicted class, a description and the model's confidence.
- Low-confidence guard: predictions below a configurable threshold are flagged with an explicit
  warning instead of being presented as a confident answer.
- Results are cached by the SHA-256 of the image bytes, so a repeat upload returns instantly.

**Accounts and access**
- Registration with email verification (Brevo SMTP), BCrypt password hashing and stateless JWT login.
- Separate user and admin sign-in, with role-based authorization on `/api/v1/admin/**`.
- Logout fully clears the session, and protected pages never reappear via the browser's Back button.

**History and analytics**
- Per-user prediction history with filters (class, date range, low-confidence only) and pagination.
- Personal stats: totals, average confidence, low-confidence rate, class distribution.
- Admin dashboard listing every user and every prediction across accounts.

**Pune district LULC map**
- An interactive land-cover map of Pune district, generated from Sentinel-2 imagery with Google
  Earth Engine (21,980 tiles classified at 10 m resolution).

**Operations**
- Flyway-managed schema migrations, Actuator health and Prometheus metrics, OpenAPI / Swagger UI,
  and per-request IDs in every log line.
- Fail-fast startup: a missing JWT secret or admin credentials stops the app with a clear error
  rather than falling back to something insecure.

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 21 |
| Backend | Spring Boot 3.3, Spring Security, Spring Data JPA |
| Inference | Deep Java Library 0.29 (PyTorch 2.3.1 engine), TorchScript model |
| Database | PostgreSQL 16, Flyway migrations |
| Auth | JWT (stateless), BCrypt, email verification via Brevo SMTP |
| Frontend | Plain HTML, CSS and JavaScript served by Spring Boot (no framework) |
| Observability | Spring Boot Actuator, Micrometer / Prometheus |
| Packaging | Docker, Docker Compose |
| Model training / export | Python, PyTorch (offline only) |

## Architecture

<img width="1536" height="1024" alt="image" src="https://github.com/user-attachments/assets/3144001e-9823-47ef-ab15-87351ee4ba35" />


The frontend and API are served from the same Spring Boot process and origin, so no CORS
configuration is needed. See [ARCHITECTURE.md](ARCHITECTURE.md) for the design decisions in full.

## Quick start

The fastest way to run everything (app, PostgreSQL and a pgAdmin browser for the database) is
Docker Compose.

**Prerequisites:** Docker Desktop and Git. The model files must exist under `backend/model/`
(they are committed; see [Model card](#model-card) to regenerate them).

```bash
git clone https://github.com/Navin9-Sh/TerraVision.git
cd TerraVision

cp .env.example .env        # then edit .env and set real values (see Configuration)
docker compose up --build
```

Then open:

| URL | What |
|---|---|
| http://localhost:8080 | Web app |
| http://localhost:8080/swagger-ui.html | Interactive API docs |
| http://localhost:5050 | pgAdmin (local development only) |

Sign in with the `ADMIN_EMAIL` / `ADMIN_PASSWORD` you put in `.env`. That account is created on
first startup. Regular users register on the site and verify their email.

> Compose bakes the app into an image, so after changing code or frontend files you must rebuild
> with `docker compose up --build`. For day-to-day development use the workflow below instead.

## Local development

Run only PostgreSQL in Docker and the app directly with Maven; frontend edits then need just an
app restart, not an image rebuild.

```bash
docker compose up -d postgres

cd backend
export TERRAVISION_JWT_SECRET="$(openssl rand -base64 32)"
export ADMIN_EMAIL="you@example.com"
export ADMIN_PASSWORD="choose-a-real-password"
mvn spring-boot:run
```

On Windows PowerShell, set the variables with `$env:NAME = "value"` and generate a secret with
`[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))`.

The app uses the `dev` profile by default (PostgreSQL on `localhost:5432`); Compose runs it with
the `prod` profile. Flyway applies the schema on startup.

### Regenerating the model (optional)

Only needed if the model is retrained. Requires Python 3.11 or 3.12 (`torch==2.3.1` is pinned to
match the libtorch version DJL bundles).

```bash
cd model-export
python -m venv venv
venv\Scripts\activate            # macOS/Linux: source venv/bin/activate
pip install -r requirements.txt
python export_model.py
```

The script traces the model to TorchScript, verifies the traced output matches the original
(max absolute difference below 1e-5), and writes `backend/model/terravision-resnet50.pt` and
`backend/model/classes.json`.

## Configuration

Every secret is supplied as an environment variable. Nothing sensitive is committed; `.env` is
git-ignored and `.env.example` is the template.

| Variable | Required | Purpose |
|---|---|---|
| `TERRAVISION_JWT_SECRET` | Yes | Signs login tokens. 32+ random characters. The app refuses to start without it. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | First run | Seeds the single admin account on an empty database. |
| `MAIL_FROM_ADDRESS` | For email | Sender address on verification emails (must be verified in Brevo). |
| `BREVO_SMTP_USERNAME`, `BREVO_SMTP_PASSWORD` | For email | Brevo SMTP login and key. |
| `APP_BASE_URL` | Deployed | Public URL used to build verification links. Default `http://localhost:8080`. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | `prod` profile | PostgreSQL connection. |
| `SPRING_PROFILES_ACTIVE` | Deployed | Use `prod` in containers. |

Model behaviour is configured under `terravision.inference.*` in
[application.yml](backend/src/main/resources/application.yml), including the confidence threshold.
For production deployment (Render / Railway) and the security checklist, see [DEPLOYMENT.md](DEPLOYMENT.md).

## API reference

All endpoints are versioned under `/api/v1`, with full schemas in Swagger UI at
`/swagger-ui.html`. Every endpoint except `health` and `auth/**` requires an
`Authorization: Bearer <jwt>` header; `admin/**` additionally requires the `ADMIN` role.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | none | Create an account (unverified until the emailed link is used) |
| POST | `/api/v1/auth/resend-verification` | none | Re-send the verification email |
| GET | `/api/v1/auth/verify?token=...` | none | Verify the account and redirect to `/email-verified.html` |
| POST | `/api/v1/auth/login` | none | Returns a JWT (403 if the email is not verified) |
| POST | `/api/v1/predict` | JWT | Multipart image upload, returns the top class, confidence and low-confidence flag |
| GET | `/api/v1/history` | JWT | Paginated history for the current user; filters `predictedClass`, `from`, `to`, `lowConfidenceOnly` |
| GET | `/api/v1/stats` | JWT | Aggregate stats for the current user |
| GET | `/api/v1/admin/users` | JWT, ADMIN | All users with prediction counts |
| GET | `/api/v1/admin/predictions` | JWT, ADMIN | All predictions; filters `userId`, `predictedClass`, `from`, `to`, `lowConfidenceOnly` |
| GET | `/api/v1/health` | none | App health (is the model loaded?) |
| GET | `/actuator/health` | none | Infrastructure liveness |
| GET | `/actuator/prometheus` | none | Prometheus metrics |

Authentication failures return `401`; a signed-in user without permission gets `403`.

**Example**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"..."}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" \
     -F "image=@sample.jpg" \
     http://localhost:8080/api/v1/predict
```

```json
{
  "className": "AnnualCrop",
  "confidencePercent": 78.5,
  "description": "Agricultural land used for annual crops like wheat, corn or rice.",
  "lowConfidence": false,
  "warningMessage": null,
  "inferenceTimeMs": 41
}
```

## Model card

| | |
|---|---|
| **Task** | Single-label classification of a satellite tile into 10 land-use classes |
| **Architecture** | ResNet50 (ImageNet-pretrained), final layer replaced with a 10-way classifier, fine-tuned in two phases (head only, then full network) |
| **Dataset** | EuroSAT: 27,000 Sentinel-2 patches, 64×64 px at 10 m/pixel, 10 balanced classes |
| **Test accuracy** | 97.85% (see `TerraVision/notebooks/LULC_Part1_Image_Classification.ipynb` in the original prototype) |
| **Serving** | TorchScript export executed by DJL's PyTorch engine; letterbox resize to 224×224 and ImageNet normalization reimplemented in Java to match training |

**Classes:** Annual Crop, Forest, Herbaceous Vegetation, Highway, Industrial, Pasture,
Permanent Crop, Residential, River, Sea / Lake.

**Known limitation: domain shift.** The model has only seen top-down Sentinel-2 tiles. Ground-level
photos, drone imagery, screenshots or other sensors typically produce a low, spread-out
confidence. Rather than presenting these as confident answers, any prediction below the
confidence threshold (`terravision.inference.confidence-threshold`) is returned with
`lowConfidence: true` and a warning, surfaced identically in the API and the UI. The in-app
[Model Info](backend/src/main/resources/static/model-info.html) page has the full write-up.

## Project structure

```
TerraVision/
├── backend/                       # Spring Boot application (the live system)
│   ├── src/main/java/ai/terravision/
│   │   ├── inference/             # DJL model loading, preprocessing, /predict
│   │   ├── prediction/            # JPA entity, repository, /history
│   │   ├── user/                  # User entity, roles, admin seeding
│   │   ├── auth/                  # JWT, register / verify / login
│   │   ├── admin/                 # /admin/users, /admin/predictions
│   │   ├── mail/                  # Verification email (Brevo SMTP)
│   │   ├── stats/                 # /stats aggregation
│   │   ├── health/                # /health
│   │   ├── security/              # Spring Security config, JWT filter
│   │   └── common/                # Error handling, OpenAPI, shared config
│   ├── src/main/resources/
│   │   ├── static/                # Frontend pages, JS and CSS
│   │   └── db/migration/          # Flyway SQL migrations
│   ├── model/                     # Exported TorchScript model + class list
│   └── Dockerfile
├── model-export/                  # One-time offline export script (Python)
├── docker-compose.yml             # PostgreSQL, app, pgAdmin (local only)
├── ARCHITECTURE.md
└── DEPLOYMENT.md
```

## Testing

```bash
cd backend
mvn test
```

Unit tests cover image preprocessing, and an integration test loads the real model and checks
a prediction end to end against the bundled sample tile.

## Roadmap

Deliberately out of scope for now, not half-implemented:

- **Forgot-password flow.** Would reuse the existing token and email machinery with a reset token
  on `User` plus `forgot-password` and `reset-password` endpoints.
- **Refresh tokens.** JWTs expire after 60 minutes with no renewal path; refresh tokens would extend
  sessions at the cost of a second token type to manage securely.
- **Predictor pooling.** A new DJL `Predictor` is created per request because it is not thread-safe;
  worth pooling only if concurrent load becomes a bottleneck (see the latency notes in ARCHITECTURE.md).

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md): design decisions, including why Spring Boot + DJL instead of a Python backend
- [DEPLOYMENT.md](DEPLOYMENT.md): Render / Railway deployment and the pre-deployment security checklist

## License

Released under the [MIT License](LICENSE).

## Author

Built by **Navin** ([@Navin9-Sh](https://github.com/Navin9-Sh)).

Data and models: [EuroSAT](https://github.com/phelber/EuroSAT) (Helber et al.), Sentinel-2 imagery
(ESA / Copernicus), inference via [Deep Java Library](https://djl.ai).
