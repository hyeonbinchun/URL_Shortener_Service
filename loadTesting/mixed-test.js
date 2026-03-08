import http from 'k6/http';
import { sleep, check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('url_shortener_errors');
const redirectLatency = new Trend('url_shortener_latency');

const BASE_URL = 'http://35.171.88.228:30000';

// Pre-inserted short codes you know exist in Cassandra
const KNOWN_SHORT_CODES = ['abc', 'def', 'ghi', 'jkl', 'mno'];

export const options = {
  stages: [
    { duration: '2m', target: 500 },  // ramp up
    { duration: '5m', target: 500 },  // steady state
    { duration: '1m', target: 0 },    // ramp down
  ],
};

export default function () {
  const rand = Math.random();
  let res;
  let success;

  if (rand < 0.8) {
    // 80% reads — GET /{shortURL}
    const code = KNOWN_SHORT_CODES[Math.floor(Math.random() * KNOWN_SHORT_CODES.length)];
    res = http.get(`${BASE_URL}/${code}`, {
      redirects: 0,  // Don't follow the 301 — just measure Spring+Cassandra response time
    });
    success = check(res, { 'read: status 301': (r) => r.status === 301 });

  } else {
    // 20% writes — PUT /?short=...&long=...
    const shortCode = `test${Math.floor(Math.random() * 1000000)}`;
    res = http.put(
      `${BASE_URL}/?short=${shortCode}&long=https://example.com/some/long/url`
    );
    success = check(res, { 'write: status 200': (r) => r.status === 200 });
  }
  
  errorRate.add(!success);
  redirectLatency.add(res.timings.duration);
}