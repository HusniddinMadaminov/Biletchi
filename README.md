# Railway Ticket Monitoring Bot

Telegram bot (Kotlin + Spring Boot) that watches [eticket.railway.uz](https://eticket.railway.uz)
for the nearest date with an available **lower berth** (odd seat number) on a
given route, and then monitors for an even earlier date every 10 minutes.

See the business rules this implementation follows in full detail in the
original specification shared for this project; the summary below only
covers how the code is organized and how to run it.

## Architecture

```
Telegram Update -> TelegramUpdateHandler -> ConversationService / SubscriptionCommandService
                                                  -> TicketSearchService -> RailwayProvider -> EticketRailwayClient -> eticket.railway.uz

TicketMonitoringScheduler -> TicketMonitoringService -> MonitoringLockService / TicketSubscriptionService
                                                       -> RailwayProvider
                                                       -> NotificationService -> Telegram Bot API
```

- `railway/` - everything eticket.railway.uz-specific (HTTP client, DTOs, mapper). This is
  the **only** place that should need to change if the site's API contract changes.
  Endpoint paths and field names in `EticketRailwayClient`/`dto/EticketDtos.kt` are
  best-effort placeholders - verify them against the real site before production use.
- `search/` - the "nearest date with a lower seat" algorithm (`NearestLowerSeatFinder`),
  batched/parallel date scanning, result sorting.
- `subscription/` - persisted watches (`ticket_subscriptions`), status machine.
- `monitoring/` - the 10-minute scheduler, the SELECT-FOR-UPDATE-SKIP-LOCKED-based
  claim (`MonitoringLockService`) so multiple instances never double-process the same
  subscription, and the monitoring algorithm itself (`TicketMonitoringService`).
- `notification/` - builds and sends the "earlier seat found" Telegram message, with
  fingerprint-based de-duplication (`subscription_notifications`).
- `bot/` - Telegram wiring: long-polling registration, the new-search conversation
  wizard, menus/keyboards, message formatting.
- `station/` - station name search backing the "qayerdan/qayerga" wizard steps.

## Running locally

```bash
cp .env.example .env   # then put your real BotFather token into .env
docker compose up --build
```

Docker Compose reads `.env` automatically. `.env` is gitignored - never
commit the real token; if a token ever leaks, revoke it via @BotFather
(`/revoke`) and issue a new one.

This starts PostgreSQL, runs Flyway migrations, and starts the bot on port 8080
(health check at `/actuator/health`).

To run without Docker, start a local PostgreSQL (see `docker-compose.yml` for
the expected `ticketbot`/`ticketbot` credentials/database) and run:

```bash
TELEGRAM_BOT_TOKEN=xxxx:yyyy gradle bootRun
```

## Tests

```bash
gradle test
```

Unit tests focus on the business rules from the spec's test scenarios: the
"stop at the first matching date" search algorithm, the lower/upper seat
parity rule, the monitoring window (`startDate .. currentBestDate-1`), the
"only notify on a strictly earlier date" rule, fingerprint-based dedup, the
COMPLETED/EXPIRED transitions, and that transient railway.uz errors are
retried rather than failing a subscription.

## Known gaps / follow-ups

- **Both search endpoints are verified** against real browser traffic (HAR
  captures, 2026-07-23), matching the site's own call order:
  `POST /api/v3/handbook/trains/list` (per-date train list with free-seat
  summaries; an empty `cars` list marks a sold-out train and skips the
  details call) and `POST /api/v1/handbook/trains` (per-train free seat
  numbers). Captured responses live in `src/test/resources/eticket` and
  are exercised by the parsing tests and `EticketRailwayProviderTest`.
- **Auth**: the captured traffic came from a logged-in session. The site's
  `POST /api/v1/auth/login` requires a reCAPTCHA header, so the bot cannot
  log in unattended. Options, in order: (1) the search endpoints may
  answer anonymously - test at first run; (2) set `RAILWAY_AUTH_TOKEN`
  from a browser session (1-hour expiry); (3) implement a refresh-token
  flow - login returns a 30-day `refreshToken`, but the refresh endpoint's
  contract has not been captured yet.
- **Station directory**: `V2__seed_stations.sql` has verified Express-3
  codes for Tashkent (2900000) and Urgench (2900790); the other codes are
  commonly cited but unverified. `POST /api/v1/handbook/stations/list`
  with `{"name":"<query>"}` is the site's own station search (a name-less
  query returned 204 in the capture).
- **Grouping identical monitored searches** across subscriptions (spec
  section 17) is not implemented yet - each subscription is checked
  independently. The `MonitoringSearchKey` shape described in the spec is a
  natural next step once usage warrants the extra complexity.
