# RailScale — REST API Specification
## Version 1.0 (OpenAPI / REST Guidelines)

All APIs use standard HTTP status codes, JSON request/response envelopes, ISO-8601 timestamps (`yyyy-MM-dd'T'HH:mm:ss.SSSX`), and UUID primary identifiers.

---

## 1. Authentication & User Management

### 1.1 Register New User
- **Method:** `POST`
- **Path:** `/api/v1/auth/register`
- **Request Body:**
```json
{
  "email": "passenger@example.com",
  "password": "SecurePassword123!",
  "fullName": "Jane Doe",
  "phoneNumber": "+919876543210"
}
```
- **Responses:**
  - `201 Created`: User created. Returns user profile without password.
  - `400 Bad Request`: Validation failure (invalid email, weak password).
  - `409 Conflict`: Email already registered.

### 1.2 User Login
- **Method:** `POST`
- **Path:** `/api/v1/auth/login`
- **Request Body:**
```json
{
  "email": "passenger@example.com",
  "password": "SecurePassword123!"
}
```
- **Responses:**
  - `200 OK`: Returns JWT Bearer token and user summary.
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "c2a3325e-9278-4a34-9c8b-21f07ced891f",
  "email": "passenger@example.com",
  "role": "ROLE_USER"
}
```
  - `401 Unauthorized`: Invalid credentials.

---

## 2. Catalog & Journey Discovery

### 2.1 Search Journeys
- **Method:** `GET`
- **Path:** `/api/v1/search`
- **Query Parameters:**
  - `source`: Station code (e.g. `DEL`)
  - `destination`: Station code (e.g. `BOM`)
  - `date`: Travel date (e.g. `2026-12-01`)
- **Responses:**
  - `200 OK`: Array of available journeys matching criteria, train details, and seat availability summaries.

### 2.2 Get Live Seat Availability for Journey
- **Method:** `GET`
- **Path:** `/api/v1/journeys/{journeyId}/seats`
- **Headers:** `Authorization: Bearer <token>`
- **Responses:**
  - `200 OK`:
```json
[
  {
    "inventoryId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "seatNumber": "1A",
    "coachNumber": "B1",
    "coachType": "AC_TIER_3",
    "berthType": "LOWER",
    "status": "AVAILABLE",
    "price": 1250.00
  }
]
```

---

## 3. Booking & Reservation Engine

### 3.1 Hold Seats (Seat Hold Phase)
- **Method:** `POST`
- **Path:** `/api/v1/bookings/hold`
- **Headers:**
  - `Authorization: Bearer <token>`
  - `Idempotency-Key: <unique-uuid-or-string>` (Mandatory for idempotency)
- **Request Body:**
```json
{
  "journeyId": "4a180f68-7c85-48b2-bdfa-bbf6e5349f2b",
  "seatIds": [
    "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "a2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e"
  ],
  "userId": "c2a3325e-9278-4a34-9c8b-21f07ced891f"
}
```
- **Responses:**
  - `201 Created`: Seats successfully held for 10 minutes.
```json
{
  "id": "7f8e9d0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a",
  "userId": "c2a3325e-9278-4a34-9c8b-21f07ced891f",
  "journeyId": "4a180f68-7c85-48b2-bdfa-bbf6e5349f2b",
  "status": "HELD",
  "totalAmount": 2500.00,
  "heldUntil": "2026-12-01T10:10:00.000Z",
  "items": [
    {
      "inventoryId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "price": 1250.00
    }
  ]
}
```
  - `409 Conflict`: Seats already held or booked (`SEAT_UNAVAILABLE`).
  - `404 Not Found`: Journey or seat inventory ID not found.

### 3.2 Confirm Booking (Initiate Payment)
- **Method:** `POST`
- **Path:** `/api/v1/bookings/{bookingId}/confirm`
- **Headers:** `Authorization: Bearer <token>`
- **Responses:**
  - `200 OK`: Hold validated, lease extended, moved to `PAYMENT_PENDING`.
```json
{
  "id": "7f8e9d0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a",
  "status": "PAYMENT_PENDING",
  "totalAmount": 2500.00,
  "paymentDeadline": "2026-12-01T10:12:00.000Z"
}
```
  - `400 Bad Request`: Booking does not belong to user.
  - `409 Conflict`: Hold expired or invalid state transition.

### 3.3 Get Booking by ID
- **Method:** `GET`
- **Path:** `/api/v1/bookings/{bookingId}`
- **Headers:** `Authorization: Bearer <token>`
- **Responses:**
  - `200 OK`: Booking representation.
  - `404 Not Found`: Booking not found.

---

## 4. Payment Gateway Webhook & Simulation

### 4.1 Simulate Payment Processing
- **Method:** `POST`
- **Path:** `/api/v1/payments/{bookingId}/simulate`
- **Request Body:**
```json
{
  "status": "SUCCESS",
  "transactionReference": "TXN-99882312"
}
```
- **Responses:**
  - `200 OK`: Payment recorded. Emits `PaymentCompleted` or `PaymentFailed` event to Kafka.
  - `404 Not Found`: Booking not found.

---

## 5. Actuator & Observability Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/actuator/health` | `GET` | Health indicator (Postgres, Redis, Disk space) |
| `/actuator/prometheus` | `GET` | Scrape target for Prometheus Micrometer metrics |
| `/actuator/info` | `GET` | Application metadata and build version |
| `/actuator/metrics` | `GET` | Available Micrometer metric keys |

### Standard HTTP Error Envelope
When an error occurs, the API returns a structured JSON payload:
```json
{
  "timestamp": "2026-12-01T10:00:05.123Z",
  "status": 409,
  "error": "Conflict",
  "message": "SEAT_UNAVAILABLE: Seat 1A is not available (Current status: HELD)",
  "path": "/api/v1/bookings/hold"
}
```
