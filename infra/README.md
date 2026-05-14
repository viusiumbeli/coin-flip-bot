# Infrastructure

Docker deployment setup for coin-flip-bot.

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | PostgreSQL + Spring Boot app |
| `Dockerfile` | Multi-stage build (JDK 21) |
| `deploy.sh` | Remote deployment script |

## Local Development

```bash
# Start PostgreSQL only
docker compose up -d postgres

# Run app locally
cd .. && ./gradlew bootRun
```

## Full Stack (Docker)

```bash
# Build and start everything
docker compose up -d --build

# View logs
docker compose logs -f

# Stop
docker compose down

# Stop and delete data
docker compose down -v
```

## Remote Deployment

```bash
# Deploy to remote server
./deploy.sh <ip-address> [ssh-user]

# Examples
./deploy.sh 192.168.1.100           # as root
./deploy.sh 192.168.1.100 ubuntu    # as ubuntu user
```

### What deploy.sh does

1. Creates `$HOME/deployment/coin-flip-bot/` on remote
2. Rsyncs project (excludes .git, .gradle, build)
3. Runs `docker compose build --no-cache`
4. Starts containers with `docker compose up -d`

### Prerequisites on remote server

- Docker and Docker Compose installed
- SSH access configured (key-based recommended)
- Ports 5432 (PostgreSQL) and 8080 (app) available

## Services

| Service | Port | Description |
|---------|------|-------------|
| postgres | 5432 | PostgreSQL 18 database |
| app | 8080 | Spring Boot application |

## Environment Variables

The app service overrides these for Docker networking:

| Variable | Value |
|----------|-------|
| `SPRING_R2DBC_URL` | `r2dbc:postgresql://postgres:5432/coinflipbot` |
| `SPRING_FLYWAY_URL` | `jdbc:postgresql://postgres:5432/coinflipbot` |

## Volumes

| Volume | Purpose |
|--------|---------|
| `coinflip-data` | PostgreSQL data persistence |

## Network

All services run on `coinflip-network` bridge network.

## Troubleshooting

### Check container status
```bash
docker compose ps
```

### View app logs
```bash
docker compose logs -f app
```

### Connect to database
```bash
docker compose exec postgres psql -U coinflipuser -d coinflipbot
```

### Rebuild after code changes
```bash
docker compose up -d --build app
```

### Reset database
```bash
docker compose down -v
docker compose up -d
```
