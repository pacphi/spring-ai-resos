# Deployment

This document covers local development setup, Docker deployment, and production considerations.

## Local Development Setup

### Prerequisites

**Required**:

- JDK 25 or later
- Maven 3.9.11 or later
- Git 2.43.0 or later

**Optional**:

- Docker 24.0+ (for PostgreSQL)
- PostgreSQL 16 (if not using Docker)
- Node.js 23.4.0 (for frontend development)

### Quick Start

**1. Clone Repository**:

```bash
git clone https://github.com/pacphi/spring-ai-resos
cd spring-ai-resos
```

**2. Build All Modules**:

```bash
mvn clean install
```

**3. Set API Keys**:

Create `mcp-client/config/creds.yml` (gitignored):

```yaml
spring:
  ai:
    openai:
      api-key: sk-...YOUR_OPENAI_KEY...
```

**4. Run Services** (3 terminals):

**Terminal 1 - Backend**:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Runs on http://localhost:8080
```

**Terminal 2 - MCP Server**:

```bash
cd mcp-server
export RESOS_API_ENDPOINT=http://localhost:8080/api/v1/resos
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Runs on http://localhost:8082
```

**Terminal 3 - MCP Client (Chatbot)**:

```bash
cd mcp-client
mvn spring-boot:run -Dspring-boot.run.profiles=openai,dev
# Runs on http://localhost:8081
```

**5. Access Application**:

- Chatbot UI: http://localhost:8081
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:resos-backend`)

**6. Login**:

- Username: `admin`
- Password: `admin123`

---

## Port Mapping

| Service    | Port | Purpose                 | Health Check                            |
| ---------- | ---- | ----------------------- | --------------------------------------- |
| Backend    | 8080 | OAuth2 Auth + ResOs API | `http://localhost:8080/actuator/health` |
| MCP Client | 8081 | React SPA + Chat API    | `http://localhost:8081/api/auth/status` |
| MCP Server | 8082 | MCP Tool Provider       | `http://localhost:8082/actuator/health` |
| PostgreSQL | 5432 | Database                | `psql -h localhost -U resos -d resos`   |
| H2 Console | 8080 | Dev database UI         | `http://localhost:8080/h2-console`      |
| Vite Dev   | 5173 | Frontend dev server     | `http://localhost:5173`                 |

---

## Docker Deployment

### Docker Compose - Full Stack

**File**: `docker/docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16
    container_name: resos-postgres
    environment:
      POSTGRES_DB: resos
      POSTGRES_USER: resos
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-resos_password}
    ports:
      - '5432:5432'
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ['CMD-SHELL', 'pg_isready -U resos']
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - resos-network

  backend:
    # Image built by Jib during mvn package
    image: spring-ai-resos-backend:test
    container_name: resos-backend
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=postgres,dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/resos
      - SPRING_DATASOURCE_USERNAME=resos
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD:-resos_password}
      - APP_SECURITY_ISSUER_URI=http://backend:8080
    ports:
      - '8080:8080'
    volumes:
      - liquibase-changelog:/tmp/liquibase
    healthcheck:
      test: ['CMD', 'curl', '-f', 'http://localhost:8080/actuator/health']
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    networks:
      - resos-network

  mcp-server:
    build:
      context: ../mcp-server
      dockerfile: Dockerfile
    image: spring-ai-resos-mcp-server:latest
    container_name: resos-mcp-server
    depends_on:
      backend:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - RESOS_API_ENDPOINT=http://backend:8080/api/v1/resos
      - AUTH_SERVER_URL=http://backend:8080
      - MCP_SERVER_SECRET=${MCP_SERVER_SECRET:-mcp-server-secret}
    ports:
      - '8082:8082'
    healthcheck:
      test: ['CMD', 'curl', '-f', 'http://localhost:8082/actuator/health']
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - resos-network

  mcp-client:
    build:
      context: ../mcp-client
      dockerfile: Dockerfile
    image: spring-ai-resos-mcp-frontend:latest
    container_name: resos-mcp-client
    depends_on:
      backend:
        condition: service_healthy
      mcp-server:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=openai,dev
      - SPRING_AI_OPENAI_API_KEY=${OPENAI_API_KEY}
      - SPRING_AI_MCP_CLIENT_HTTP_CONNECTIONS_BUTLER_URL=http://mcp-server:8082
      - SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_FRONTEND_APP_ISSUER_URI=http://backend:8080
      - MCP_CLIENT_SECRET=${MCP_CLIENT_SECRET:-mcp-client-secret}
    ports:
      - '8081:8081'
    healthcheck:
      test: ['CMD', 'curl', '-f', 'http://localhost:8081/actuator/health']
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - resos-network

volumes:
  postgres-data:
    driver: local
  liquibase-changelog:
    driver: local

networks:
  resos-network:
    driver: bridge
```

### Commands

```bash
# Build and start all services
docker-compose -f docker/docker-compose.yml up --build

# Start in detached mode
docker-compose -f docker/docker-compose.yml up -d

# View logs
docker-compose -f docker/docker-compose.yml logs -f

# View logs for specific service
docker-compose -f docker/docker-compose.yml logs -f backend

# Stop all services
docker-compose -f docker/docker-compose.yml down

# Stop and remove volumes (complete reset)
docker-compose -f docker/docker-compose.yml down -v

# Restart specific service
docker-compose -f docker/docker-compose.yml restart backend
```

### Environment Variables

Create `.env` file in `docker/` directory:

```bash
# LLM API Keys
OPENAI_API_KEY=sk-...
GROQ_API_KEY=gsk_...
OPENROUTER_API_KEY=sk-or-...

# OAuth2 Secrets (change in production!)
MCP_SERVER_SECRET=your-secure-secret-here
MCP_CLIENT_SECRET=another-secure-secret-here

# Database
POSTGRES_PASSWORD=your-database-password

# Optional: Custom configuration
# LOGGING_LEVEL_ROOT=INFO
# LOGGING_LEVEL_ME_PACPHI=DEBUG
```

**Security**: Never commit `.env` file to git!

---

## Docker Images

### Backend Docker Image (Google Jib)

The backend Docker image is built using **Google Jib** - a container image builder for Java applications that doesn't require a Dockerfile. Jib is configured directly in `backend/pom.xml`.

**Benefits of Jib**:

- **No Dockerfile needed** - configuration in pom.xml
- **Faster builds** - intelligent layer caching (dependencies, resources, classes as separate layers)
- **Reproducible** - same contents always produce same image
- **No Docker daemon required** for building (except when using `dockerBuild` goal)

**Jib Configuration** (`backend/pom.xml`):

```xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>3.4.4</version>
    <configuration>
        <from>
            <image>bellsoft/liberica-openjdk-debian:25</image>
        </from>
        <to>
            <image>spring-ai-resos-backend:test</image>
        </to>
        <container>
            <jvmFlags>
                <jvmFlag>-Dspring.profiles.active=dev</jvmFlag>
                <jvmFlag>-Djava.security.egd=file:///dev/urandom</jvmFlag>
            </jvmFlags>
            <environment>
                <CSV_BASE_PATH>/app/seed-data</CSV_BASE_PATH>
            </environment>
            <ports>
                <port>8080</port>
            </ports>
            <workingDirectory>/app</workingDirectory>
        </container>
        <extraDirectories>
            <paths>
                <path>
                    <from>${project.basedir}/seed-data</from>
                    <into>/app/seed-data</into>
                </path>
            </paths>
        </extraDirectories>
    </configuration>
</plugin>
```

**Layer Optimization**:

| Layer        | Contents                      | Rebuild Frequency              |
| ------------ | ----------------------------- | ------------------------------ |
| Dependencies | Third-party JARs              | Rarely (only when deps change) |
| Resources    | application.yml, static files | Sometimes                      |
| Classes      | Your compiled code            | Every code change              |

This means incremental builds are much faster - only the classes layer changes when you modify code!

### Dockerfile - MCP Client (with React)

```dockerfile
FROM bellsoft/liberica-openjdk-debian:25 AS builder
WORKDIR /build

# Install Node.js (for React build)
RUN curl -fsSL https://deb.nodesource.com/setup_23.x | bash - && \
    apt-get install -y nodejs

# Copy and build
COPY . .
RUN ./mvnw package -DskipTests -pl mcp-client -am

FROM bellsoft/liberica-runtime-container:jre-25
WORKDIR /app

COPY --from=builder /build/mcp-client/target/*.jar app.jar

RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Note**: Includes Node.js in builder stage for React build

---

## Production Deployment

### Environment Configuration

**Production Profile** (`application-prod.yml`):

```yaml
spring:
  profiles:
    active: prod

  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  liquibase:
    enabled: true
    contexts: prod # Only run prod changesets

logging:
  level:
    root: WARN
    me.pacphi.ai.resos: INFO
  pattern:
    console: '%d{yyyy-MM-dd HH:mm:ss} - %msg%n'
  file:
    name: /var/log/resos/application.log

app:
  security:
    issuer-uri: ${OAUTH2_ISSUER_URI} # e.g., https://auth.yourdomain.com

  seed:
    csv:
      enabled: false # Disable CSV seeding in production
```

### Connection Pooling (HikariCP)

**Tuning**:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20 # Max connections
      minimum-idle: 5 # Keep warm
      connection-timeout: 30000 # 30s wait for connection
      idle-timeout: 600000 # 10min idle before close
      max-lifetime: 1800000 # 30min max connection age
      leak-detection-threshold: 60000 # 60s leak detection
```

**Formula**: `maximum-pool-size = (core_count * 2) + effective_spindle_count`

- 4-core server with SSD: `(4 * 2) + 1 = 9`
- Use 20 for safety margin

### Reverse Proxy (nginx)

**Configuration**:

```nginx
upstream backend {
    server localhost:8080;
}

upstream frontend {
    server localhost:8081;
}

server {
    listen 80;
    server_name yourdomain.com;

    # Redirect to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name yourdomain.com;

    ssl_certificate /etc/ssl/certs/yourdomain.crt;
    ssl_certificate_key /etc/ssl/private/yourdomain.key;

    # Frontend
    location / {
        proxy_pass http://frontend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # SSE streaming (special config)
    location /api/v1/resos/stream {
        proxy_pass http://frontend;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Host $host;
    }

    # Backend API
    location /api {
        proxy_pass http://backend;
        proxy_set_header Host $host;
    }

    # OAuth2 endpoints
    location /oauth2 {
        proxy_pass http://backend;
    }
}
```

**Key Settings for SSE**:

- `proxy_buffering off` - Stream immediately
- `proxy_cache off` - No caching
- `chunked_transfer_encoding off` - Prevent buffering
- `proxy_http_version 1.1` - HTTP/1.1 for streaming

### Systemd Service

**File**: `/etc/systemd/system/resos-backend.service`

```ini
[Unit]
Description=Spring AI ResOs Backend
After=network.target postgresql.service

[Service]
Type=simple
User=resos
Group=resos
WorkingDirectory=/opt/resos-backend

Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="JAVA_OPTS=-Xmx1g -Xms512m"

ExecStart=/usr/bin/java $JAVA_OPTS \
  -jar /opt/resos-backend/spring-ai-resos-backend.jar

Restart=on-failure
RestartSec=10

StandardOutput=append:/var/log/resos/backend-stdout.log
StandardError=append:/var/log/resos/backend-stderr.log

[Install]
WantedBy=multi-user.target
```

**Commands**:

```bash
# Enable and start
sudo systemctl enable resos-backend
sudo systemctl start resos-backend

# Check status
sudo systemctl status resos-backend

# View logs
sudo journalctl -u resos-backend -f

# Restart
sudo systemctl restart resos-backend
```

---

## Kubernetes Deployment

### Deployment Manifests

**Backend Deployment**:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resos-backend
  namespace: resos
spec:
  replicas: 3
  selector:
    matchLabels:
      app: resos-backend
  template:
    metadata:
      labels:
        app: resos-backend
    spec:
      containers:
        - name: backend
          image: your-registry/spring-ai-resos-backend:1.0.0
          ports:
            - containerPort: 8080
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: 'postgres,prod'
            - name: SPRING_DATASOURCE_URL
              value: 'jdbc:postgresql://postgres-service:5432/resos'
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: username
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: password
          resources:
            requests:
              memory: '512Mi'
              cpu: '500m'
            limits:
              memory: '1Gi'
              cpu: '1000m'
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
          volumeMounts:
            - name: liquibase-temp
              mountPath: /tmp/liquibase
      volumes:
        - name: liquibase-temp
          emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: resos-backend
  namespace: resos
spec:
  selector:
    app: resos-backend
  ports:
    - protocol: TCP
      port: 8080
      targetPort: 8080
  type: ClusterIP
```

### ConfigMap for Configuration

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resos-config
  namespace: resos
data:
  application-prod.yml: |
    spring:
      datasource:
        hikari:
          maximum-pool-size: 20
          minimum-idle: 5
    logging:
      level:
        root: WARN
        me.pacphi.ai.resos: INFO
```

### Secrets Management

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: resos-secrets
  namespace: resos
type: Opaque
stringData:
  openai-api-key: sk-...
  mcp-server-secret: ...
  mcp-client-secret: ...
  postgres-password: ...
```

**Usage in Pod**:

```yaml
env:
  - name: OPENAI_API_KEY
    valueFrom:
      secretKeyRef:
        name: resos-secrets
        key: openai-api-key
```

---

## Production Checklist

### Security

- [ ] Change default OAuth2 secrets
- [ ] Use strong database passwords
- [ ] Enable TLS/HTTPS (Let's Encrypt, AWS ACM)
- [ ] Configure CORS for production domain
- [ ] Set `requireAuthorizationConsent=true` for OAuth2 clients
- [ ] Disable H2 console
- [ ] Disable actuator endpoints or protect with auth
- [ ] Use read-only database user for queries
- [ ] Enable rate limiting
- [ ] Set up WAF (Web Application Firewall)

### Database

- [ ] Use managed database (AWS RDS, Azure Database)
- [ ] Enable automated backups
- [ ] Configure replication (read replicas)
- [ ] Set up monitoring (slow query log)
- [ ] Create indexes for performance
- [ ] Disable SchemaCreator (use pre-generated changelogs)
- [ ] Test Liquibase rollback procedures

### Application

- [ ] Set appropriate JVM memory (`-Xmx`, `-Xms`)
- [ ] Configure logging (external log aggregation)
- [ ] Enable metrics export (Prometheus)
- [ ] Set up health checks
- [ ] Configure connection pooling
- [ ] Disable dev profiles
- [ ] Use production-grade secrets management (Vault, AWS Secrets Manager)

### Monitoring

- [ ] Set up application monitoring (Datadog, New Relic)
- [ ] Configure log aggregation (ELK, Splunk)
- [ ] Set up alerts (error rate, response time, CPU, memory)
- [ ] Enable distributed tracing (OpenTelemetry)
- [ ] Monitor OAuth2 token issuance rate
- [ ] Track LLM API usage and costs

### Deployment

- [ ] Use blue-green deployment strategy
- [ ] Implement circuit breakers (Resilience4j)
- [ ] Configure load balancing
- [ ] Set up CDN for static assets (CloudFront, CloudFlare)
- [ ] Enable auto-scaling
- [ ] Test disaster recovery procedures

---

## Scaling Considerations

### Horizontal Scaling

**Stateless Design**:

- JWT tokens (no server-side sessions)
- OAuth2 tokens in database (shared across instances)
- No in-memory state (except chat memory - see below)

**Load Balancing**:

```text
                 ┌─────────────┐
User ───────────►│ Load        │
                 │ Balancer    │
                 └──────┬──────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
    ┌────────┐     ┌────────┐     ┌────────┐
    │Backend │     │Backend │     │Backend │
    │ Pod 1  │     │ Pod 2  │     │ Pod 3  │
    └────────┘     └────────┘     └────────┘
        │               │               │
        └───────────────┴───────────────┘
                        │
                        ▼
                 ┌─────────────┐
                 │ PostgreSQL  │
                 │ (RDS, etc.) │
                 └─────────────┘
```

### Chat Memory (Stateful Component)

**Problem**: `InMemoryChatMemory` is per-instance

**Solutions**:

**Option 1: Sticky Sessions**:

```yaml
# Kubernetes Service
sessionAffinity: ClientIP
sessionAffinityConfig:
  clientIP:
    timeoutSeconds: 10800 # 3 hours
```

**Option 2: Redis-Backed Memory** (Recommended):

```java
@Bean
public ChatMemory chatMemory(RedisTemplate<String, Object> redisTemplate) {
    return new RedisChatMemory(redisTemplate);
}
```

### Database Connection Pooling

**Per Instance**:

- 20 connections per backend instance
- 3 instances = 60 total connections
- PostgreSQL max_connections: 100-200 (ensure sufficient)

**Autoscaling**:

```yaml
# Kubernetes HPA
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: resos-backend-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: resos-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

## Monitoring & Health Checks

### Actuator Endpoints

**Enabled**:

- `/actuator/health` - Health status
- `/actuator/health/liveness` - Kubernetes liveness probe
- `/actuator/health/readiness` - Kubernetes readiness probe
- `/actuator/info` - Build info, git commit
- `/actuator/metrics` - Micrometer metrics

**Configuration**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### Custom Health Indicators

```java
@Component
public class BackendApiHealthIndicator implements HealthIndicator {

    private final DefaultApi backendApi;

    @Override
    public Health health() {
        try {
            backendApi.healthCheck();  // Custom health endpoint
            return Health.up().build();
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

---

## Backup & Recovery

### Database Backups

**PostgreSQL**:

```bash
# Automated backup (cron daily)
0 2 * * * pg_dump -h localhost -U resos -d resos -F c -f /backups/resos-$(date +\%Y\%m\%d).dump
```

**Retention**: 30 days

### Restore Procedure

```bash
# Stop application
systemctl stop resos-backend

# Restore database
pg_restore -h localhost -U resos -d resos -c /backups/resos-20260106.dump

# Start application
systemctl start resos-backend
```

### Liquibase Rollback

```bash
# Rollback last changeset
mvn liquibase:rollback -Dliquibase.rollbackCount=1

# Rollback to specific tag
mvn liquibase:rollback -Dliquibase.rollbackTag=v1.0.0
```

---

## Critical Files

| File                                              | Purpose                            |
| ------------------------------------------------- | ---------------------------------- |
| `docker/docker-compose.yml`                       | Full stack deployment              |
| `backend/pom.xml` (jib-maven-plugin)              | Backend container image (Jib)      |
| `.env`                                            | Environment variables (not in git) |
| `backend/src/main/resources/application-prod.yml` | Production configuration           |

## Related Documentation

- [Deployment Architecture Diagram](diagrams/deployment-architecture.md) - Visual overview
- [Build Workflow](10-build-workflow.md) - Building artifacts
- [Security Architecture](06-security-architecture.md) - Production security
- [Future Enhancements](15-future-enhancements.md) - Observability recommendations
