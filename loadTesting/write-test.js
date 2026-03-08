import http from 'k6/http';
import { sleep, check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('url_shortener_errors');
const redirectLatency = new Trend('url_shortener_latency');

const BASE_URL = 'http://35.171.88.228:30000';

export const options = {
    stages: [
        { duration: '2m', target: 500 },  // ramp up
        { duration: '5m', target: 500 },  // steady state
        { duration: '1m', target: 0 },    // ramp down
    ],
};

export default function () {
    // 100% writes — PUT /?short=...&long=...
    const shortCode = `test${Math.floor(Math.random() * 1000000)}`;
    const res = http.put(
        `${BASE_URL}/?short=${shortCode}&long=https://example.com/some/long/url`
    );
    const success = check(res, { 'write: status 200': (r) => r.status === 200 });

    errorRate.add(!success);
    redirectLatency.add(res.timings.duration);
}