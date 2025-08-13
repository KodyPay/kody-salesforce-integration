#!/bin/bash

# Docker run script with environment variable support
# Usage: ./docker-run.sh [environment]
# Examples:
#   ./docker-run.sh sandbox
#   ./docker-run.sh production
#   ENVIRONMENT=sandbox ./docker-run.sh

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

ENVIRONMENT=${1:-${ENVIRONMENT:-sandbox}}

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}🐳 Docker: Starting Kody Payment Integration${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  Environment: ${GREEN}$ENVIRONMENT${NC}"

# Check if environment file exists
ENV_FILE=".env.$ENVIRONMENT"
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}❌ Environment file not found: $ENV_FILE${NC}"
    echo -e "${YELLOW}Available environment files:${NC}"
    ls -1 .env.* 2>/dev/null || echo "  No .env files found"
    echo ""
    echo -e "${YELLOW}To create one, copy from example:${NC}"
    echo "  cp .env.${ENVIRONMENT}.example $ENV_FILE"
    echo "  # Edit $ENV_FILE with your configuration"
    exit 1
fi

echo -e "  Config File: ${GREEN}$ENV_FILE${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Build and run with Docker Compose
export ENVIRONMENT
docker-compose up --build kody-payment-service