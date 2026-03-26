# Notification Service

A multi-channel notification service built with Spring Boot. Supports SMS, email, push notifications, and webhooks with async processing, priority queuing, exponential backoff retry, delivery tracking, and webhook callbacks.

## Features

- **Multi-Channel Dispatch** - SMS, Email, Push (FCM), Webhook — each channel has its own dispatcher
- **Priority Queue** - CRITICAL > HIGH > NORMAL > LOW, with FIFO ordering within the same priority
- **Async Processing** - Notifications are queued and processed by a configurable pool of worker threads
- **Exponential Backoff Retry** - Failed deliveries are retried with increasing delays (1s → 2s → 4s → ...)
- **Delivery Tracking** - Every attempt is logged with status, duration, and error details
- **Idempotency** - Duplicate requests are safely deduplicated via idempotency keys
- **Template Engine** - Pre-built templates (OTP, transaction alerts, welcome, promotions) with `{{variable}}` substitution
- **Bulk Send** - Send to multiple recipients in a single API call
- **Webhook Callbacks** - Notify callers when delivery succeeds or fails
- **Delivery Stats** - Real-time statistics: delivery rate, counts by status/channel, queue depth
- **Scheduled Delivery** - Queue notifications for future delivery

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Language | Java 17 |
| Database | H2 (dev) / PostgreSQL (prod) |
| ORM | Spring Data JPA |
| Queue | In-memory PriorityBlockingQueue |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven |
| Testing | JUnit 5 + Spring Boot Test |

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Run

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8282`. Swagger UI at [http://localhost:8282/swagger-ui.html](http://localhost:8282/swagger-ui.html).

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/notifications` | Send a single notification |
| POST | `/api/notifications/bulk` | Send to multiple recipients |
| GET | `/api/notifications/{id}` | Get notification by ID |
| GET | `/api/notifications/key/{key}` | Get by idempotency key |
| GET | `/api/notifications/recipient/{recipient}` | History for a recipient |
| GET | `/api/notifications/status/{status}` | Filter by status |
| GET | `/api/notifications/stats` | Delivery statistics |
| GET | `/api/notifications/templates` | List available templates |

## Usage Examples

### Send an SMS

```bash
curl -X POST http://localhost:8282/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "SMS",
    "recipient": "+254700000001",
    "body": "Your account has been credited with KES 5,000",
    "idempotencyKey": "txn-credit-001"
  }'
```

### Send OTP using a template

```bash
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
```

### Send bulk promotional SMS

```bash
curl -X POST http://localhost:8282/api/notifications/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "SMS",
    "recipients": ["+254700000001", "+254700000002", "+254700000003"],
    "templateId": "promotion",
    "templateParams": {"message": "Get 50% off on all transfers this weekend!"},
    "batchId": "promo-weekend-001"
  }'
```

### Check delivery stats

```bash
curl http://localhost:8282/api/notifications/stats
```

Response:
```json
{
  "totalNotifications": 4,
  "last24Hours": 4,
  "deliveredLast24Hours": 4,
  "deliveryRateLast24Hours": 100.0,
  "byStatus": {"DELIVERED": 4},
  "byChannel": {"SMS": 4},
  "queueSize": 0
}
```

## Built-in Templates

| Template ID | Use Case | Variables |
|---|---|---|
| `transaction_success` | Money sent confirmation | `txnId`, `amount`, `recipient`, `balance` |
| `transaction_received` | Money received alert | `txnId`, `amount`, `sender`, `balance` |
| `otp` | Verification codes | `code`, `expiry` |
| `welcome` | New user registration | `name` |
| `low_balance` | Balance alert | `balance` |
| `loan_due` | Loan reminder | `amount`, `dueDate` |
| `promotion` | Marketing messages | `message` |
| `password_reset` | Password reset | `code`, `expiry` |

## Architecture

```
API Request
    │
    ▼
NotificationController
    │
    ▼
NotificationService ──► TemplateEngine (resolve templates)
    │
    ▼
NotificationQueue (PriorityBlockingQueue)
    │
    ▼
NotificationWorker (thread pool)
    │
    ├──► SmsDispatcher      (SMPP / Africa's Talking)
    ├──► EmailDispatcher     (SMTP / SendGrid)
    ├──► PushDispatcher      (FCM / APNs)
    └──► WebhookDispatcher   (HTTP POST)
    │
    ▼
DeliveryLog (audit trail)
    │
    ▼
CallbackService (webhook to caller)
```

### Retry Flow

```
Attempt 1 fails → wait 1s → Attempt 2 fails → wait 2s → Attempt 3 fails → FAILED
                   ↑                              ↑
            exponential backoff           backoff * multiplier
```

### Key Design Decisions

- **Priority Queue** - Critical notifications (OTPs, fraud alerts) skip ahead of bulk promotions
- **Idempotency** - Safe for retry from the caller side — duplicate keys return the original response
- **Channel Abstraction** - Adding a new channel (e.g., WhatsApp) only requires implementing `ChannelDispatcher`
- **Delivery Logs** - Every attempt is recorded separately, so you can see the full retry history
- **Callbacks** - Callers can provide a `callbackUrl` to receive delivery status webhooks asynchronously

## Configuration

| Property | Default | Description |
|---|---|---|
| `notification.queue.capacity` | 10000 | Max queue size |
| `notification.queue.worker-threads` | 4 | Concurrent dispatch threads |
| `notification.retry.max-attempts` | 3 | Max delivery attempts |
| `notification.retry.initial-delay-ms` | 1000 | First retry delay |
| `notification.retry.backoff-multiplier` | 2.0 | Backoff multiplier |
| `notification.retry.max-delay-ms` | 30000 | Max retry delay |
| `notification.webhook.enabled` | true | Enable delivery callbacks |
| `notification.webhook.timeout-ms` | 5000 | Callback HTTP timeout |

## Running Tests

```bash
mvn test
```

11 tests covering:
- SMS and email notification queuing
- Template resolution and variable substitution
- Unknown template rejection
- Idempotency key deduplication
- Bulk notification dispatch
- Retrieval by ID and idempotency key
- Statistics aggregation
- Priority handling
- Missing body validation

## License

MIT
