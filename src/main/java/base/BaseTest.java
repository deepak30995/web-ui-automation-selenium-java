package base;

import com.aventstack.extentreports.ExtentTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.*;
import utils.ConfigReader;
import utils.ExtentReportManager;
import utils.UnifiedDriverManager;

/**
 * Enhanced BaseTest with environment variable support
 */
public class BaseTest {

    protected WebDriver driver;
    protected ExtentTest test;
    private static final Logger logger = LogManager.getLogger(BaseTest.class);

    @BeforeSuite
    public void suiteSetup() {
        // Print configuration info for debugging
        ConfigReader.printConfigInfo();

        // Initialize reports
        ExtentReportManager.initReports();

        String executionMode = ConfigReader.getProperty("execution.mode", "local");
        String browser = ConfigReader.getProperty("browser", "chrome");
        String environment = ConfigReader.getProperty("environment", "UNKNOWN");

        logger.info("=".repeat(80));
        logger.info("TEST SUITE STARTED");
        logger.info("Environment: {}", environment);
        logger.info("Execution Mode: {}", executionMode);
        logger.info("Browser: {}", browser);
        logger.info("Config File: {}", ConfigReader.getCurrentConfigFile());
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        logger.info("=".repeat(80));
    }

    private static long suiteStartTime;
    @BeforeMethod
    public void setUp(java.lang.reflect.Method method) {
        try {
            // Initialize driver using unified manager
            driver = UnifiedDriverManager.getDriver();

            suiteStartTime = System.currentTimeMillis();
            // Create extent test with environment info
            String environment = ConfigReader.getProperty("environment", "UNKNOWN");
            test = ExtentReportManager.createTest(
                    method.getName(),
                    "Test executed on " + environment + " environment"
            );

            // Navigate to base URL
            String baseUrl = ConfigReader.getProperty("base.url");
            driver.get(baseUrl);

            String executionMode = ConfigReader.getProperty("execution.mode", "local");
            String browser = ConfigReader.getProperty("browser", "chrome");

            logger.info("Test setup completed for: {} | Environment: {} | Mode: {} | Browser: {}",
                    method.getName(), environment, executionMode, browser);

            test.info(String.format("Test started: %s | Environment: %s | Mode: %s | Browser: %s",
                    method.getName(), environment, executionMode, browser));

        } catch (Exception e) {
            logger.error("Error in test setup: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to setup test: " + method.getName(), e);
        }
    }

    @AfterMethod
    public void tearDown(java.lang.reflect.Method method, ITestResult result) {
        try {
            // Mark test status for cloud platforms
            if (result.getStatus() == ITestResult.SUCCESS) {
                UnifiedDriverManager.markTestStatus("passed", "Test passed");
                test.pass("Test completed successfully");
            } else if (result.getStatus() == ITestResult.FAILURE) {
                UnifiedDriverManager.markTestStatus("failed", result.getThrowable().getMessage());
                test.fail("Test failed: " + result.getThrowable().getMessage());
            }

            // Quit driver
            if (driver != null) {
                UnifiedDriverManager.quitDriver();
            }

            logger.info("Test completed: {}", method.getName());

        } catch (Exception e) {
            logger.error("Error in test teardown: {}", e.getMessage());
        }
    }

    @AfterSuite
    public void suiteTeardown() {
        try {
            // Flush reports first
            ExtentReportManager.flushReports();

            // Get report paths for logging
            String reportPath = ExtentReportManager.getReportPath();
            String absoluteReportPath = ExtentReportManager.getAbsoluteReportPath();

            // Your excellent logging format (keep this!)
            logger.info("=".repeat(80));
            logger.info("TEST SUITE COMPLETED");
            logger.info("Environment: {}", ConfigReader.getProperty("environment", "UNKNOWN"));
            logger.info("Execution Mode: {}", ConfigReader.getProperty("execution.mode", "local"));
            logger.info("Browser: {}", ConfigReader.getProperty("browser", "chrome"));

            // Enhanced report logging
            if (reportPath != null) {
                logger.info("Reports available at: {}", reportPath);
                logger.info("Absolute path: {}", absoluteReportPath);

                // Verify report file exists
                if (ExtentReportManager.reportExists()) {
                    logger.info("✅ Report file verified and accessible");
                } else {
                    logger.warn("⚠️  Report file not found at expected location");
                }
            } else {
                logger.warn("❌ Report path is null - report may not have been generated");
            }

            // Additional useful information
            logger.info("Total execution time: {} ms", System.currentTimeMillis() - suiteStartTime);
            logger.info("Java Version: {}", System.getProperty("java.version"));
            logger.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
            logger.info("=".repeat(80));

        } catch (Exception e) {
            logger.error("Error in suite teardown: {}", e.getMessage(), e);

            // Still try to log basic completion info even if there's an error
            logger.info("=".repeat(80));
            logger.info("TEST SUITE COMPLETED (with errors)");
            logger.info("Environment: {}", ConfigReader.getProperty("environment", "UNKNOWN"));
            logger.info("=".repeat(80));
        }
    }
}