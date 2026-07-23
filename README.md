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
export TELEGRAM_BOT_TOKEN=xxxx:yyyy
docker compose up --build
```

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

- **railway.uz endpoint contract**: `EticketRailwayClient` paths and
  `dto/EticketDtos.kt` field names are placeholders and need to be verified
  against the real site (see comments in those files).
- **Station directory**: `V2__seed_stations.sql` ships a small example list;
  refresh it from the real station search endpoint before relying on it.
- **Grouping identical monitored searches** across subscriptions (spec
  section 17) is not implemented yet - each subscription is checked
  independently. The `MonitoringSearchKey` shape described in the spec is a
  natural next step once usage warrants the extra complexity.
