#!/bin/bash
set -e

# Deployment script for coin-flip-bot
# Usage: ./deploy.sh <remote-ip> [ssh-user]

REMOTE_IP="$1"
SSH_USER="${2:-root}"
REMOTE_DIR="\$HOME/deployment/coin-flip-bot"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Validate arguments
if [ -z "$REMOTE_IP" ]; then
    log_error "Usage: $0 <remote-ip> [ssh-user]"
    log_error "Example: $0 192.168.1.100 ubuntu"
    exit 1
fi

REMOTE_HOST="${SSH_USER}@${REMOTE_IP}"

log_info "Deploying coin-flip-bot to ${REMOTE_HOST}"
log_info "Project root: ${PROJECT_ROOT}"
log_info "Remote directory: ${REMOTE_DIR}"

# Step 1: Create remote directory
log_info "Creating remote directory..."
ssh "${REMOTE_HOST}" "mkdir -p ${REMOTE_DIR}"

# Step 2: Sync project files
log_info "Syncing project files..."
rsync -avz --progress \
    --exclude '.git' \
    --exclude '.gradle' \
    --exclude '.idea' \
    --exclude 'build' \
    --exclude '*.log' \
    --exclude '.DS_Store' \
    "${PROJECT_ROOT}/" \
    "${REMOTE_HOST}:${REMOTE_DIR}/"

# Step 3: Build and run on remote server
log_info "Building Docker images on remote server..."
ssh "${REMOTE_HOST}" "cd ${REMOTE_DIR}/infra && docker compose down || true"
ssh "${REMOTE_HOST}" "cd ${REMOTE_DIR}/infra && docker compose build --no-cache"
ssh "${REMOTE_HOST}" "cd ${REMOTE_DIR}/infra && docker compose up -d"

# Step 4: Show status
log_info "Checking deployment status..."
ssh "${REMOTE_HOST}" "cd ${REMOTE_DIR}/infra && docker compose ps"

# Step 5: Cleanup source files (image is built, no longer needed)
log_info "Cleaning up source files..."
ssh "${REMOTE_HOST}" "rm -rf ${REMOTE_DIR}"

log_info "Deployment complete!"
log_info "Application should be available at http://${REMOTE_IP}:8080"
