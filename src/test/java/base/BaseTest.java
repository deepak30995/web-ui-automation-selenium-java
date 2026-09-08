package base;

import com.aventstack.extentreports.ExtentTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.*;
import utils.ConfigReader;
import utils.ExtentReportManager;
import utils.UnifiedDriverManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Enhanced BaseTest with environment variable support
 */
public class BaseTest {

    protected WebDriver driver;

    // Held per thread so parallel tests never overwrite each other's report node
    private static final ThreadLocal<ExtentTest> currentTest = new ThreadLocal<>();

    private static final Logger logger = LogManager.getLogger(BaseTest.class);

    // Set once per suite, not per test method
    private static long suiteStartTime;

    /**
     * Report node for the test running on this thread.
     */
    protected ExtentTest getTest() {
        return currentTest.get();
    }

    @BeforeSuite
    public void suiteSetup() {
        suiteStartTime = System.currentTimeMillis();

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

    @BeforeMethod
    public void setUp(java.lang.reflect.Method method) {
        try {
            // Initialize driver using unified manager
            driver = UnifiedDriverManager.getDriver();

            // Create extent test with environment info
            String environment = ConfigReader.getProperty("environment", "UNKNOWN");
            currentTest.set(ExtentReportManager.createTest(
                    method.getName(),
                    "Test executed on " + environment + " environment"
            ));

            // Navigate to base URL
            String baseUrl = ConfigReader.getProperty("base.url");
            driver.get(baseUrl);

            String executionMode = ConfigReader.getProperty("execution.mode", "local");
            String browser = ConfigReader.getProperty("browser", "chrome");

            logger.info("Test setup completed for: {} | Environment: {} | Mode: {} | Browser: {}",
                    method.getName(), environment, executionMode, browser);

            getTest().info(String.format("Test started: %s | Environment: %s | Mode: %s | Browser: %s",
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
                getTest().pass("Test completed successfully");
            } else if (result.getStatus() == ITestResult.FAILURE) {
                // Capture evidence while the driver is still alive
                String screenshotPath = captureScreenshot(method.getName());
                if (screenshotPath != null) {
                    ExtentReportManager.addScreenshot(getTest(), screenshotPath, "Failure screenshot");
                }
                UnifiedDriverManager.markTestStatus("failed", result.getThrowable().getMessage());
                getTest().fail("Test failed: " + result.getThrowable().getMessage());
            }

            // Quit driver
            if (driver != null) {
                UnifiedDriverManager.quitDriver();
            }

            logger.info("Test completed: {}", method.getName());

        } catch (Exception e) {
            logger.error("Error in test teardown: {}", e.getMessage());
        } finally {
            currentTest.remove();
        }
    }

    /**
     * Saves a screenshot of the current browser state.
     * @return absolute path to the saved file, or null if the capture failed
     */
    private String captureScreenshot(String testName) {
        if (driver == null) {
            return null;
        }
        try {
            String screenshotDir = ConfigReader.getProperty("screenshots.path", "test-output/screenshots");
            Path directory = Paths.get(screenshotDir);
            Files.createDirectories(directory);

            String timestamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
            File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path target = directory.resolve(testName + "_" + timestamp + ".png");

            Files.copy(source.toPath(), target);
            logger.info("Failure screenshot saved: {}", target.toAbsolutePath());
            return target.toAbsolutePath().toString();

        } catch (Exception e) {
            logger.warn("Could not capture failure screenshot: {}", e.getMessage());
            return null;
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
            logger.info("Total suite execution time: {} ms", System.currentTimeMillis() - suiteStartTime);
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
