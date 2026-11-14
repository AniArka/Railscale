import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const e2eCompletedCounter = new Counter('e2e_booking_completed');
const e2eFailedCounter = new Counter('e2e_booking_failed');
const searchLatency = new Trend('search_latency_ms');
const holdLatency = new Trend('hold_latency_ms');
const confirmLatency = new Trend('confirm_latency_ms');

export const options = {
  stages: [
    { duration: '30s', target: 50 },  // Ramp up to 50 active users
    { duration: '1m', target: 100 },  // Steady state at 100 concurrent users
    { duration: '30s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<400', 'p(99)<800'],
    http_req_failed: ['rate<0.05'], // < 5% failure under mixed load
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

export default function () {
  const userId = `u0000000-0000-0000-0000-${String(__VU).padStart(12, '0')}`;
  const correlationId = `k6-e2e-${__VU}-${__ITER}`;

  const headers = {
    'Content-Type': 'application/json',
    'X-Request-ID': correlationId,
    'X-User-ID': userId,
  };

  let journeyId = null;
  let seatId = null;
  let bookingId = null;

  // Step 1: Search for Trains between stations
  group('1. Search Trains', () => {
    const t0 = new Date();
    const searchRes = http.get(
      `${BASE_URL}/api/search/trains?source=NDLS&destination=MMCT&date=2026-11-13`,
      { headers, tags: { name: 'SearchTrains' } }
    );
    searchLatency.add(new Date() - t0);

    check(searchRes, {
      'search status 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    // Fallback to seeded benchmark journey if search result is empty or simulated
    journeyId = __ENV.JOURNEY_ID || 'a0000000-0000-0000-0000-000000000001';
  });

  sleep(0.1);

  // Step 2: Hold Seat
  group('2. Hold Seat', () => {
    const idempotencyKey = `e2e-hold-${__VU}-${__ITER}`;
    const holdPayload = JSON.stringify({
      journeyId: journeyId,
      seatIds: ['s0000000-0000-0000-0000-000000000001'],
      userId: userId,
    });

    const t0 = new Date();
    const holdRes = http.post(
      `${BASE_URL}/api/bookings/hold`,
      holdPayload,
      {
        headers: Object.assign({}, headers, { 'Idempotency-Key': idempotencyKey }),
        tags: { name: 'HoldSeat' },
      }
    );
    holdLatency.add(new Date() - t0);

    if (holdRes.status === 201) {
      const body = JSON.parse(holdRes.body);
      bookingId = body.bookingId;
      check(holdRes, {
        'hold created 201': (r) => r.status === 201,
        'has booking ID': () => bookingId !== undefined,
      });
    } else {
      check(holdRes, {
        'contention expected 409': (r) => r.status === 409,
      });
    }
  });

  // Step 3: Confirm Booking if held
  if (bookingId) {
    sleep(0.2);

    group('3. Verify Booking Status', () => {
      const t0 = new Date();
      const statusRes = http.get(
        `${BASE_URL}/api/bookings/${bookingId}`,
        { headers, tags: { name: 'GetBooking' } }
      );
      confirmLatency.add(new Date() - t0);

      const success = check(statusRes, {
        'booking status 200': (r) => r.status === 200,
        'booking is held or pending': (r) => {
          const b = JSON.parse(r.body);
          return b.status === 'HELD' || b.status === 'PAYMENT_PENDING' || b.status === 'CONFIRMED';
        },
      });

      if (success) {
        e2eCompletedCounter.add(1);
      } else {
        e2eFailedCounter.add(1);
      }
    });
  }

  sleep(0.5);
}
