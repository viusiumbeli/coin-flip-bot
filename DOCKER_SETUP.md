# PostgreSQL Docker Setup

This guide explains how to run PostgreSQL in Docker for the coin-flip-bot application.

## Prerequisites

- Docker installed and running on your system
- Ports 5432 available on localhost

## Starting PostgreSQL

Run the following command to start a PostgreSQL container:

```bash
docker run -d \
  --name coinflip-postgres \
  -e POSTGRES_DB=coinflipbot \
  -e POSTGRES_USER=coinflipuser \
  -e POSTGRES_PASSWORD=coinflippass \
  -p 5432:5432 \
  -v coinflip-postgres-data:/var/lib/postgresql/data \
  postgres:16-alpine
```

### Command Breakdown

- `--name coinflip-postgres` - Container name for easy reference
- `-e POSTGRES_DB=coinflipbot` - Creates database named "coinflipbot"
- `-e POSTGRES_USER=coinflipuser` - Database username (matches application.yml)
- `-e POSTGRES_PASSWORD=coinflippass` - Database password (matches application.yml)
- `-p 5432:5432` - Maps container port 5432 to localhost:5432
- `-v coinflip-postgres-data:/var/lib/postgresql/data` - Persistent volume for data
- `postgres:16-alpine` - PostgreSQL 16 with lightweight Alpine Linux

## Managing the Container

### Check if container is running
```bash
docker ps | grep coinflip-postgres
```

### View container logs
```bash
docker logs coinflip-postgres
```

### Stop the container
```bash
docker stop coinflip-postgres
```

### Start the container (after stopping)
```bash
docker start coinflip-postgres
```

### Remove the container
```bash
docker stop coinflip-postgres
docker rm coinflip-postgres
```

### Remove the container AND data volume (WARNING: deletes all data)
```bash
docker stop coinflip-postgres
docker rm coinflip-postgres
docker volume rm coinflip-postgres-data
```

## Connecting to PostgreSQL

### Using psql (PostgreSQL command-line client)
```bash
docker exec -it coinflip-postgres psql -U coinflipuser -d coinflipbot
```

### Connection details for external tools
- **Host**: localhost
- **Port**: 5432
- **Database**: coinflipbot
- **Username**: coinflipuser
- **Password**: coinflippass

## Verifying the Setup

After starting the container, verify it's working:

```bash
# Check container status
docker ps | grep coinflip-postgres

# Test connection
docker exec -it coinflip-postgres psql -U coinflipuser -d coinflipbot -c "SELECT version();"
```

## Running the Application

Once PostgreSQL is running, start the application:

```bash
./gradlew bootRun
```

The application will:
1. Connect to PostgreSQL on localhost:5432
2. Run Flyway migrations automatically (creating the candles table)
3. Start fetching and storing historical data with optimized batch inserts

## Performance Notes

- The optimized batch inserts should process 50,000 rows in 3-6 seconds (vs several minutes before)
- Data is persisted in the Docker volume, so it survives container restarts
- To start fresh, remove the volume and recreate the container

## Troubleshooting

### "Connection refused" error
- Ensure Docker container is running: `docker ps | grep coinflip-postgres`
- Check if port 5432 is already in use: `lsof -i :5432`

### "role does not exist" error
- Ensure the container was created with correct environment variables
- Try removing and recreating the container

### Flyway migration errors
- Check logs: `docker logs coinflip-postgres`
- Ensure migrations are in `src/main/resources/db/migration/`
- Verify migration file names follow pattern: `V{version}__{description}.sql`
