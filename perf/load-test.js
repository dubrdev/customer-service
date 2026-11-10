// k6 run -e SCENARIO=load perf/load-test.js         (smoke | load | stress | spike | soak)
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const KC = __ENV.KC_URL || 'http://localhost:8180';
const SCENARIO = __ENV.SCENARIO || 'smoke';

const scenarios = {
  smoke:  { executor: 'constant-vus', vus: 1, duration: '30s' },
  load:   { executor: 'ramping-vus', startVUs: 0, stages: [
            { duration: '2m', target: 50 }, { duration: '5m', target: 50 }, { duration: '1m', target: 0 }] },
  stress: { executor: 'ramping-arrival-rate', startRate: 50, timeUnit: '1s', preAllocatedVUs: 200, maxVUs: 1000, stages: [
            { target: 100, duration: '2m' }, { target: 300, duration: '3m' },
            { target: 600, duration: '3m' }, { target: 0, duration: '1m' }] },
  spike:  { executor: 'ramping-vus', startVUs: 10, stages: [
            { duration: '30s', target: 10 }, { duration: '10s', target: 500 },
            { duration: '2m', target: 500 }, { duration: '10s', target: 10 }, { duration: '1m', target: 10 }] },
  soak:   { executor: 'constant-vus', vus: 40, duration: '2h' },
};

export const options = {
  scenarios: { [SCENARIO]: scenarios[SCENARIO] },
  thresholds: {                                   // the pass/fail criteria (SLOs)
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const res = http.post(`${KC}/realms/customers/protocol/openid-connect/token`, {
    grant_type: 'password', client_id: 'customers-api', username: 'admin', password: 'admin123',
  });
  return { token: res.json('access_token') };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' };
  const dice = Math.random();

  if (dice < 0.70) {                                               // 70% reads
    const r = http.get(`${BASE}/customers?size=20`, { headers });
    check(r, { 'list 200': (x) => x.status === 200 });
  } else if (dice < 0.90) {                                        // 20% search
    const r = http.get(`${BASE}/customers/search?name=load`, { headers });
    check(r, { 'search 200': (x) => x.status === 200 });
  } else {                                                         // 10% writes
    const cpf = String(Math.floor(Math.random() * 9e10) + 1e10);
    const r = http.post(`${BASE}/customers`, JSON.stringify({
      name: `Load ${cpf}`, cpf, email: `load${cpf}@email.com` }), { headers });
    check(r, { 'create 201/409': (x) => x.status === 201 || x.status === 409 });
  }
  sleep(Math.random() * 0.5);
}
