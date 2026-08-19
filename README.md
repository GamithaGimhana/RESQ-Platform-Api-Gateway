# RESQ — Spring Cloud API Gateway & Security Entry Point (`resq-api-gateway`)

## Student & Assessment Details
- **Student Name:** Gamitha
- **Student Number:** HDSE-ITS2130-ECA
- **Slack Handle:** `@gamitha`
- **GCP Project ID:** `resq-enterprise-cloud`
- **Course:** ITS 2130 — Enterprise Cloud Architecture

---

## 1. Project Description
`resq-api-gateway` is the single public-facing entry point for all backend traffic. Deployed behind the GCP External Application Load Balancer, it routes incoming traffic to the appropriate internal microservices via Netflix Eureka discovery, enforces HMAC-SHA256 JWT authentication, performs Role-Based Access Control (RBAC), injects distributed tracing correlation IDs, handles CORS, and intercepts errors to return standardized API error payloads.

---

## 2. Technology Stack
- **Runtime:** Java 17 / 25
- **Framework:** Spring Boot 3.3.5 (Spring WebFlux Reactive), Spring Cloud Gateway 2023.0.3
- **Security:** JJWT (Java JWT) with HMAC-SHA256
- **Service Discovery:** Netflix Eureka Client
- **Process Management:** PM2 on GCP Compute Engine VM
- **Monitoring:** Spring Boot Actuator (`/actuator/health`, `/actuator/info`)

---

## 3. Dynamic Routing Table
| Inbound Path | Destination Service | Required Roles | Description |
|---|---|---|---|
| `/api/v1/auth/**` | Local Gateway Controller | Public | Token generation & verification |
| `/api/v1/incidents/**` | `lb://incident-service` | `ADMIN`, `DISPATCHER`, `RESPONDER`, `REPORTER` | Incident reporting & lifecycle |
| `/api/v1/response/**` | `lb://response-service` | `ADMIN`, `DISPATCHER`, `RESPONDER` | Rescue team & resource allocation |
| `/api/v1/evidence/**` | `lb://evidence-service` | `ADMIN`, `DISPATCHER`, `RESPONDER`, `REPORTER` | GCS multipart upload & auditing |
| `/actuator/health` | Local Gateway | Public / GCP Health Check | Used by GCP Load Balancer Probes |

---

## 4. Setup & Getting Started

### Local Development
```bash
# Compile and run
mvn clean spring-boot:run

# Or run JAR
mvn clean package -DskipTests
java -jar target/resq-api-gateway-1.0.0.jar
```

### Health Check
- **Endpoint:** `http://localhost:8080/actuator/health`
- **Demo Tokens:** `http://localhost:8080/api/v1/auth/demo-tokens`

---

## 5. Production Deployment on GCP Compute Engine (PM2)
```bash
# Start Gateway process
pm2 start target/resq-api-gateway-1.0.0.jar --name "resq-api-gateway"

# Ensure persistence across VM restarts
pm2 save
pm2 startup systemd
```
