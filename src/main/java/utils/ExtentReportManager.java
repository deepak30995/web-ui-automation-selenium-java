package utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * ExtentReports management utility class
 * Handles report initialization, test creation, and report generation
 */
public class ExtentReportManager {

    private static final Logger logger = LogManager.getLogger(ExtentReportManager.class);
    private static ExtentReports extent;
    private static String reportPath;
    private static final int MAX_REPORTS_TO_KEEP = 5;

    /**
     * Initialize ExtentReports
     */
    public static void initReports() {
        if (extent == null) {
            // Create reports directory if it doesn't exist
            String reportsDir = "test-output/extent-reports";
            File reportsDirFile = new File(reportsDir);

            // Create directory if it doesn't exist
            if (!reportsDirFile.exists()) {
                boolean created = reportsDirFile.mkdirs();
                if (created) {
                    logger.info("Created reports directory: {}", reportsDir);
                } else {
                    logger.error("Failed to create reports directory: {}", reportsDir);
                }
            }

            // Clean old reports (keep only 5 latest) - only if directory exists
            if (reportsDirFile.exists()) {
                cleanOldReports(reportsDirFile, MAX_REPORTS_TO_KEEP);
            }

            // Generate report file name with timestamp
            String timestamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
            reportPath = reportsDir + "/ExtentReport_" + timestamp + ".html";

            // Configure ExtentSparkReporter
            ExtentSparkReporter sparkReporter = new ExtentSparkReporter(reportPath);

            // Configure reporter settings
            try {
                sparkReporter.config().setDocumentTitle("Selenium Automation Test Report");
                sparkReporter.config().setReportName("Graphy Website UI Test Execution Report");
                sparkReporter.config().setTheme(Theme.STANDARD);
                sparkReporter.config().setTimeStampFormat("dd-MM-yyyy HH:mm:ss");

                // Additional configuration
                sparkReporter.config().setEncoding("UTF-8");
                sparkReporter.config().setCss("body { font-family: Arial, sans-serif; }");

            } catch (Exception e) {
                logger.warn("Could not configure ExtentSparkReporter: {}", e.getMessage());
            }

            // Initialize ExtentReports
            extent = new ExtentReports();
            extent.attachReporter(sparkReporter);

            // Set system information
            setSystemInformation();

            logger.info("ExtentReports initialized successfully. Report path: {}", reportPath);
        }
    }

    /**
     * Set system information for the report
     */
    private static void setSystemInformation() {
        try {
            extent.setSystemInfo("OS", System.getProperty("os.name"));
            extent.setSystemInfo("OS Version", System.getProperty("os.version"));
            extent.setSystemInfo("Java Version", System.getProperty("java.version"));
            extent.setSystemInfo("User", System.getProperty("user.name"));
            extent.setSystemInfo("Timezone", System.getProperty("user.timezone"));
            extent.setSystemInfo("Report Generated", new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()));

            // Add configuration details - with null safety
            extent.setSystemInfo("Browser", getConfigProperty("browser", "chrome"));
            extent.setSystemInfo("Environment", getConfigProperty("environment", "QA"));
            extent.setSystemInfo("Execution Mode", getConfigProperty("execution.mode", "local"));
            extent.setSystemInfo("Base URL", getConfigProperty("base.url", "N/A"));
            extent.setSystemInfo("Test Framework", "TestNG + Selenium WebDriver");

        } catch (Exception e) {
            logger.warn("Could not load all system properties for report: {}", e.getMessage());
        }
    }

    /**
     * Clean old reports, keeping only the specified number of latest reports
     */
    private static void cleanOldReports(File reportsDir, int maxReports) {
        try {
            File[] reportFiles = reportsDir.listFiles((dir, name) ->
                    name.startsWith("ExtentReport_") && name.endsWith(".html"));

            if (reportFiles != null && reportFiles.length > maxReports) {
                // Sort by last modified time (newest first)
                Arrays.sort(reportFiles, (f1, f2) ->
                        Long.compare(f2.lastModified(), f1.lastModified()));

                // Delete older reports
                int deletedCount = 0;
                for (int i = maxReports; i < reportFiles.length; i++) {
                    if (reportFiles[i].delete()) {
                        logger.info("Deleted old report: {}", reportFiles[i].getName());
                        deletedCount++;
                    } else {
                        logger.warn("Failed to delete old report: {}", reportFiles[i].getName());
                    }
                }

                if (deletedCount > 0) {
                    logger.info("Cleaned up {} old reports, keeping {} latest reports",
                            deletedCount, maxReports);
                }
            } else {
                logger.info("No old reports to clean up. Current reports: {}",
                        reportFiles != null ? reportFiles.length : 0);
            }

        } catch (Exception e) {
            logger.error("Error cleaning old reports: {}", e.getMessage());
        }
    }

    /**
     * Helper method to safely get config properties
     */
    private static String getConfigProperty(String key, String defaultValue) {
        try {
            return ConfigReader.getProperty(key, defaultValue);
        } catch (Exception e) {
            logger.warn("Could not read property '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Create a new test in the report
     * @param testName Test name
     * @return ExtentTest instance
     */
    public static ExtentTest createTest(String testName) {
        if (extent == null) {
            initReports();
        }
        ExtentTest test = extent.createTest(testName);
        logger.debug("Created test: {}", testName);
        return test;
    }

    /**
     * Create a new test with description
     * @param testName Test name
     * @param testDescription Test description
     * @return ExtentTest instance
     */
    public static ExtentTest createTest(String testName, String testDescription) {
        if (extent == null) {
            initReports();
        }
        ExtentTest test = extent.createTest(testName, testDescription);
        logger.debug("Created test: {} with description: {}", testName, testDescription);
        return test;
    }

    /**
     * Create a test with category/tag
     * @param testName Test name
     * @param testDescription Test description
     * @param category Test category/tag
     * @return ExtentTest instance
     */
    public static ExtentTest createTest(String testName, String testDescription, String category) {
        if (extent == null) {
            initReports();
        }
        ExtentTest test = extent.createTest(testName, testDescription);
        test.assignCategory(category);
        logger.debug("Created test: {} with category: {}", testName, category);
        return test;
    }

    /**
     * Flush the reports (write to file)
     */
    public static void flushReports() {
        if (extent != null) {
            try {
                extent.flush();
                logger.info("ExtentReports flushed successfully.");
                logger.info("Report available at: {}", reportPath);
                logger.info("Open report: file://{}", new File(reportPath).getAbsolutePath());

                // Auto-open report on macOS (optional)
                if (Boolean.parseBoolean(getConfigProperty("auto.open.report", "false"))) {
                    openReportInBrowser();
                }

            } catch (Exception e) {
                logger.error("Error flushing ExtentReports: {}", e.getMessage());
            }
        } else {
            logger.warn("ExtentReports instance is null, cannot flush reports");
        }
    }

    /**
     * Auto-open report in default browser (macOS/Windows)
     */
    private static void openReportInBrowser() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String absolutePath = new File(reportPath).getAbsolutePath();

            if (os.contains("mac")) {
                Runtime.getRuntime().exec("open " + absolutePath);
            } else if (os.contains("windows")) {
                Runtime.getRuntime().exec("cmd /c start " + absolutePath);
            } else if (os.contains("linux")) {
                Runtime.getRuntime().exec("xdg-open " + absolutePath);
            }

            logger.info("Opened report in default browser");

        } catch (IOException e) {
            logger.warn("Could not open report in browser: {}", e.getMessage());
        }
    }

    /**
     * Get the report file path
     * @return Report file path
     */
    public static String getReportPath() {
        return reportPath;
    }

    /**
     * Get the absolute report file path
     * @return Absolute report file path
     */
    public static String getAbsoluteReportPath() {
        if (reportPath != null) {
            return new File(reportPath).getAbsolutePath();
        }
        return null;
    }

    /**
     * Check if report file exists
     * @return true if report file exists
     */
    public static boolean reportExists() {
        return reportPath != null && new File(reportPath).exists();
    }

    /**
     * Add screenshot to test
     * @param test ExtentTest instance
     * @param screenshotPath Screenshot file path
     * @param title Screenshot title
     */
    public static void addScreenshot(ExtentTest test, String screenshotPath, String title) {
        try {
            if (test != null && screenshotPath != null) {
                // Ensure screenshot file exists
                File screenshotFile = new File(screenshotPath);
                if (screenshotFile.exists()) {
                    test.addScreenCaptureFromPath(screenshotPath, title);
                    logger.info("Screenshot added to report: {}", screenshotPath);
                } else {
                    logger.warn("Screenshot file not found: {}", screenshotPath);
                }
            }
        } catch (Exception e) {
            logger.error("Error adding screenshot to report: {}", e.getMessage());
        }
    }

    /**
     * Log info message to test
     * @param test ExtentTest instance
     * @param message Info message
     */
    public static void logInfo(ExtentTest test, String message) {
        if (test != null) {
            test.info(message);
        }
    }

    /**
     * Log pass message to test
     * @param test ExtentTest instance
     * @param message Pass message
     */
    public static void logPass(ExtentTest test, String message) {
        if (test != null) {
            test.pass(message);
        }
    }

    /**
     * Log fail message to test
     * @param test ExtentTest instance
     * @param message Fail message
     */
    public static void logFail(ExtentTest test, String message) {
        if (test != null) {
            test.fail(message);
        }
    }

    /**
     * Log skip message to test
     * @param test ExtentTest instance
     * @param message Skip message
     */
    public static void logSkip(ExtentTest test, String message) {
        if (test != null) {
            test.skip(message);
        }
    }

    /**
     * Log warning message to test
     * @param test ExtentTest instance
     * @param message Warning message
     */
    public static void logWarning(ExtentTest test, String message) {
        if (test != null) {
            test.warning(message);
        }
    }
}