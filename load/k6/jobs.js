import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    submit_and_claim: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<500"],
  },
};

const baseUrl = __ENV.FORGEFLOW_URL || "http://localhost:8080";

export default function () {
  const uniqueKey = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const createResponse = http.post(
    `${baseUrl}/api/jobs`,
    JSON.stringify({
      taskType: "batch-inference",
      maxAttempts: 3,
      idempotencyKey: uniqueKey,
    }),
    { headers: { "Content-Type": "application/json" } },
  );

  check(createResponse, {
    "job created": (response) => response.status === 201,
  });

  const claimResponse = http.post(
    `${baseUrl}/api/jobs/claim`,
    JSON.stringify({
      workerId: `k6-worker-${__VU}`,
      leaseSeconds: 30,
    }),
    { headers: { "Content-Type": "application/json" } },
  );

  check(claimResponse, {
    "claim returned job or no content": (response) => response.status === 200 || response.status === 204,
  });

  sleep(1);
}
