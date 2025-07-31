#!/bin/bash

echo "🔍 Checking Environment Variables"
echo "================================"

# Function to check environment variable
check_env_var() {
    local var_name=$1
    local var_description=$2
    local current_value=$(printenv $var_name)

    if [ -n "$current_value" ]; then
        echo "✅ $var_name: Set (***MASKED***)"
    else
        echo "❌ $var_name: Not set - $var_description"
    fi
}

# Check all required environment variables
check_env_var "BROWSERSTACK_USERNAME" "BrowserStack username"
check_env_var "BROWSERSTACK_ACCESS_KEY" "BrowserStack access key"
check_env_var "LAMBDATEST_USERNAME" "LambdaTest username"
check_env_var "LAMBDATEST_ACCESS_KEY" "LambdaTest access key"
check_env_var "SAUCELABS_USERNAME" "Sauce Labs username"
check_env_var "SAUCELABS_ACCESS_KEY" "Sauce Labs access key"

echo ""
echo "💡 To set missing variables, run: ./setup-environment.sh"