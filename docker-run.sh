#!/bin/bash

# Docker run script for Kody-Salesforce Integration
set -e

# Check if config directory exists
if [ ! -d "config" ]; then
    echo "📁 Creating config directory..."
    mkdir -p config
fi

# Check if configuration file exists
if [ ! -f "config/arguments-sandbox.yaml" ]; then
    echo "⚠️  Configuration file not found!"
    echo "📋 Setting up configuration template..."
    
    # Create config directory and copy template
    cp src/main/resources/arguments-sandbox.yaml config/arguments-sandbox.yaml
    
    echo "✅ Configuration template created at: config/arguments-sandbox.yaml"
    echo ""
    echo "🔧 Please edit config/arguments-sandbox.yaml with your credentials:"
    echo "   - KODY_HOSTNAME"
    echo "   - KODY_API_KEY" 
    echo "   - KODY_STORE_ID"
    echo "   - Salesforce USERNAME/PASSWORD"
    echo ""
    echo "Then run this script again."
    exit 1
fi

# Create logs directory
mkdir -p logs

echo "🐳 Starting Kody-Salesforce Integration..."
echo "📊 Configuration: config/arguments-sandbox.yaml"
echo "📝 Logs will be in: logs/"
echo ""

# Start the service
docker-compose up -d

echo "✅ Service started successfully!"
echo ""
echo "🔍 Useful commands:"
echo "  docker-compose logs -f        # View live logs"
echo "  docker-compose ps             # Check service status"
echo "  docker-compose down           # Stop the service"
echo "  docker-compose restart        # Restart the service"