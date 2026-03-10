import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL = 'http://172.31.35.239:30000';

export const options = {
  stages: [
    { duration: '2m', target: 1000 },  // ramp up
    { duration: '5m', target: 1000 },  // steady state
    { duration: '1m', target: 0 },    // ramp down
  ],
};

export default function () {
  const rand = Math.random();
  if (rand < 0.9) {
    // 90% reads — GET /{shortURL}
    const code = Math.floor(Math.random() * 10000) + 1;
   const res = http.get(`${BASE_URL}/short${code}`, {
      redirects: 0,  // Don't follow the 301 — just measure Spring+Cassandra response time
      tags: { type: 'read', name: 'GET /redirect' },
    });
    check(res, { 'read: status 301': (r) => r.status === 301 });

  } else {
    // 10% writes — PUT /?short=...&long=...
    const shortCode = `test${Math.floor(Math.random() * 1000000)}`;
    const res = http.put(
      `${BASE_URL}/?short=${shortCode}&long=https://example.com/some/long/url`,
      null,
      { tags: { type: 'write', name: 'PUT /write' } }
    );
    check(res, { 'write: status 200': (r) => r.status === 200 });
  }
  
  sleep(1);
}