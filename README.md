# Notification Service

Every time someone sends money, receives a loan, or changes their PIN, they get an SMS. Behind the scenes, there's a service responsible for actually delivering those messages — handling retries when the SMS provider is down, making sure an OTP gets sent before a bulk promotion campaign, and tracking whether each message was actually delivered.

This project is that service. It accepts notification requests over a REST API, queues them by priority, and dispatches them through the appropriate channel (SMS, email, push, or webhook). If delivery fails, it retries with increasing delays. Everything is logged so you can trace exactly what happened to any notification.

## What It Does

- **Multi-channel dispatch** — SMS, email, push notifications, and webhooks, each with its own dispatcher
- **Priority queue** — CRITICAL notifications (like OTPs) jump ahead of NORMAL ones (like promotions)
- **Retry with backoff** — if delivery fails, it waits 1 second, then 2, then 4, up to a configurable maximum
- **Templates** — 8 pre-built templates with `{{variable}}` substitution
- **Bulk send** — send to hundreds of recipients in a single API call
- **Delivery tracking** — every attempt is logged with status, duration, and error details
- **Health monitoring** — tracks delivery rate and flags when it drops below thresholds

## How the Queue Works

Notifications don't get sent immediately. They go into a priority queue, and a pool of worker threads picks them up and dispatches them. This means the API responds instantly, surges don't overwhelm SMS providers, and critical messages always go first.

In production, you'd replace the in-memory queue with Kafka or RabbitMQ. The dispatcher interface stays the same.

| Problem | Solution | Implementation |
|---|---|---|
## Quick Start

```bash
mvn spring-boot:run
# Swagger UI: http://localhost:8282/swagger-ui.html
```

## Try It Out

```bash
# Send an OTP (gets CRITICAL priority — jumps the queue)
curl -X POST http://localhost:8282/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "SMS",
    "recipient": "+254700000001",
    "templateId": "otp",
    "templateParams": {"code": "482917", "expiry": "5"},
    "priority": "CRITICAL",
    "idempotencyKey": "otp-001"
  }'

# Send a bulk promotion
curl -X POST http://localhost:8282/api/notifications/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "SMS",
    "recipients": ["+254700000001", "+254700000002", "+254700000003"],
    "body": "Get 50% off on all transfers this weekend!",
    "batchId": "promo-001"
  }'

# Check delivery health
curl http://localhost:8282/api/notifications/stats
```

## Available Templates

| Template | What it's for | Variables you fill in |
|---|---|---|
| `otp` | Verification codes | `code`, `expiry` |
| `transaction_success` | "You sent KES X" | `txnId`, `amount`, `recipient`, `balance` |
| `transaction_received` | "You received KES X" | `txnId`, `amount`, `sender`, `balance` |
| `welcome` | New user welcome | `name` |
| `low_balance` | Balance warning | `balance` |
| `loan_due` | Loan reminder | `amount`, `dueDate` |
| `promotion` | Marketing blast | `message` |
| `password_reset` | Reset code | `code`, `expiry` |

## API Reference

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/api/notifications` | Send one notification |
| POST | `/api/notifications/bulk` | Send to multiple recipients |
| GET | `/api/notifications/{id}` | Check status by ID |
| GET | `/api/notifications/key/{key}` | Check by idempotency key |
| GET | `/api/notifications/recipient/{phone}` | History for a phone number |
| GET | `/api/notifications/status/{status}` | Filter by delivery status |
| GET | `/api/notifications/stats` | Delivery rate, queue depth, health |
| GET | `/api/notifications/templates` | List all templates |

## Built With

Spring Boot 3.2, Java 17, Spring Data JPA, PostgreSQL (H2 for dev), Docker, GitHub Actions CI.

## Tests

```bash
mvn test   # 11 tests
```

Covers SMS and email queuing, template resolution, unknown template rejection, idempotency deduplication, bulk dispatch, retrieval by ID and key, stats, priority handling, and missing body validation.

## License

MIT
