# Notification Service

A multi-channel notification dispatch system handling SMS, email, push, and webhooks. Processes notifications asynchronously through a priority queue with exponential backoff retry, delivery tracking, and real-time health monitoring — built for the throughput and reliability requirements of a telco platform sending millions of transaction alerts daily.

## Why This Architecture

Notification systems at scale fail when you treat them as simple HTTP calls. Messages get lost in spikes, retries flood downstream providers, and bulk promotions starve time-sensitive OTPs. This architecture addresses each failure mode:

| Problem | Solution | Implementation |
|---|---|---|
| OTP delayed behind 100K promo blast | Priority queue | `PriorityBlockingQueue` — CRITICAL (OTPs) skip ahead of LOW (promotions) |
| SMS provider intermittently fails | Exponential backoff retry | 1s → 2s → 4s with configurable max attempts and delay cap |
| Caller retries and sends duplicate | Idempotency deduplication | Same key returns original response without re-sending |
| Can't tell if notification was delivered | Per-attempt delivery logging | Every attempt recorded with status, duration, and provider response |
| Provider outage goes unnoticed | Health monitoring | Delivery rate tracked with EXCELLENT/GOOD/DEGRADED/CRITICAL thresholds |
| Need to notify caller of delivery | Webhook callbacks | Async POST to caller's URL on delivery success or final failure |

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Queue | In-memory `PriorityBlockingQueue` (Kafka/RabbitMQ in production) |
| Database | PostgreSQL (H2 for dev) |
| Workers | Configurable thread pool (default 4 concurrent dispatchers) |

## Architecture

```
API Request
    │
    ▼
NotificationService ──► TemplateEngine (resolve {{variables}})
    │
    ▼
NotificationQueue (priority: CRITICAL > HIGH > NORMAL > LOW)
    │
    ▼
NotificationWorker (4 threads polling queue)
    │
    ├──► SmsDispatcher       → SMPP / Africa's Talking
    ├──► EmailDispatcher     → SMTP / SendGrid
    ├──► PushDispatcher      → FCM / APNs
    └──► WebhookDispatcher   → HTTP POST
    │
    ▼
DeliveryLog (per-attempt audit)
    │
    ▼
CallbackService (async status webhook to caller)
```

## Built-in Templates

| ID | Use Case | Variables |
|---|---|---|
| `otp` | Verification codes | `code`, `expiry` |
| `transaction_success` | Money sent | `txnId`, `amount`, `recipient`, `balance` |
| `transaction_received` | Money received | `txnId`, `amount`, `sender`, `balance` |
| `welcome` | New registration | `name` |
| `low_balance` | Balance alert | `balance` |
| `loan_due` | Loan reminder | `amount`, `dueDate` |
| `promotion` | Marketing | `message` |
| `password_reset` | Password reset | `code`, `expiry` |

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/notifications` | Send single notification |
| POST | `/api/notifications/bulk` | Send to multiple recipients |
| GET | `/api/notifications/{id}` | Get by ID |
| GET | `/api/notifications/key/{key}` | Get by idempotency key |
| GET | `/api/notifications/recipient/{phone}` | History for recipient |
| GET | `/api/notifications/status/{status}` | Filter by delivery status |
| GET | `/api/notifications/stats` | Delivery rate, health status, queue depth |
| GET | `/api/notifications/templates` | List available templates |

## Usage

```bash
# Send OTP (CRITICAL priority — skips queue)
curl -X POST http://localhost:8282/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"channel":"SMS","recipient":"+254700000001","templateId":"otp","templateParams":{"code":"482917","expiry":"5"},"priority":"CRITICAL","idempotencyKey":"otp-001"}'

# Bulk promotional SMS
curl -X POST http://localhost:8282/api/notifications/bulk \
  -H "Content-Type: application/json" \
  -d '{"channel":"SMS","recipients":["+254700000001","+254700000002"],"body":"Weekend offer!","batchId":"promo-001"}'

# Check delivery health
curl http://localhost:8282/api/notifications/stats
```

## Running

```bash
mvn spring-boot:run   # http://localhost:8282/swagger-ui.html
```

## Testing

```bash
mvn test   # 11 tests
```

Covers: SMS/email queuing, template resolution, unknown template rejection, idempotency deduplication, bulk dispatch, retrieval by ID and key, stats aggregation, priority handling, missing body validation.

## License

MIT
