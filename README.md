# Webhook Delivery Engine

A production-grade async webhook fan-out engine built with Java, Spring Boot, MySQL, Docker, and deployed to AWS EC2.

Services register subscriber URLs for specific event types. When an event is emitted, the engine fans out HTTP POST requests to all registered subscribers in parallel. Failed deliveries are automatically retried with exponential backoff (max 3 attempts). Every delivery attempt is persisted in MySQL for full auditability.

**Live EC2 URL:** `http://13.201.194.84:8080`

---

## Architecture

```
Client (Postman)
      │
      ▼
WebhookController  (REST API)
      │
      ▼
WebhookService
  ├── subscribe()        → saves subscriber to DB
  ├── unsubscribe()      → soft-deletes subscriber
  ├── emitEvent()        → saves event, fans out async per subscriber
  ├── getLogs()          → returns delivery audit trail
  └── listSubscribers()  → returns active subscribers
      │
      ├── @Async fan-out → RestTemplate POST to each callback URL
      │       │
      │       ├── HTTP 2xx → DeliveryAttempt(SUCCESS)
      │       └── Failure  → DeliveryAttempt(RETRYING) → RetryQueueEntry
      │
      └── RetryScheduler (@Scheduled every 10s)
              └── picks due RetryQueueEntry → re-delivers → DeliveryAttempt
```

### Database Schema (4 tables)

| Table | Purpose |
|---|---|
| `subscribers` | Registered callback URLs per event type |
| `events` | Emitted events with payload and status |
| `delivery_attempts` | Per-attempt audit log (attempt number, status, HTTP code) |
| `retry_queue` | Scheduled retries with backoff timestamps |

### Retry / Backoff Logic

| Attempt | Delay |
|---|---|
| 1st try | immediate |
| 1st retry | +30 seconds |
| 2nd retry | +60 seconds |
| 3rd retry | +120 seconds → marked FAILED |

### Event Status Resolution

| Condition | Status |
|---|---|
| All subscribers succeeded | `COMPLETED` |
| Some succeeded, some failed | `PARTIAL` |
| All subscribers failed | `FAILED` |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Persistence | Spring Data JPA + Hibernate |
| Database (dev) | H2 in-memory |
| Database (prod) | MySQL 8 |
| Async delivery | Spring `@Async` + `ThreadPoolTaskExecutor` |
| Retry scheduler | Spring `@Scheduled` (fixed delay 10s) |
| HTTP client | `RestTemplate` |
| Containerisation | Docker + Docker Compose |
| Cloud | AWS EC2 t2.micro (Mumbai) |
| Build tool | Maven |

---

## API Endpoints

### POST `/api/webhook/subscribe`
Register a subscriber URL for an event type.

**Request:**
```json
{
  "eventType": "order.created",
  "callbackUrl": "https://webhook.site/your-uuid",
  "secretKey": "optional-secret"
}
```

**Response `201`:**
```json
{
  "success": true,
  "message": "Subscriber registered successfully",
  "data": {
    "id": 1,
    "eventType": "order.created",
    "callbackUrl": "https://webhook.site/your-uuid",
    "active": true,
    "createdAt": "2026-05-25T07:00:00"
  },
  "timestamp": "2026-05-25T07:00:00"
}
```

---

### DELETE `/api/webhook/unsubscribe/{id}`
Soft-delete a subscriber (sets `is_active = false`).

**Response `200`:**
```json
{
  "success": true,
  "message": "Subscriber deactivated successfully",
  "timestamp": "2026-05-25T07:00:00"
}
```

---

### POST `/api/webhook/emit`
Emit an event and trigger async fan-out to all matching subscribers.

**Request:**
```json
{
  "eventType": "order.created",
  "payload": "{\"orderId\": 42, \"amount\": 999, \"customer\": \"Aadil\"}"
}
```

**Response `200`:**
```json
{
  "success": true,
  "message": "Event emitted successfully. Fan-out dispatched asynchronously.",
  "data": {
    "id": 1,
    "eventType": "order.created",
    "payload": "{\"orderId\": 42, \"amount\": 999, \"customer\": \"Aadil\"}",
    "status": "PENDING",
    "emittedAt": "2026-05-25T07:00:00"
  },
  "timestamp": "2026-05-25T07:00:00"
}
```

---

### GET `/api/webhook/logs/{eventId}`
Get full delivery audit trail for an event.

**Response `200`:**
```json
{
  "success": true,
  "message": "Delivery logs retrieved: 2 attempt(s)",
  "data": [
    {
      "id": 1,
      "eventId": 1,
      "subscriberId": 1,
      "callbackUrl": "https://webhook.site/your-uuid",
      "attemptNumber": 1,
      "status": "SUCCESS",
      "responseCode": 200,
      "attemptedAt": "2026-05-25T07:00:01"
    }
  ],
  "timestamp": "2026-05-25T07:00:02"
}
```

---

### GET `/api/webhook/subscribers`
List all active subscribers.

**Response `200`:**
```json
{
  "success": true,
  "message": "Active subscribers: 1",
  "data": [...],
  "timestamp": "2026-05-25T07:00:00"
}
```

---

## Local Setup

### Prerequisites
- Java 17
- Maven 3.9+
- Docker Desktop

### Run with Docker Compose (recommended)

```bash
# 1. Clone the repo
git clone https://github.com/mohammedaadils/webhook-engine.git
cd webhook-engine

# 2. Create .env from example
cp .env.example .env
# Edit .env and set your DB password

# 3. Start the full stack
docker-compose up --build

# 4. Test
curl http://localhost:8080/api/webhook/subscribers
```

### Run locally with H2 (dev mode, no Docker needed)

```bash
# In IntelliJ — run WebhookengineApplication
# OR from terminal:
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# H2 console available at:
http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:webhookdb
```

---

## AWS EC2 Deployment

The app is deployed on a **t2.micro free tier** instance in **Asia Pacific (Mumbai)**.

### Deploy from scratch

```bash
# 1. Launch Ubuntu 24.04 t2.micro EC2 instance
# 2. Open ports 22 and 8080 in security group

# 3. Connect and install Docker
sudo apt update -y
sudo apt install docker.io docker-compose -y
sudo usermod -aG docker ubuntu
newgrp docker

# 4. Clone and run
git clone https://github.com/mohammedaadils/webhook-engine.git
cd webhook-engine
cat > .env << EOF
DB_NAME=webhookdb
DB_USER=webhookuser
DB_PASSWORD=your-password
EOF
docker-compose up --build -d

# 5. Enable Docker on boot
sudo systemctl enable docker
```

### Live endpoints

```
Base URL: http://13.201.194.84:8080

GET    http://13.201.194.84:8080/api/webhook/subscribers
POST   http://13.201.194.84:8080/api/webhook/subscribe
POST   http://13.201.194.84:8080/api/webhook/emit
GET    http://13.201.194.84:8080/api/webhook/logs/{eventId}
DELETE http://13.201.194.84:8080/api/webhook/unsubscribe/{id}
```

---

## Project Structure

```
com.aadil.webhookengine
├── controller/    — REST endpoints (WebhookController)
├── service/       — Business logic (WebhookService)
├── scheduler/     — Retry job (RetryScheduler)
├── repository/    — JPA repositories (4)
├── model/         — JPA entities (4) + enums (2)
├── dto/           — Request/response DTOs (6)
├── config/        — AsyncConfig, AppConfig
└── exception/     — GlobalExceptionHandler
```

---

## Resume Bullets

- Built an async event fan-out engine delivering HTTP POST webhooks to 10 subscribers with per-attempt tracking across a 4-table MySQL schema
- Implemented exponential backoff retry (3 attempts, 30/60/120s) via Spring Scheduler with full SQL-persisted delivery audit logs
- Containerised the full stack using Docker Compose, orchestrating Spring Boot and MySQL as isolated services with healthcheck-based startup ordering
- Deployed to AWS EC2 (free tier, Mumbai) via Docker, exposing REST endpoints publicly with live URL in README

---

## Skills Demonstrated

`Java` `Spring Boot` `Spring Data JPA` `REST API` `MySQL` `H2` `Docker` `Docker Compose` `AWS EC2` `Spring Scheduler` `Spring Async` `Maven` `Postman`
