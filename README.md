
Quick Reference: Running Your Selenium Tests in Docker
# Step 1: Navigate to your project
cd /Users/deepakkumar/Documents/Coding/UIAutomationSelenium

# Step 2: Start Docker Grid
docker-compose up -d

# Step 3: Run your tests (after 60 seconds)
bash run-tests.sh docker chrome LoginTest

===================================================================================================

🔧 If You Get Java Error
If you see "invalid target release: 11", run this first:
# Set Java 11
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Then run tests
bash run-tests.sh docker chrome LoginTest

===================================================================================================

🛑 To Stop Docker Grid
# Stop when done
docker-compose down

===================================================================================================

📊 Check If Docker Grid is Running
# Check status
docker-compose ps

# Check grid health
curl -sSL http://localhost:4444/wd/hub/status

# View grid console
open http://localhost:4444/ui

===================================================================================================

🎯 Different Browser Options
# Chrome (default)
bash run-tests.sh docker chrome LoginTest

# Firefox
bash run-tests.sh docker firefox LoginTest

# Edge
bash run-tests.sh docker edge LoginTest

===================================================================================================

📁 View Your Test Reports
# Open latest report
open test-output/extent-reports/*.html

# Or check this folder
open test-output/extent-reports/

===================================================================================================

💡 Pro Tips

# Grid Console: http://localhost:4444/ui - Watch tests run in real-time
# Wait Time: Always wait 60 seconds after starting Docker Grid
# Java: If tests don't compile, you need to set Java 11 (see above)
# Reports: Found in test-output/extent-reports/

====================================================================================================
====================================================================================================

# UIAutomationSelenium Framework

> A comprehensive Selenium WebDriver automation framework using Java, TestNG, and Page Object Model (POM) design pattern with support for local, Docker, and cloud-based test execution.

## 📑 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Running Tests](#running-tests)
- [Configuration](#configuration)
- [Reports](#reports)
- [Docker Execution](#docker-execution)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)
- [Future Enhancements](#future-enhancements)
- [Contributing](#contributing)

---

## 🎯 Overview

**UIAutomationSelenium** is a production-ready Selenium automation framework designed for testing web-based learning platforms and course management systems. Built with scalability and maintainability in mind, it supports multiple browsers, execution environments, and provides comprehensive test reporting.

### **Current Status**
- ✅ **Production-Ready**: Actively maintained
- ✅ **Framework Type**: Data-driven, Keyword-driven
- ✅ **Test Coverage**: Login module (expandable to other modules)
- ✅ **Execution**: Daily regression testing

---

## ✨ Features

### **Core Capabilities**
- 🎯 **Page Object Model (POM)**: Clean separation of test logic and page elements
- 🌐 **Multi-Browser Support**: Chrome, Firefox, Edge, Safari
- 🐳 **Docker Integration**: Containerized test execution with Selenium Grid
- ☁️ **Cloud Platform Ready**: Pre-configured for BrowserStack, LambdaTest, SauceLabs
- 📊 **Dual Reporting**: ExtentReports (HTML) + Allure (Interactive)
- 📝 **Comprehensive Logging**: Log4j2 with file rotation and archiving
- 🔧 **Multi-Environment Support**: Local, Docker, Cloud platforms
- 🎨 **Clean Architecture**: Base classes, utilities, and modular design

### **Technical Features**
- ✅ Thread-safe driver management
- ✅ Automatic report cleanup (keeps latest 5 reports)
- ✅ Screenshot capture on test failures
- ✅ Configurable timeouts and waits
- ✅ Environment-specific configurations
- ✅ Detailed step-by-step test logging

---

## 🏗️ Architecture

### **Design Patterns**
```
┌─────────────────────────────────────────┐
│         Test Layer (TestNG)             │
│  ┌───────────────────────────────────┐  │
│  │    LoginTest.java                 │  │
│  └───────────────────────────────────┘  │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      Page Object Layer (POM)            │
│  ┌───────────────────────────────────┐  │
│  │    Login.java                     │  │
│  │    (More pages can be added)      │  │
│  └───────────────────────────────────┘  │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│        Base Layer (Reusable)            │
│  ┌──────────────┐  ┌─────────────────┐  │
│  │ BasePage     │  │  BaseTest       │  │
│  └──────────────┘  └─────────────────┘  │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      Utilities Layer (Core)             │
│  ┌──────────────────────────────────┐   │
│  │ UnifiedDriverManager             │   │
│  │ ConfigReader                     │   │
│  │ ExtentReportManager              │   │
│  └──────────────────────────────────┘   │
└──────────────────────────────────────────┘
```

### **Execution Flow**
```
BaseTest (Setup)
    ↓
UnifiedDriverManager (Driver Creation)
    ↓
Page Objects (Element Interactions)
    ↓
Test Methods (Business Logic)
    ↓
BaseTest (Teardown + Report Generation)
```

---

## 📋 Prerequisites

### **Required Software**
- **Java**: JDK 11 or higher
- **Maven**: 3.6+
- **Docker**: Latest version (for containerized execution)
- **IDE**: IntelliJ IDEA / Eclipse (recommended)

### **Required Skills**
- Basic knowledge of Java programming
- Understanding of Selenium WebDriver concepts
- Familiarity with TestNG framework
- Basic command line operations

### **System Requirements**
- **OS**: macOS, Windows, or Linux
- **RAM**: Minimum 8GB (16GB recommended)
- **Disk**: 5GB free space for dependencies and reports

---

## 🚀 Installation

### **1. Clone Repository**
```bash
git clone <repository-url>
cd UIAutomationSelenium
```

### **2. Install Dependencies**
```bash
# Install Maven dependencies
mvn clean install

# Verify installation
mvn -version
java -version
```

### **3. Set Java Environment (macOS/Linux)**
```bash
# Set Java 11
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Make permanent (add to ~/.zshrc or ~/.bash_profile)
echo 'export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home' >> ~/.zshrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.zshrc
source ~/.zshrc
```

### **4. Set Java Environment (Windows)**
```cmd
# Set JAVA_HOME
setx JAVA_HOME "C:\Program Files\Java\jdk-11"
setx PATH "%JAVA_HOME%\bin;%PATH%"
```

### **5. Verify Installation**
```bash
# Check Java version (should show 11.x.x)
java -version

# Check Maven
mvn -version

# Check Docker (if using Docker execution)
docker --version
```

---

## ⚡ Quick Start

### **Run Your First Test (Local)**
```bash
# Navigate to project directory
cd /path/to/UIAutomationSelenium

# Run login test on local Chrome
mvn clean test -Dtest=LoginTest -Dbrowser=chrome

# View report
open test-output/extent-reports/*.html
```

### **Run Test on Docker**
```bash
# Start Docker Grid
docker-compose up -d

# Wait for grid to be ready (60 seconds)
sleep 60

# Run test
bash run-tests.sh docker chrome LoginTest

# Stop Docker Grid
docker-compose down
```

---

## 🧪 Running Tests

### **Local Execution**

#### **Maven Commands**
```bash
# Run specific test
mvn clean test -Dtest=LoginTest

# Run with specific browser
mvn clean test -Dtest=LoginTest -Dbrowser=chrome

# Run all tests
mvn clean test

# Run with custom environment
mvn clean test -Dtest.env=local -Dbrowser=firefox
```

#### **Using Shell Scripts**
```bash
# Chrome
bash run-tests.sh local chrome LoginTest

# Firefox
bash run-tests.sh local firefox LoginTest

# Safari (macOS only)
bash run-tests.sh local safari LoginTest
```

### **Docker Execution**

#### **Start Docker Grid**
```bash
# Start Selenium Grid
docker-compose up -d

# Check grid status
docker-compose ps

# View grid console
open http://localhost:4444/ui
```

#### **Run Tests on Docker**
```bash
# Using shell script
bash run-tests.sh docker chrome LoginTest

# Using Maven
mvn clean test -Dtest.env=docker -Dbrowser=chrome -Dtest=LoginTest
```

#### **Stop Docker Grid**
```bash
# Stop all containers
docker-compose down

# Clean up resources
docker system prune -f
```

### **Cloud Execution (Future)**

#### **BrowserStack**
```bash
# Set credentials as environment variables
export BROWSERSTACK_USERNAME="your_username"
export BROWSERSTACK_ACCESS_KEY="your_access_key"

# Run test
mvn clean test -Dtest.env=browserstack -Dbrowser=chrome
```

#### **LambdaTest**
```bash
# Set credentials
export LAMBDATEST_USERNAME="your_username"
export LAMBDATEST_ACCESS_KEY="your_access_key"

# Run test
mvn clean test -Dtest.env=lambdatest -Dbrowser=chrome
```

---

## ⚙️ Configuration

### **Environment Configuration Files**

The framework supports multiple environment-specific configuration files:

| File | Purpose | Usage |
|------|---------|-------|
| `config.properties` | Default configuration | Always loaded |
| `config-local.properties` | Local execution | `-Dtest.env=local` |
| `config-docker.properties` | Docker Grid | `-Dtest.env=docker` |
| `config-browserstack.properties` | BrowserStack | `-Dtest.env=browserstack` |
| `config-lambdatest.properties` | LambdaTest | `-Dtest.env=lambdatest` |
| `config-saucelabs.properties` | Sauce Labs | `-Dtest.env=saucelabs` |

### **Key Configuration Properties**

```properties
# Execution mode
execution.mode=local

# Browser selection
browser=chrome

# Application URL (can be changed per requirement)
base.url=https://your-application-url.com/

# Timeouts (seconds)
implicit.wait=10
explicit.wait=15
page.load.timeout=30

# Test credentials
test.learner.email=test@example.com
test.learner.password=securePassword123
```

### **Changing Test URL**
```bash
# Option 1: Edit config.properties
# Update: base.url=https://new-url.com

# Option 2: Pass at runtime
mvn clean test -Dbase.url=https://new-url.com
```

---

## 📊 Reports

### **ExtentReports (HTML)**

**Location**: `test-output/extent-reports/`

**Features**:
- Beautiful HTML dashboard
- Step-by-step test execution logs
- System information
- Test duration and timestamps
- Pass/Fail status with details
- Automatic cleanup (keeps latest 5 reports)

**View Report**:
```bash
# macOS
open test-output/extent-reports/*.html

# Linux
xdg-open test-output/extent-reports/*.html

# Windows
start test-output/extent-reports/*.html
```

### **Allure Reports (Interactive)**

**Generate Allure Report**:
```bash
# Generate report
mvn allure:report

# Serve report (opens in browser)
mvn allure:serve
```

**Features**:
- Interactive dashboard
- Test trends over time
- Detailed test steps
- Screenshot attachments
- Environment information

### **Logs**

**Location**: `test-output/logs/`

| File | Description |
|------|-------------|
| `automation.log` | Current session logs |
| `automation-rolling.log` | Rolling log file |
| `automation-rolling-*.log.gz` | Archived compressed logs |

---

## 🐳 Docker Execution

### **Quick Reference**

#### **Start Docker Grid**
```bash
cd /path/to/UIAutomationSelenium
docker-compose up -d
sleep 60  # Wait for grid to initialize
```

#### **Run Tests**
```bash
# Set Java 11 (if needed)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Run test
bash run-tests.sh docker chrome LoginTest
```

#### **Monitor Execution**
```bash
# Check grid status
docker-compose ps

# View grid console
open http://localhost:4444/ui

# Check logs
docker-compose logs -f
```

#### **Stop Grid**
```bash
docker-compose down
```

### **Docker Grid Components**

| Service | Image | Replicas | Purpose |
|---------|-------|----------|---------|
| selenium-hub | selenium/hub:4.15.0 | 1 | Central hub |
| chrome-node | selenium/node-chrome:4.15.0 | 2 | Chrome browsers |
| firefox-node | selenium/node-firefox:4.15.0 | 1 | Firefox browser |
| edge-node | selenium/node-edge:4.15.0 | 1 | Edge browser |

### **VNC Live View**

Watch tests execute in real-time:
1. Open: `http://localhost:4444/ui`
2. Navigate to active session
3. Password: `secret`

---

## 📁 Project Structure

```
UIAutomationSelenium/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── base/
│   │   │   │   ├── BasePage.java          # Common page methods
│   │   │   │   └── BaseTest.java          # Test setup/teardown
│   │   │   ├── pages/
│   │   │   │   └── Login.java             # Login page object
│   │   │   └── utils/
│   │   │       ├── ConfigReader.java      # Configuration manager
│   │   │       ├── ExtentReportManager.java  # Report manager
│   │   │       └── UnifiedDriverManager.java # Driver manager
│   │   └── resources/
│   │       ├── config*.properties         # Environment configs
│   │       ├── log4j2.xml                # Logging config
│   │       └── testng.xml                # TestNG suite
│   └── test/
│       └── java/
│           └── tests/
│               └── LoginTest.java         # Login test cases
├── test-output/
│   ├── extent-reports/                   # HTML reports
│   ├── logs/                             # Execution logs
│   └── allure-results/                   # Allure data
├── docker-compose.yml                    # Docker Grid config
├── pom.xml                              # Maven dependencies
├── .gitignore                           # Git ignore rules
└── *.sh                                 # Utility scripts
```

---

## 🔧 Troubleshooting

### **Common Issues**

#### **1. Java Version Error**
```
Error: invalid target release: 11
```

**Solution**:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
java -version  # Verify Java 11
```

#### **2. Docker Grid Not Ready**
```
Error: Could not start a new session
```

**Solution**:
```bash
# Check grid status
curl -sSL http://localhost:4444/wd/hub/status

# Restart grid
docker-compose down
docker-compose up -d
sleep 60
```

#### **3. Compilation Errors**
```
Error: Compilation failure
```

**Solution**:
```bash
# Clean and rebuild
mvn clean install -U

# Force update dependencies
mvn dependency:purge-local-repository
```

#### **4. Log4j2 Configuration Not Found**
```
Error: No configuration found
```

**Solution**:
```bash
# Ensure log4j2.xml is in correct location
ls -la src/main/resources/log4j2.xml

# If missing, copy from src/test/resources
cp src/test/resources/log4j2.xml src/main/resources/
```

#### **5. Docker Nodes Not Connecting**
```
Error: SE_EVENT_BUS_HOST not set
```

**Solution**: Already fixed in `docker-compose.yml` with environment variables

---

## 💡 Best Practices

### **Test Development**
1. **Follow POM**: Keep page elements and actions in page objects
2. **Use Meaningful Names**: Clear method and variable names
3. **Add Logging**: Log all important actions and verifications
4. **Handle Waits**: Use explicit waits instead of Thread.sleep()
5. **Assertions**: Use descriptive assertion messages

### **Configuration Management**
1. **Never Commit Credentials**: Use environment variables for sensitive data
2. **Environment-Specific Configs**: Separate configs for each environment
3. **Parameterize URLs**: Make base URLs configurable
4. **Version Control**: Keep configs in version control (except credentials)

### **Execution**
1. **Clean Before Run**: Always run `mvn clean` before tests
2. **Check Environment**: Verify Java version and Docker status
3. **Wait for Grid**: Give Docker Grid 60 seconds to initialize
4. **Monitor Resources**: Check Docker memory/CPU usage

### **Reporting**
1. **Review Reports**: Check HTML reports after each run
2. **Archive Important Runs**: Save reports for significant test runs
3. **Clean Old Reports**: Framework auto-keeps latest 5 reports
4. **Log Analysis**: Check logs for debugging failures

---

## 🚀 Future Enhancements

### **Planned Features**
- [ ] CI/CD Integration (Jenkins/GitHub Actions)
- [ ] Database connectivity for test data
- [ ] API testing integration
- [ ] Parallel test execution
- [ ] Data-driven testing with Excel/JSON
- [ ] Email notifications for test results
- [ ] Screenshot comparison tool
- [ ] Performance testing integration
- [ ] Mobile testing support


---

## 📄 License

This project is private and proprietary.

---

## ✅ Quick Command Reference

```bash
# Local Execution
mvn clean test -Dtest=LoginTest

# Docker Execution
docker-compose up -d && sleep 60 && bash run-tests.sh docker chrome LoginTest

# Set Java 11
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH

# View Reports
open test-output/extent-reports/*.html

# Stop Docker
docker-compose down

# Grid Console
open http://localhost:4444/ui
```

---

**Maintained by**: Deepak Kumar  
**Last Updated**: October 2025  
**Status**: ✅ Production Ready