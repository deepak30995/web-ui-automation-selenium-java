#!/bin/bash

echo "📊 Selenium Grid Status"
echo "======================="

# Check if containers are running
if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -q "selenium-hub"; then
    echo "✅ Hub Status: Running"

    # Check hub readiness
    if curl -sSL http://localhost:4444/wd/hub/status 2>/dev/null | jq -r '.value.ready' | grep -q "true"; then
        echo "✅ Hub Ready: Yes"
    else
        echo "❌ Hub Ready: No"
    fi

    # Show node count
    NODE_COUNT=$(curl -sSL http://localhost:4444/wd/hub/status 2>/dev/null | jq -r '.value.nodes | length')
    echo "🔢 Active Nodes: $NODE_COUNT"

    # Show running containers
    echo ""
    echo "📋 Running Containers:"
    docker ps --filter "name=selenium" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

    # Show resource usage
    echo ""
    echo "💻 Resource Usage:"
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}" $(docker ps --filter "name=selenium" -q)

else
    echo "❌ Selenium Grid is not running"
    echo "💡 Start it with: ./docker-grid-start.sh"
fi