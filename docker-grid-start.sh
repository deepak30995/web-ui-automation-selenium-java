#!/bin/bash

echo "🚀 Starting Selenium Grid with Docker on macOS..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi

# Pull latest images
echo "📦 Pulling latest Selenium images..."
docker-compose pull

# Start the grid
echo "🔄 Starting Selenium Grid containers..."
docker-compose up -d

# Wait for hub to be ready
echo "⏳ Waiting for Selenium Hub to be ready..."
sleep 15

# Check if hub is running
echo "🔍 Checking hub status..."
if curl -sSL http://localhost:4444/wd/hub/status | jq -r '.value.ready' | grep -q "true"; then
    echo "✅ Selenium Grid started successfully!"
    echo "🌐 Hub URL: http://localhost:4444"
    echo "🖥️  Grid Console: http://localhost:4444/ui"
    echo "📊 Grid Status: http://localhost:4444/wd/hub/status"

    # Show running containers
    echo "📋 Running containers:"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
else
    echo "❌ Hub is not ready. Check logs with: docker-compose logs"
    exit 1
fi