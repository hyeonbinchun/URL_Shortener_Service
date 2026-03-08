import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL = 'http://35.171.88.228';

// Pre-inserted short codes you know exist in Cassandra
const KNOWN_SHORT_CODES = ['abc', 'def', 'ghi', 'jkl', 'mno'];

export const options = {
  stages: [
    { duration: '2m', target: 100 },  // ramp up
    { duration: '5m', target: 100 },  // steady state
    { duration: '1m', target: 0 },    // ramp down
  ],
};

export default function () {
  const rand = Math.random();

  if (rand < 0.8) {
    // 80% reads — GET /{shortURL}
    const code = KNOWN_SHORT_CODES[Math.floor(Math.random() * KNOWN_SHORT_CODES.length)];
    const res = http.get(`${BASE_URL}/${code}`, {
      redirects: 0,  // Don't follow the 301 — just measure Spring+Cassandra response time
    });
    check(res, { 'read: status 301': (r) => r.status === 301 });

  } else {
    // 20% writes — PUT /?short=...&long=...
    const shortCode = `test${Math.floor(Math.random() * 1000000)}`;
    const res = http.put(
      `${BASE_URL}/?short=${shortCode}&long=https://example.com/some/long/url`
    );
    check(res, { 'write: status 200': (r) => r.status === 200 });
  }

  sleep(1);
}