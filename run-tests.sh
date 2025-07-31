#!/bin/bash

echo "🧪 Selenium Test Runner"
echo "======================"

# Default values
TEST_ENV=${1:-"local"}
BROWSER=${2:-"chrome"}
TEST_CLASS=${3:-"LoginTest"}

echo "🎯 Test Environment: $TEST_ENV"
echo "🌐 Browser: $BROWSER"
echo "📋 Test Class: $TEST_CLASS"
echo ""

# Validate environment
case $TEST_ENV in
    "local")
        echo "✅ Running tests locally"
        ;;
    "docker")
        echo "🐳 Running tests on Docker Grid"
        if ! curl -sSL http://localhost:4444/wd/hub/status 2>/dev/null | grep -q "ready"; then
            echo "❌ Docker Grid is not running"
            echo "💡 Start it with: ./docker-grid-start.sh"
            exit 1
        fi
        ;;
    "browserstack")
        echo "📱 Running tests on BrowserStack"
        if [ -z "$BROWSERSTACK_USERNAME" ] || [ -z "$BROWSERSTACK_ACCESS_KEY" ]; then
            echo "❌ BrowserStack credentials not set"
            echo "💡 Set them with: ./setup-environment.sh"
            exit 1
        fi
        ;;
    "lambdatest")
        echo "🧪 Running tests on LambdaTest"
        if [ -z "$LAMBDATEST_USERNAME" ] || [ -z "$LAMBDATEST_ACCESS_KEY" ]; then
            echo "❌ LambdaTest credentials not set"
            echo "💡 Set them with: ./setup-environment.sh"
            exit 1
        fi
        ;;
    "saucelabs")
        echo "🥫 Running tests on Sauce Labs"
        if [ -z "$SAUCELABS_USERNAME" ] || [ -z "$SAUCELABS_ACCESS_KEY" ]; then
            echo "❌ Sauce Labs credentials not set"
            echo "💡 Set them with: ./setup-environment.sh"
            exit 1
        fi
        ;;
    *)
        echo "❌ Unknown environment: $TEST_ENV"
        echo "💡 Valid environments: local, docker, browserstack, lambdatest, saucelabs"
        exit 1
        ;;
esac

echo "🚀 Starting test execution..."
echo ""

# Run tests
mvn clean test \
    -Dtest.env=$TEST_ENV \
    -Dbrowser=$BROWSER \
    -Dtest=$TEST_CLASS

# Check results
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Tests completed successfully!"

    # Open reports if available
    if [ -d "test-output/extent-reports" ]; then
        REPORT_FILE=$(find test-output/extent-reports -name "*.html" | head -1)
        if [ -n "$REPORT_FILE" ]; then
            echo "📊 Opening test report..."
            open "$REPORT_FILE"
        fi
    fi
else
    echo ""
    echo "❌ Tests failed!"
    echo "📋 Check logs in test-output/logs/"
fi