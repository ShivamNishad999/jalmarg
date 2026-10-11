# JALMARG PHASE 18 — DEPLOYMENT & OPERATIONS HANDOFF
## Local Development, PostGIS Setup, and Production Readiness

**Project:** JalMarg — Delhi NCR Urban Waterlogging Intelligence  
**Phase:** 18 — Municipal Flood Digital Twin Foundation  
**Audit Date:** 2026-10-10  

---

### 1. Local Development Quickstart

The application preserves zero-dependency local development by default using H2 and local mock telemetry.

#### Prerequisites
- Java 21 (OpenJDK / Amazon Corretto 21)
- Node.js 18+ / 20+ / 24+
- Maven 3.9+

#### Starting the Application
1. **Backend (Spring Boot 3.4.3):**
   ```bash
   cd D:\jalmarg\jalmarg\backend
   mvn spring-boot:run
   ```
   *Health endpoint:* `http://localhost:8080/api/meta`  
   *Digital Twin status:* `http://localhost:8080/api/v1/twin/status`

2. **Frontend (React 18 + Vite):**
   ```bash
   cd D:\jalmarg\jalmarg\frontend
   npm run dev
   ```
   *Web application:* `http://localhost:5173`  
   *Digital Twin Dashboard:* `http://localhost:5173/twin`

---

### 2. PostGIS Production Database Profile

To activate full spatial PostGIS persistence:

1. **Provision PostgreSQL + PostGIS:**
   ```bash
   docker run --name jalmarg-postgis -e POSTGRES_DB=jalmarg -e POSTGRES_USER=jalmarg -e POSTGRES_PASSWORD=jalmarg_secure_pass -p 5432:5432 -d postgis/postgis:16-3.4
   ```

2. **Configure Spring Boot `application-postgis.properties`:**
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/jalmarg
   spring.datasource.username=jalmarg
   spring.datasource.password=jalmarg_secure_pass
   spring.datasource.driver-class-name=org.postgresql.Driver
   spring.jpa.properties.hibernate.dialect=org.hibernate.spatial.dialect.postgis.PostgisPG95Dialect
   spring.flyway.enabled=true
   spring.flyway.locations=classpath:db/migration/postgis
   ```

3. **Run with PostGIS Profile:**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=postgis
   ```

---

### 3. AWS Production Deployment Architecture

When deploying to AWS:
- **Compute:** AWS Elastic Beanstalk (Corretto 21 AL2023) or Amazon ECS Fargate.
- **Database:** Amazon RDS for PostgreSQL with PostGIS extension enabled.
- **Object Storage:** Amazon S3 for citizen photo uploads (`jalmarg-prod-photos-ap-south-1`).
- **Vision AI:** Amazon Bedrock (`apac.amazon.nova-lite-v1:0`) in `ap-south-1` (Mumbai) for image depth estimation.
- **Frontend Hosting:** AWS Amplify Hosting or Amazon S3 + CloudFront CDN.
- **Identity & Access:** AWS Cognito User Pools for Municipal Authority Admin authentication.

---

### 4. Verification Checklist & Regression Safety

- [x] Baseline backend tests passing (59/59)
- [x] Frontend unit tests passing (17/17)
- [x] Frontend production build passing with Vite (`npm run build`)
- [x] Digital twin status endpoint operational (`/api/v1/twin/status`)
- [x] Municipal zones and road assets mapped without physical drainage fabrication
- [x] Data provenance ledger operational with 6 registered source feeds
- [x] Snapshot state isolation enforced (Current vs Historical vs Simulated)
- [x] What-if scenario sandbox deterministic and labeled `SIMULATION`
- [x] Municipal incident lifecycle state machine and audit trail active
- [x] React Digital Twin UI tab integrated at `/twin` with full Hindi / English i18n support
- [x] Existing citizen report creation, route evaluation, weather feed, and authority PDF export unaffected
