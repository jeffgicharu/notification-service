# Notification Service

Every time someone sends money, receives a loan, or changes their PIN, they get an SMS. Behind the scenes, there's a service responsible for actually delivering those messages, handling retries when the SMS provider is down, making sure an OTP gets sent before a bulk promotion campaign, and tracking whether each message was actually delivered.

This project is that service. It accepts notification requests over a REST API, queues them by priority, and dispatches them through the appropriate channel (SMS, email, push, or webhook). If delivery fails, it retries with increasing delays. If a notification gets stuck in processing, a background job detects and recovers it. If a single phone number is getting spammed, the rate limiter blocks it. Everything is logged so you can trace exactly what happened to any notification.

## What It Does

**Sending notifications:**
- Queue SMS, email, push, or webhook notifications via REST API
- Bulk send to multiple recipients in a single request
- 8 built-in templates with variable substitution (OTP, transaction alerts, welcome, promotions, etc.)
- Priority queue so CRITICAL notifications (OTPs, fraud alerts) skip ahead of NORMAL ones (promotions)

**Reliability:**
- Exponential backoff retry: 1s, then 2s, then 4s, up to a configurable maximum
- Every delivery attempt logged with status, duration, and provider response
- Stuck notification recovery: a background job detects notifications in PROCESSING for more than 5 minutes and re-queues them
- Idempotency keys prevent duplicate sends on retried requests

**Control:**
- Cancel queued or retrying notifications before they're sent
- Resend failed notifications manually (resets the retry counter)
- Rate limiting per recipient: max 10 notifications per hour to prevent spam
- Schedule notifications for future delivery and manage the queue

**Monitoring:**
- Per-channel health monitoring: tracks delivery rate for SMS, email, push, and webhook separately
- Health statuses: HEALTHY (95%+), DEGRADED (80-95%), CRITICAL (below 80%)
- Platform-wide stats: total sent, delivery rate, queue depth, 24-hour trends
- Delivery logs queryable per notification showing every attempt

## Quick Start

```bash
mvn spring-boot:run
# Swagger UI: http://localhost:8282/swagger-ui.html
```

## Try It Out

```bash
# Send an OTP (CRITICAL priority, skips the queue)
curl -X POST http://localhost:8282/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"SMS","recipient":"+254700000001","templateId":"otp","templateParams":{"code":"482917","expiry":"5"},"priority":"CRITICAL","idempotencyKey":"otp-001"}'

# Send a bulk promotion
curl -X POST http://localhost:8282/api/notifications/bulk \
  -H "Content-Type: application/json" \
  -d '{"channel":"SMS","recipients":["+254700000001","+254700000002"],"body":"Weekend offer!","batchId":"promo-001"}'

# Check channel health
curl http://localhost:8282/api/notifications/channels/health

# View delivery attempts for a notification
curl http://localhost:8282/api/notifications/1/delivery-logs

# Cancel a scheduled notification
curl -X POST http://localhost:8282/api/notifications/5/cancel

# Resend a failed notification
curl -X POST http://localhost:8282/api/notifications/3/resend
```

## Available Templates

| Template | What it's for | Variables |
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

### Sending

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/api/notifications` | Send one notification |
| POST | `/api/notifications/bulk` | Send to multiple recipients |

### Querying

| Method | Endpoint | What it does |
|---|---|---|
| GET | `/api/notifications/{id}` | Get by ID |
| GET | `/api/notifications/key/{key}` | Get by idempotency key |
| GET | `/api/notifications/recipient/{phone}` | History for a phone number |
| GET | `/api/notifications/status/{status}` | Filter by delivery status |
| GET | `/api/notifications/{id}/delivery-logs` | Every delivery attempt for a notification |

### Management

| Method | Endpoint | What it does |
|---|---|---|
| POST | `/api/notifications/{id}/cancel` | Cancel a queued notification |
| POST | `/api/notifications/{id}/resend` | Resend a failed notification |
| GET | `/api/notifications/scheduled` | List pending scheduled notifications |

### Monitoring

| Method | Endpoint | What it does |
|---|---|---|
| GET | `/api/notifications/stats` | Delivery rate, queue depth, health status |
| GET | `/api/notifications/channels/health` | Per-channel delivery rates and status |
| GET | `/api/notifications/templates` | List all templates |

## Built With

Spring Boot 3.2, Java 17, Spring Data JPA, PostgreSQL (H2 for dev), Docker, GitHub Actions CI.

## Tests

```bash
mvn test   # 23 tests
```

**Unit tests (11):** SMS/email queuing, template resolution, unknown template rejection, idempotency deduplication, bulk dispatch, retrieval by ID and key, stats, priority handling, missing body validation.

**Integration tests (12):** SMS send via HTTP, email with template resolution, bulk send, idempotency through HTTP, get by ID, stats with health status, template listing, channel health per-channel, cancel queued notification, scheduled listing, unknown template rejection, recipient history.

## License

MIT
