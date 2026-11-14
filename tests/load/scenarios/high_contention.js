import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics for architectural analysis
const holdSuccessCounter = new Counter('seat_hold_success');
const holdContentionCounter = new Counter('seat_hold_contention');
const errorRate = new Rate('error_rate');
const holdLatency = new Trend('hold_latency_ms');

export const options = {
  scenarios: {
    seat_contention_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 500,
      stages: [
        { target: 100, duration: '15s' }, // Warm up to 100 req/s
        { target: 500, duration: '30s' }, // High-concurrency contention spike
        { target: 100, duration: '15s' }, // Cool down
      ],
    },
  },
  thresholds: {
    // 95% of requests should complete within 500ms
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    // System should not crash (5xx error rate < 1%)
    error_rate: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const JOURNEY_ID = __ENV.JOURNEY_ID || 'a0000000-0000-0000-0000-000000000001';

// Seeded physical seats from coach A1
const SEATS = [
  's0000000-0000-0000-0000-000000000001',
  's0000000-0000-0000-0000-000000000002',
  's0000000-0000-0000-0000-000000000003',
  's0000000-0000-0000-0000-000000000004',
  's0000000-0000-0000-0000-000000000005',
];

export default function () {
  const userId = `u0000000-0000-0000-0000-${String(__VU).padStart(12, '0')}`;
  const idempotencyKey = `idemp-${__VU}-${__ITER}-${Date.now()}`;

  // Randomly select 1 or 2 seats from the contested pool
  const chosenSeat = SEATS[Math.floor(Math.random() * SEATS.length)];

  const payload = JSON.stringify({
    journeyId: JOURNEY_ID,
    seatIds: [chosenSeat],
    userId: userId,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
      'X-Request-ID': `k6-req-${__VU}-${__ITER}`,
      'X-User-ID': userId,
    },
    tags: { name: 'HoldSeats' },
  };

  const startTime = new Date();
  const res = http.post(`${BASE_URL}/api/bookings/hold`, payload, params);
  holdLatency.add(new Date() - startTime);

  if (res.status === 201) {
    holdSuccessCounter.add(1);
    errorRate.add(0);
    check(res, {
      'hold succeeded (201)': (r) => r.status === 201,
      'response has bookingId': (r) => JSON.parse(r.body).bookingId !== undefined,
    });
  } else if (res.status === 409) {
    // 409 Conflict is EXPECTED under heavy contention (seat already held)
    holdContentionCounter.add(1);
    errorRate.add(0);
    check(res, {
      'seat contention correctly handled (409)': (r) => r.status === 409,
    });
  } else {
    // Unexpected error (e.g. 500, 502, 504)
    errorRate.add(1);
    check(res, {
      'unexpected error status': (r) => r.status !== 500 && r.status !== 502,
    });
  }

  sleep(0.05); // 50ms user think time
}
