#!/bin/bash

echo "🛑 Stopping Selenium Grid..."

# Stop and remove containers
docker-compose down

# Remove dangling images (optional)
echo "🧹 Cleaning up dangling images..."
docker image prune -f

# Remove unused volumes (optional)
echo "🧹 Cleaning up unused volumes..."
docker volume prune -f

echo "✅ Selenium Grid stopped successfully!"