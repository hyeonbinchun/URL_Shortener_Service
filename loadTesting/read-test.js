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
    const code = Math.floor(Math.random() * 10000) + 1;
    const res = http.get(`${BASE_URL}/short${code}`, {
        redirects: 0,   // Stop at 301 — don't follow redirect to external site
        tags: { type: 'read', name: 'GET /redirect' },   // ← queryable in Prometheus as label
    });

    check(res, { 'read: status 301': (r) => r.status === 301 });

    sleep(1);
}