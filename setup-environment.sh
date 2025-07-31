#!/bin/bash

echo "🔧 Setting up Environment Variables for Selenium Testing"
echo "======================================================"

# Function to set environment variable
set_env_var() {
    local var_name=$1
    local var_description=$2
    local current_value=$(printenv $var_name)

    if [ -n "$current_value" ]; then
        echo "✅ $var_name is already set"
    else
        echo "❌ $var_name is not set"
        echo "   Description: $var_description"
        read -p "   Enter value for $var_name: " var_value

        if [ -n "$var_value" ]; then
            export $var_name="$var_value"
            echo "export $var_name=\"$var_value\"" >> ~/.bash_profile
            echo "export $var_name=\"$var_value\"" >> ~/.zshrc
            echo "✅ $var_name set successfully"
        else
            echo "⚠️  Skipping $var_name"
        fi
    fi
    echo ""
}

# BrowserStack
echo "📱 BrowserStack Configuration:"
set_env_var "BROWSERSTACK_USERNAME" "Your BrowserStack username"
set_env_var "BROWSERSTACK_ACCESS_KEY" "Your BrowserStack access key"

# LambdaTest
echo "🧪 LambdaTest Configuration:"
set_env_var "LAMBDATEST_USERNAME" "Your LambdaTest username"
set_env_var "LAMBDATEST_ACCESS_KEY" "Your LambdaTest access key"

# Sauce Labs
echo "🥫 Sauce Labs Configuration:"
set_env_var "SAUCELABS_USERNAME" "Your Sauce Labs username"
set_env_var "SAUCELABS_ACCESS_KEY" "Your Sauce Labs access key"

# Optional: Build number for CI/CD
echo "🏗️  Build Configuration:"
set_env_var "BUILD_NUMBER" "Build number for reports (optional)"

echo "🔄 Please restart your terminal or run:"
echo "   source ~/.bash_profile  # for bash"
echo "   source ~/.zshrc         # for zsh"
echo ""
echo "✅ Environment setup complete!"