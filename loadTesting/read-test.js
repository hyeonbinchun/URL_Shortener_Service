import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL = 'http://35.171.88.228:30000';

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
    const code = KNOWN_SHORT_CODES[Math.floor(Math.random() * KNOWN_SHORT_CODES.length)];
    const res = http.get(`${BASE_URL}/${code}`, {
        redirects: 0,   // Stop at 301 — don't follow redirect to external site
    });

    check(res, { 'read: status 301': (r) => r.status === 301 });

    sleep(1);
}