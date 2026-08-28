# 🚀 MedTrack Deployment Guide

This guide covers production deployment strategies for **MedTrack**, from one-command Docker Compose setups to cloud PaaS and enterprise Linux VPS deployments.

---

## 📑 Deployment Options

- [Option 1: One-Click Docker Compose (Recommended for Local / VPS)](#option-1-docker-compose-production)
- [Option 2: Cloud PaaS (Render / Railway / Fly.io / DigitalOcean)](#option-2-cloud-paas-deployment)
- [Option 3: Linux Bare-Metal / Ubuntu VPS (Systemd + Nginx + PostgreSQL)](#option-3-native-linux-vps-systemd--nginx)
- [Database Backups & Maintenance](#database-backups--maintenance)
- [Production SSL / HTTPS with Let's Encrypt](#production-ssl--https-with-lets-encrypt)

---

## Option 1: Docker Compose Production

The repository includes ready-to-run multi-stage Dockerfiles and Docker Compose files for building and orchestrating the entire stack:
* **PostgreSQL 18** database container with persistent data volume.
* **Spring Boot 4.1.1 (Java 25 LTS)** backend container with Flyway migrations.
* **React 19 + Vite 6 + Nginx** frontend container with SPA routing & reverse proxy.

### Quick Start:

1. Clone repository to your server:
   ```bash
   git clone https://github.com/your-org/MedTrack.git
   cd MedTrack
   ```

2. Configure environment variables (optional, defaults provided):
   ```bash
   cp .env.example .env
   ```

3. Build and launch all services in the background:
   ```bash
   docker compose up --build -d
   ```

4. Verify service health:
   ```bash
   docker compose ps
   docker compose logs -f backend
   ```

* **Frontend UI**: `http://<your-server-ip>`
* **Backend API**: `http://<your-server-ip>:8080/api/v1`
* **Swagger UI Docs**: `http://<your-server-ip>/swagger-ui/index.html`

To stop the containers:
```bash
docker compose down
```

---

## Option 2: Cloud PaaS Deployment

### Deploying to Render / Railway / Fly.io

1. **PostgreSQL Database**:
   * Provision a managed PostgreSQL instance (PostgreSQL 16–18 supported).
   * Note the connection credentials: `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`.

2. **Backend Service (Spring Boot Web Service)**:
   * Build Type: `Dockerfile`
   * Dockerfile Path: `./backend/Dockerfile`
   * Context: `./backend`
   * Environment Variables:
     ```env
     SPRING_DATASOURCE_URL=jdbc:postgresql://<db-host>:<db-port>/<db-name>
     SPRING_DATASOURCE_USERNAME=<db-user>
     SPRING_DATASOURCE_PASSWORD=<db-password>
     SPRING_FLYWAY_ENABLED=true
     MEDTRACK_JWT_SECRET=<generate-a-secure-256-bit-hex-secret>
     MEDTRACK_JWT_EXPIRATION_MS=900000
     ```

3. **Frontend Service (Static Site or Web Service)**:
   * Build Command: `npm run build`
   * Publish Directory: `dist`
   * Environment Variables:
     ```env
     VITE_API_BASE_URL=https://<your-backend-url>/api/v1
     ```

---

## Option 3: Native Linux VPS (Systemd + Nginx)

For dedicated Ubuntu 24.04 / Debian servers running directly on the host OS:

### 1. Install Java 25, PostgreSQL 18 & Node.js 22

```bash
sudo apt update && sudo apt install -y curl wget git build-essential nginx

# Install JDK 25
wget https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.deb
sudo dpkg -i jdk-25_linux-x64_bin.deb

# Install PostgreSQL
sudo apt install -y postgresql postgresql-contrib
```

### 2. Configure PostgreSQL

```bash
sudo -u postgres psql -c "CREATE USER medtrack WITH PASSWORD 'StrongSecurePassword123!';"
sudo -u postgres psql -c "CREATE DATABASE medtrack OWNER medtrack;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE medtrack TO medtrack;"
```

### 3. Build & Deploy Backend

```bash
cd /opt/MedTrack/backend
./mvnw clean package -DskipTests
```

Create a systemd service `/etc/systemd/system/medtrack.service`:
```ini
[Unit]
Description=MedTrack Pharmaceutical Backend
After=network.target postgresql.service

[Service]
User=www-data
WorkingDirectory=/opt/MedTrack/backend
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/MedTrack/backend/target/medtrack-backend-0.0.1-SNAPSHOT.jar
Environment="SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/medtrack"
Environment="SPRING_DATASOURCE_USERNAME=medtrack"
Environment="SPRING_DATASOURCE_PASSWORD=StrongSecurePassword123!"
Environment="MEDTRACK_JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Enable and start backend:
```bash
sudo systemctl daemon-reload
sudo systemctl enable medtrack
sudo systemctl start medtrack
```

### 4. Build & Deploy Frontend with Nginx

```bash
cd /opt/MedTrack/frontend
npm install
npm run build
sudo cp -r dist/* /var/www/medtrack/
```

Configure Nginx `/etc/nginx/sites-available/medtrack`:
```nginx
server {
    listen 80;
    server_name yourdomain.com www.yourdomain.com;

    root /var/www/medtrack;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable and reload Nginx:
```bash
sudo ln -s /etc/nginx/sites-available/medtrack /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

---

## Production SSL / HTTPS with Let's Encrypt

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com
```

Certbot will automatically install TLS certificates, configure HTTP-to-HTTPS redirects, and set up auto-renewal.

---

## Database Backups & Maintenance

Automated daily backup cron script:
```bash
#!/bin/bash
BACKUP_DIR="/var/backups/medtrack"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
mkdir -p "$BACKUP_DIR"

PGPASSWORD="StrongSecurePassword123!" pg_dump -U medtrack -h localhost medtrack | gzip > "$BACKUP_DIR/medtrack_db_$TIMESTAMP.sql.gz"

# Retain backups for 30 days
find "$BACKUP_DIR" -type f -name "*.sql.gz" -mtime +30 -delete
```