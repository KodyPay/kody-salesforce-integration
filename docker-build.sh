#!/bin/bash

# Docker build script for Kody-Salesforce Integration
set -e

echo "🐳 Building Kody-Salesforce Integration Docker image..."

# Build the Docker image
docker build -t kody-salesforce-integration:latest .

echo "✅ Docker image built successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Create your config directory: mkdir -p config"
echo "2. Copy your configuration: cp src/main/resources/arguments-sandbox.yaml config/"
echo "3. Update config/arguments-sandbox.yaml with your credentials"
echo "4. Run with: docker-compose up -d"
echo ""
echo "🔍 Available commands:"
echo "  docker-compose up -d          # Start the service"
echo "  docker-compose logs -f        # View logs"
echo "  docker-compose down           # Stop the service"