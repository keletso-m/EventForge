# EventForge

**Distributed Event Processing System**

EventForge is a small event-driven system built to demonstrate reliable **asynchronous processing, distributed workers, fault tolerance, and horizontal scaling**.

The system accepts events through a REST API, persists them, places them onto a queue, and processes them asynchronously using independent workers.

## Architecture

```text
                         Client
                           │
                           │ HTTP
                           ▼
                  ┌─────────────────┐
                  │  Spring Boot API │
                  └────────┬────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │   Event Store   │
                  │   PostgreSQL    │
                  └────────┬────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │      Queue      │
                  │      SQS        │
                  └────────┬────────┘
                           │
                 ┌─────────┴─────────┐
                 ▼                   ▼
        ┌─────────────────┐ ┌─────────────────┐
        │    Worker #1    │ │    Worker #2    │
        └────────┬────────┘ └────────┬────────┘
                 │                   │
                 └─────────┬─────────┘
                           ▼
                  ┌─────────────────┐
                  │   PostgreSQL    │
                  └─────────────────┘
```

## Core Features

### Event Ingestion

Clients submit events through the REST API.

```http
POST /events
Content-Type: application/json
```

```json
{
  "type": "order.created",
  "payload": {
    "orderId": "1234"
  }
}
```

Each event receives a unique identifier and is persisted before asynchronous processing begins.

### Asynchronous Processing

Events are placed onto a queue and consumed by independent workers.

This separates event ingestion from processing and allows workers to scale independently of the API.

### Idempotency

Workers must safely handle duplicate event deliveries without processing the same event multiple times.

### Retries

Failed events are retried automatically using exponential backoff.

```text
Attempt 1
   │
   └── failure
          │
          ▼
      retry delay
          │
          ▼
Attempt 2
   │
   └── failure
          │
          ▼
      retry delay
          │
          ▼
Attempt 3
   │
   └── failure
          │
          ▼
    DEAD LETTER
```

### Event Status

Events maintain a processing status throughout their lifecycle.

Example:

```text
Event #18291

Type:
order.created

Status:
RETRYING

Attempt:
2 / 3

Last error:
Database timeout

Next retry:
12:43:12
```

Possible states include:

```text
PENDING
PROCESSING
COMPLETED
RETRYING
FAILED
```

Events that exceed the maximum retry count are moved to the dead-letter queue.

## Worker Concurrency

Multiple worker instances can process events concurrently.

For example:

```bash
kubectl scale deployment event-worker --replicas=5
```

This allows EventForge to increase processing capacity by running multiple worker instances.

## Technology Stack

| Technology  | Purpose                            |
| ----------- | ---------------------------------- |
| Java        | Application runtime                |
| Spring Boot | REST API and application framework |
| PostgreSQL  | Event persistence                  |
| Docker      | Containerization                   |
| Kubernetes  | Container orchestration            |
| AWS EKS     | Kubernetes infrastructure          |
| AWS ECR     | Container image registry           |
| AWS SQS     | Message queue                      |
| AWS RDS     | Managed PostgreSQL                 |

## Kubernetes

EventForge is deployed as separate services:

```text
api
worker
postgres
```

The API and worker components are independently deployable and scalable.

Workers can be horizontally scaled based on processing requirements.

## AWS Architecture

The production-style deployment uses:

```text
                    AWS
                     │
          ┌──────────┴──────────┐
          │                     │
        EKS                   ECR
          │                     │
     ┌────┴────┐          Container Images
     │         │
    API      Workers
     │         │
     │         ▼
     │        SQS
     │
     ▼
    RDS
 PostgreSQL
```

## Reliability

EventForge is designed around several distributed-system reliability concerns:

* Durable event storage
* Asynchronous processing
* Idempotent workers
* Retry handling
* Exponential backoff
* Dead-letter handling
* Failure recovery
* Horizontal worker scaling
* Concurrent event processing

## Project Goal

The primary goal of EventForge is to demonstrate practical understanding of **distributed event processing and reliable asynchronous systems**.

Rather than focusing on deployment itself, the project focuses on how software can reliably process work across multiple independent components.

> **DeploymentCtrl:** How do I deploy software?

> **EventForge:** How do distributed systems reliably process work?
