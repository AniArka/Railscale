import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom benchmark metrics
const requestsTotal = new Counter('benchmark_requests_total');
const successTotal = new Counter('benchmark_success_total');
const contentionTotal = new Counter('benchmark_contention_total');
const serverErrorTotal = new Counter('benchmark_server_error_total');
const requestDuration = new Trend('benchmark_duration_ms');

// Target stage selector via ENV (100, 1000, 10000, 50000)
const TARGET_VUS = parseInt(__ENV.VUS || '100', 10);
const DURATION = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    progressive_benchmark: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: Math.min(TARGET_VUS, 50) },           // Initial ramp
        { duration: '20s', target: TARGET_VUS },                         // Target plateau
        { duration: DURATION, target: TARGET_VUS },                      // Benchmark steady-state
        { duration: '10s', target: 0 },                                  // Ramp down
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // Latency SLOs: p95 under 300ms, p99 under 800ms
    http_req_duration: ['p(95)<300', 'p(99)<800'],
    // Invariant: 0 unhandled server errors (5xx < 0.1%)
    http_req_failed: ['rate<0.001'],
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const JOURNEY_ID = __ENV.JOURNEY_ID || 'a0000000-0000-0000-0000-000000000001';

export default function () {
  const userId = `u0000000-0000-0000-0000-${String(__VU).padStart(12, '0')}`;
  const seatIndex = ((__VU + __ITER) % 10) + 1;
  const seatId = `s0000000-0000-0000-0000-${String(seatIndex).padStart(12, '0')}`;
  const idempotencyKey = `prog-${__VU}-${__ITER}-${Date.now()}`;

  const payload = JSON.stringify({
    journeyId: JOURNEY_ID,
    seatIds: [seatId],
    userId: userId,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
      'X-Request-ID': `bench-${__VU}-${__ITER}`,
      'X-User-ID': userId,
    },
  };

  const t0 = new Date();
  const res = http.post(`${BASE_URL}/api/bookings/hold`, payload, params);
  requestDuration.add(new Date() - t0);
  requestsTotal.add(1);

  if (res.status === 201) {
    successTotal.add(1);
    check(res, { 'status is 201': (r) => r.status === 201 });
  } else if (res.status === 409) {
    contentionTotal.add(1);
    check(res, { 'status is 409 (contention)': (r) => r.status === 409 });
  } else {
    serverErrorTotal.add(1);
    check(res, { 'unexpected error status': (r) => r.status < 500 });
  }

  sleep(0.01);
}
