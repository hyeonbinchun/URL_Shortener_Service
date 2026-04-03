import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL = 'http://172.31.35.239:30000';


export const options = {
    stages: [
        { duration: '4m', target: 7000 },  // ramp up
        { duration: '7m', target: 7000 },  // steady state
        { duration: '1m', target: 0 },    // ramp down
    ],
        thresholds: {
                http_req_duration: ['p(95)<1000', 'p(99)<2000'],
                http_req_failed:   ['rate<0.01'],
        },
};

export default function () {
    // 100% writes — PUT /?short=...&long=...
    const shortCode = `test${Math.floor(Math.random() * 1000000)}`;
    const res = http.put(
        `${BASE_URL}/?short=${shortCode}&long=https://example.com/some/long/url`,
        null,
        {
            timeout: "3s",
            tags: { 
            type: 'write',
            name: 'PUT /write',  // ← this overrides the URL as the metric label
         }}
    );
    check(res, { 'write: status 200': (r) => r.status === 200 });

    sleep(1);
}