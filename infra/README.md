# Infrastructure

Docker deployment setup for coin-flip-bot.

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | PostgreSQL + Spring Boot app |
| `Dockerfile` | Multi-stage build (JDK 21) |
| `deploy.sh` | Remote deployment script |
| `.env.example` | Template for API credentials |
| `.env` | Your API credentials (not in git) |

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
2. Rsyncs project files (excludes .git, .gradle, build)
3. Copies `.env` file if it exists locally (API credentials)
4. Runs `docker compose build --no-cache`
5. Starts containers with `docker compose up -d`
6. Cleans up source files (keeps `infra/` for runtime)

### API Credentials for Remote Deployment

**Option 1: Local .env (recommended)**
```bash
# Create .env locally first
cp .env.example .env
nano .env  # add your credentials

# Deploy - script will copy .env to remote
./deploy.sh 192.168.1.100
```

**Option 2: Create .env on remote server**
```bash
# Deploy without credentials
./deploy.sh 192.168.1.100

# Then SSH to remote and create .env
ssh root@192.168.1.100
cd ~/deployment/coin-flip-bot/infra
cp .env.example .env
nano .env  # add your credentials

# Restart to pick up credentials
docker compose up -d
```

### Prerequisites on remote server

- Docker and Docker Compose installed
- SSH access configured (key-based recommended)
- Port 8080 available (PostgreSQL is internal only)

## Services

| Service | Port | Description |
|---------|------|-------------|
| postgres | internal | PostgreSQL 18 database (not exposed) |
| app | 8080 | Spring Boot application |

## API Credentials Setup

To enable real trading on ByBit, configure your API credentials:

```bash
# Copy the template
cp .env.example .env

# Edit with your credentials
nano .env
```

Your `.env` file should contain:
```
BYBIT_API_KEY=your_api_key_here
BYBIT_API_SECRET=your_api_secret_here
```

Get your API keys from: https://www.bybit.com/app/user/api-management

**Notes:**
- The `.env` file is gitignored (never committed)
- If credentials are not set, the app runs in simulation mode (no real orders)
- Docker Compose automatically loads `.env` from this directory

## Environment Variables

The app service uses these environment variables:

| Variable | Source | Purpose |
|----------|--------|---------|
| `SPRING_R2DBC_URL` | docker-compose.yml | Database connection |
| `SPRING_FLYWAY_URL` | docker-compose.yml | Flyway migrations |
| `BYBIT_API_KEY` | .env file | ByBit API authentication |
| `BYBIT_API_SECRET` | .env file | ByBit API authentication |

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
