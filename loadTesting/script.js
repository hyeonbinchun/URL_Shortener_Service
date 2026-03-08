import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10m', target: 6000 }, // Ramp up to 6000 users over 10 minutes
  ],
};

export default function () {
    http.get('http://35.171.88.228/test');
    sleep(1); // Simulate user think time
}