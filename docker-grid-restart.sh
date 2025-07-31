#!/bin/bash

echo "🔄 Restarting Selenium Grid..."

# Stop the grid
./docker-grid-stop.sh

# Wait a moment
sleep 3

# Start the grid
./docker-grid-start.sh