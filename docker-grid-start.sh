#!/bin/bash

echo "🚀 Starting Selenium Grid with Docker on macOS..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi

# The readiness check below parses JSON; without jq it silently reports "not ready"
if ! command -v jq > /dev/null 2>&1; then
    echo "❌ jq is required for the readiness check. Install it with: brew install jq"
    exit 1
fi

# Pull latest images
echo "📦 Pulling latest Selenium images..."
docker compose pull

# Start the grid
echo "🔄 Starting Selenium Grid containers..."
docker compose up -d

# Poll rather than sleeping a fixed amount: a cold image pull needs far longer
# than 15s, and a warm start is ready in two.
echo "⏳ Waiting for Selenium Hub to be ready..."
READY=false
for i in $(seq 1 60); do
    if curl -sSL http://localhost:4444/wd/hub/status 2>/dev/null | jq -r '.value.ready' 2>/dev/null | grep -q "true"; then
        READY=true
        break
    fi
    sleep 2
done

echo "🔍 Checking hub status..."
if [ "$READY" = true ]; then
    echo "✅ Selenium Grid started successfully!"
    echo "🌐 Hub URL: http://localhost:4444"
    echo "🖥️  Grid Console: http://localhost:4444/ui"
    echo "📊 Grid Status: http://localhost:4444/wd/hub/status"

    # Show running containers
    echo "📋 Running containers:"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
else
    echo "❌ Hub is not ready. Check logs with: docker compose logs"
    exit 1
fi