#!/usr/bin/env python3
"""Small demo worker for exercising Forgeflow's HTTP worker protocol."""

from __future__ import annotations

import argparse
import json
import os
import random
import socket
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class WorkerConfig:
    base_url: str
    worker_id: str
    lease_seconds: int
    wait_seconds: int
    task_seconds: float
    renew_interval_seconds: float
    retry_delay_seconds: int
    failure_rate: float
    max_jobs: int | None


class ForgeflowClient:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")

    def heartbeat(self, worker_id: str, current_job_id: str | None) -> dict[str, Any]:
        return self._request(
            "POST",
            f"/api/workers/{worker_id}/heartbeat",
            {"currentJobId": current_job_id},
        )

    def poll(self, worker_id: str, lease_seconds: int, wait_seconds: int) -> dict[str, Any] | None:
        return self._request(
            "POST",
            f"/api/workers/{worker_id}/poll",
            {"leaseSeconds": lease_seconds, "waitSeconds": wait_seconds},
            allow_no_content=True,
        )

    def renew_lease(
        self,
        worker_id: str,
        job_id: str,
        assignment_version: int,
        lease_seconds: int,
    ) -> dict[str, Any]:
        return self._request(
            "POST",
            f"/api/workers/{worker_id}/jobs/{job_id}/renew-lease",
            {"assignmentVersion": assignment_version, "leaseSeconds": lease_seconds},
        )

    def complete(self, worker_id: str, job_id: str, assignment_version: int) -> dict[str, Any]:
        return self._request(
            "POST",
            f"/api/workers/{worker_id}/jobs/{job_id}/complete",
            {"assignmentVersion": assignment_version},
        )

    def fail(
        self,
        worker_id: str,
        job_id: str,
        assignment_version: int,
        retry_delay_seconds: int,
    ) -> dict[str, Any]:
        return self._request(
            "POST",
            f"/api/workers/{worker_id}/jobs/{job_id}/fail",
            {
                "assignmentVersion": assignment_version,
                "reason": "demo worker injected failure",
                "retryable": True,
                "retryDelaySeconds": retry_delay_seconds,
            },
        )

    def _request(
        self,
        method: str,
        path: str,
        payload: dict[str, Any],
        allow_no_content: bool = False,
    ) -> dict[str, Any] | None:
        body = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=body,
            method=method,
            headers={"Content-Type": "application/json"},
        )

        try:
            with urllib.request.urlopen(request, timeout=35) as response:
                if response.status == 204 and allow_no_content:
                    return None
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8")
            raise RuntimeError(f"{method} {path} failed with {error.code}: {detail}") from error


def renew_until_done(
    client: ForgeflowClient,
    config: WorkerConfig,
    job_id: str,
    assignment_version: int,
    stop: threading.Event,
) -> None:
    while not stop.wait(config.renew_interval_seconds):
        renewed = client.renew_lease(
            config.worker_id,
            job_id,
            assignment_version,
            config.lease_seconds,
        )
        print_event("renewed", renewed)


def execute_job(client: ForgeflowClient, config: WorkerConfig, job: dict[str, Any]) -> None:
    job_id = job["id"]
    assignment_version = int(job["assignmentVersion"])
    print_event("claimed", job)

    stop = threading.Event()
    renewer = threading.Thread(
        target=renew_until_done,
        args=(client, config, job_id, assignment_version, stop),
        daemon=True,
    )
    renewer.start()

    try:
        time.sleep(config.task_seconds)
        if random.random() < config.failure_rate:
            result = client.fail(
                config.worker_id,
                job_id,
                assignment_version,
                config.retry_delay_seconds,
            )
            print_event("failed", result)
        else:
            result = client.complete(config.worker_id, job_id, assignment_version)
            print_event("completed", result)
    finally:
        stop.set()
        renewer.join(timeout=2)


def print_event(event: str, payload: dict[str, Any]) -> None:
    print(json.dumps({"event": event, "payload": payload}, sort_keys=True), flush=True)


def parse_args() -> WorkerConfig:
    parser = argparse.ArgumentParser(description="Run a Forgeflow demo worker.")
    parser.add_argument("--base-url", default=os.getenv("FORGEFLOW_URL", "http://localhost:8080"))
    parser.add_argument("--worker-id", default=f"{socket.gethostname()}-{os.getpid()}")
    parser.add_argument("--lease-seconds", type=int, default=20)
    parser.add_argument("--wait-seconds", type=int, default=5)
    parser.add_argument("--task-seconds", type=float, default=2.0)
    parser.add_argument("--renew-interval-seconds", type=float, default=5.0)
    parser.add_argument("--retry-delay-seconds", type=int, default=5)
    parser.add_argument("--failure-rate", type=float, default=0.0)
    parser.add_argument("--max-jobs", type=int)
    args = parser.parse_args()

    if not 0 <= args.failure_rate <= 1:
        parser.error("--failure-rate must be between 0 and 1")

    return WorkerConfig(
        base_url=args.base_url,
        worker_id=args.worker_id,
        lease_seconds=args.lease_seconds,
        wait_seconds=args.wait_seconds,
        task_seconds=args.task_seconds,
        renew_interval_seconds=args.renew_interval_seconds,
        retry_delay_seconds=args.retry_delay_seconds,
        failure_rate=args.failure_rate,
        max_jobs=args.max_jobs,
    )


def main() -> int:
    config = parse_args()
    client = ForgeflowClient(config.base_url)
    processed = 0

    print_event("worker_started", {"workerId": config.worker_id, "baseUrl": config.base_url})
    while config.max_jobs is None or processed < config.max_jobs:
        client.heartbeat(config.worker_id, None)
        job = client.poll(config.worker_id, config.lease_seconds, config.wait_seconds)
        if job is None:
            print_event("poll_empty", {"workerId": config.worker_id})
            continue

        client.heartbeat(config.worker_id, job["id"])
        execute_job(client, config, job)
        processed += 1

    print_event("worker_stopped", {"workerId": config.worker_id, "processed": processed})
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("interrupted", file=sys.stderr)
        raise SystemExit(130)
