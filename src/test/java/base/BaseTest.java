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
import utils.FailureReporter;
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

    /**
     * Held per thread. TestNG reuses one test-class instance across parallel methods,
     * so a plain instance field is shared by every thread and the last writer wins -
     * which silently makes three parallel tests drive one browser.
     */
    private static final ThreadLocal<WebDriver> currentDriver = new ThreadLocal<>();

    /** Driver for the test running on this thread. */
    protected WebDriver getDriver() {
        return currentDriver.get();
    }

    // Held per thread so parallel tests never overwrite each other's report node
    private static final ThreadLocal<ExtentTest> currentTest = new ThreadLocal<>();

    /**
     * Whether setUp ran to completion for the test on this thread.
     *
     * This tracks <em>this</em> method only, which is sound because setUp is the one
     * @BeforeMethod in the project - Base owns setup, by convention. A second
     * @BeforeMethod added in a test class would not be covered: a failure there would
     * be read as case 3 below rather than as a setup failure, so set this flag from
     * it too if one is ever introduced.
     *
     * TestNG reports an attempt that RetryAnalyzer is about to re-run with status
     * SKIP - the very status a @BeforeMethod failure produces - so the status alone
     * cannot tell the two apart. Without this flag tearDown called every retried
     * test "Setup failed", which pointed the reader at driver setup for a failure
     * that happened mid-test.
     */
    private static final ThreadLocal<Boolean> setupCompleted = ThreadLocal.withInitial(() -> false);

    private static final Logger logger = LogManager.getLogger(BaseTest.class);

    // Set once per suite, not per test method
    private static long suiteStartTime;

    /**
     * Report node for the test running on this thread.
     */
    protected ExtentTest getTest() {
        return currentTest.get();
    }

    @BeforeSuite(alwaysRun = true)
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

    @BeforeMethod(alwaysRun = true)
    public void setUp(java.lang.reflect.Method method) {
        // Created before anything that can throw, so a setUp failure still has a
        // report node to be recorded against. Driver creation used to come first,
        // which left a failure there invisible to the report entirely.
        String environment = ConfigReader.getProperty("environment", "UNKNOWN");
        currentTest.set(ExtentReportManager.createTest(
                method.getName(),
                "Test executed on " + environment + " environment"
        ));
        setupCompleted.set(false);

        try {
            // Initialize driver using unified manager
            currentDriver.set(UnifiedDriverManager.getDriver());

            // Navigate to base URL
            String baseUrl = ConfigReader.getBaseUrl();
            getDriver().get(baseUrl);

            String executionMode = ConfigReader.getProperty("execution.mode", "local");
            String browser = ConfigReader.getProperty("browser", "chrome");

            logger.info("Test setup completed for: {} | Environment: {} | Mode: {} | Browser: {}",
                    method.getName(), environment, executionMode, browser);

            getTest().info(String.format("Test started: %s | Environment: %s | Mode: %s | Browser: %s",
                    method.getName(), environment, executionMode, browser));

            setupCompleted.set(true);

        } catch (Exception e) {
            logger.error("Error in test setup: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to setup test: " + method.getName(), e);
        }
    }

    @AfterMethod(alwaysRun = true)
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
                FailureReporter.capture(getDriver(), method.getName(), result.getThrowable(), screenshotPath);

                UnifiedDriverManager.markTestStatus("failed", result.getThrowable().getMessage());
                getTest().fail("Test failed: " + result.getThrowable().getMessage());

            } else if (result.getStatus() == ITestResult.SKIP) {
                // Four different things arrive here with this one status, and they
                // need four different reports. Taken in this order, because more than
                // one condition can hold at once and the earlier ones are stronger:
                //
                //  1. this test's own @BeforeMethod failed. TestNG skips the test
                //     method and records the config exception on the result. Without
                //     this case the Extent node kept its default status and rendered
                //     as a PASS, so a run where nothing executed was emailed as green.
                //  2. an attempt RetryAnalyzer is about to re-run. It failed inside
                //     the test, not in setUp. Reported as the failure it is.
                //  3. TestNG skipped the test because some *other* test's config
                //     method failed earlier in the suite - its configfailurepolicy.
                //     The throwable is the other test's exception, reused, so this
                //     test neither failed nor was retried: it never ran at all.
                //  4. a deliberate skip from a group filter or dependsOnMethods,
                //     which carries no throwable.
                //
                // 1 and 2 are reported as failures, not skips, to match the suite
                // result: surefire counts them as failures and so does the email.
                Throwable cause = result.getThrowable();
                boolean setupFailed = !setupCompleted.get();

                if (cause == null) {
                    getTest().skip("Test skipped");

                } else if (setupFailed || result.wasRetried()) {
                    String screenshotPath = captureScreenshot(method.getName());
                    if (screenshotPath != null) {
                        ExtentReportManager.addScreenshot(getTest(), screenshotPath,
                                setupFailed ? "Setup failure screenshot" : "Failure screenshot");
                    }
                    FailureReporter.capture(getDriver(), method.getName(), cause, screenshotPath);

                    // Only a setUp failure is unwrapped, because only setUp wraps:
                    // it throws RuntimeException("Failed to setup test: X"), whose
                    // message says nothing about why. A test failure must be reported
                    // as thrown - unwrapping a TimeoutException discards "tried for
                    // 30 second(s)" and surfaces the NoSuchElementException the wait
                    // was swallowing, which reads as a stale locator and is not one.
                    String reason = setupFailed ? describeRootCause(cause) : cause.getMessage();
                    String label = setupFailed ? "Setup failed - " : "Test failed (retried): ";

                    UnifiedDriverManager.markTestStatus("failed", reason);
                    getTest().fail(label + reason);

                } else {
                    // Case 3. No screenshot and no failure bundle: this test never
                    // ran, so the browser is sitting on the base URL and its DOM says
                    // nothing about anything. Capturing it buried the one bundle that
                    // did matter under two that carried another test's exception, and
                    // the agents read them as evidence about this test.
                    getTest().skip("Skipped by TestNG after an earlier configuration "
                            + "failure - " + cause.getMessage());
                }
            }

            // Quit driver
            if (getDriver() != null) {
                UnifiedDriverManager.quitDriver();
            }

            logger.info("Test completed: {}", method.getName());

        } catch (Exception e) {
            logger.error("Error in test teardown: {}", e.getMessage());
        } finally {
            currentTest.remove();
            currentDriver.remove();
            setupCompleted.remove();
        }
    }

    /**
     * Names the innermost cause, for exceptions this class wrapped itself.
     * @return simple class name and message of the deepest cause
     */
    private static String describeRootCause(Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /**
     * Saves a screenshot of the current browser state.
     * @return absolute path to the saved file, or null if the capture failed
     */
    private String captureScreenshot(String testName) {
        if (getDriver() == null) {
            return null;
        }
        try {
            String screenshotDir = ConfigReader.getProperty("screenshots.path", "test-output/screenshots");
            Path directory = Paths.get(screenshotDir);
            Files.createDirectories(directory);

            String timestamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
            File source = ((TakesScreenshot) getDriver()).getScreenshotAs(OutputType.FILE);
            Path target = directory.resolve(testName + "_" + timestamp + ".png");

            Files.copy(source.toPath(), target);
            logger.info("Failure screenshot saved: {}", target.toAbsolutePath());
            return target.toAbsolutePath().toString();

        } catch (Exception e) {
            logger.warn("Could not capture failure screenshot: {}", e.getMessage());
            return null;
        }
    }

    @AfterSuite(alwaysRun = true)
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
