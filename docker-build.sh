#!/bin/bash

# Docker build script for Kody-Salesforce Integration
set -e

echo "🐳 Building Kody-Salesforce Integration Docker image..."

# Build the Docker image
docker build -t kody-salesforce-integration:latest .

echo "✅ Docker image built successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Create environment file: cp .env.sandbox.example .env.sandbox"
echo "2. Edit .env.sandbox with your credentials"
echo "3. Run with: ./docker-run.sh sandbox"
echo ""
echo "🔍 Available commands:"
echo "  ./docker-run.sh sandbox       # Start with environment variables"
echo "  docker-compose logs -f        # View logs"
echo "  docker-compose down           # Stop the service"