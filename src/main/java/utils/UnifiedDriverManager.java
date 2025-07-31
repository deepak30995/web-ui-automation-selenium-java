package utils;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;




import utils.ConfigReader;  // Add this line
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
// ... other imports

/**
 * Unified WebDriver management utility class for all execution modes
 * Supports: Local, Docker Grid, BrowserStack, LambdaTest, AWS, Sauce Labs
 */
public class UnifiedDriverManager {

    private static final Logger logger = LogManager.getLogger(UnifiedDriverManager.class);
    private static ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();

    /**
     * Get WebDriver instance based on configuration
     * @return WebDriver instance
     */
    public static WebDriver getDriver() {
        if (driverThreadLocal.get() == null) {
            String executionMode = ConfigReader.getProperty("execution.mode", "local");
            String browser = ConfigReader.getProperty("browser", "chrome");

            WebDriver driver = createDriver(executionMode, browser);
            driverThreadLocal.set(driver);

            // Configure common settings
            configureDriver(driver);
        }
        return driverThreadLocal.get();
    }

    /**
     * Create WebDriver based on execution mode
     */
    private static WebDriver createDriver(String executionMode, String browser) {
        logger.info("Creating driver - Mode: {}, Browser: {}", executionMode, browser);

        switch (executionMode.toLowerCase()) {
            case "local":
                return createLocalDriver(browser);

            case "remote":
            case "docker":
            case "grid":
                return createRemoteDriver(browser);

            case "browserstack":
                return createBrowserStackDriver(browser);

            case "lambdatest":
                return createLambdaTestDriver(browser);

            case "saucelabs":
                return createSauceLabsDriver(browser);

            case "aws":
                return createAWSDeviceFarmDriver(browser);

            default:
                throw new IllegalArgumentException("Unsupported execution mode: " + executionMode);
        }
    }

    /**
     * Create local WebDriver
     */
    private static WebDriver createLocalDriver(String browser) {
        WebDriver driver;

        switch (browser.toLowerCase()) {
            case "chrome":
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver(getChromeOptions());
                break;

            case "firefox":
                WebDriverManager.firefoxdriver().setup();
                driver = new FirefoxDriver(getFirefoxOptions());
                break;

            case "edge":
                WebDriverManager.edgedriver().setup();
                driver = new EdgeDriver(getEdgeOptions());
                break;

            case "safari":
                // Safari driver comes with macOS, no setup needed
                driver = new SafariDriver(getSafariOptions());
                break;

            default:
                throw new IllegalArgumentException("Browser not supported: " + browser);
        }

        logger.info("Local {} driver created successfully", browser);
        return driver;
    }

    /**
     * Create RemoteWebDriver for Docker/Grid
     */
    private static WebDriver createRemoteDriver(String browser) {
        try {
            String hubUrl = ConfigReader.getProperty("selenium.hub.url", "http://localhost:4444/wd/hub");
            DesiredCapabilities capabilities = new DesiredCapabilities();

            switch (browser.toLowerCase()) {
                case "chrome":
                    capabilities.setCapability(ChromeOptions.CAPABILITY, getChromeOptions());
                    capabilities.setBrowserName("chrome");
                    break;

                case "firefox":
                    capabilities.setCapability(FirefoxOptions.FIREFOX_OPTIONS, getFirefoxOptions());
                    capabilities.setBrowserName("firefox");
                    break;

                case "edge":
                    capabilities.setCapability(EdgeOptions.CAPABILITY, getEdgeOptions());
                    capabilities.setBrowserName("MicrosoftEdge");
                    break;

                default:
                    throw new IllegalArgumentException("Browser not supported for remote execution: " + browser);
            }

            WebDriver driver = new RemoteWebDriver(new URL(hubUrl), capabilities);
            logger.info("Remote {} driver created with hub: {}", browser, hubUrl);
            return driver;

        } catch (Exception e) {
            logger.error("Error creating remote driver: {}", e.getMessage());
            throw new RuntimeException("Failed to create remote driver", e);
        }
    }

    /**
     * Create BrowserStack WebDriver
     */
    private static WebDriver createBrowserStackDriver(String browser) {
        try {
            String username = ConfigReader.getProperty("browserstack.username");
            String accessKey = ConfigReader.getProperty("browserstack.accesskey");

            if (username == null || accessKey == null) {
                throw new RuntimeException("BrowserStack credentials not found in config.properties");
            }

            String hubUrl = String.format("https://%s:%s@hub-cloud.browserstack.com/wd/hub", username, accessKey);

            DesiredCapabilities capabilities = new DesiredCapabilities();
            capabilities.setCapability("browserName", browser);
            capabilities.setCapability("browserVersion", ConfigReader.getProperty("browser.version", "latest"));
            capabilities.setCapability("os", ConfigReader.getProperty("os.name", "OS X"));
            capabilities.setCapability("osVersion", ConfigReader.getProperty("os.version", "Monterey"));

            // BrowserStack specific options
            Map<String, Object> bsOptions = new HashMap<>();
            bsOptions.put("projectName", ConfigReader.getProperty("project.name", "Selenium Automation"));
            bsOptions.put("buildName", ConfigReader.getProperty("build.name", "Build-" + System.currentTimeMillis()));
            bsOptions.put("sessionName", "Test Session - " + browser);
            bsOptions.put("local", "false");
            bsOptions.put("seleniumVersion", "4.15.0");
            bsOptions.put("debug", "true");
            bsOptions.put("video", "true");
            bsOptions.put("networkLogs", "true");
            bsOptions.put("consoleLogs", "info");

            capabilities.setCapability("bstack:options", bsOptions);

            WebDriver driver = new RemoteWebDriver(new URL(hubUrl), capabilities);
            logger.info("BrowserStack {} driver created successfully", browser);
            return driver;

        } catch (Exception e) {
            logger.error("Error creating BrowserStack driver: {}", e.getMessage());
            throw new RuntimeException("Failed to create BrowserStack driver", e);
        }
    }

    /**
     * Create LambdaTest WebDriver
     */
    private static WebDriver createLambdaTestDriver(String browser) {
        try {
            String username = ConfigReader.getProperty("lambdatest.username");
            String accessKey = ConfigReader.getProperty("lambdatest.accesskey");

            if (username == null || accessKey == null) {
                throw new RuntimeException("LambdaTest credentials not found in config.properties");
            }

            String hubUrl = String.format("https://%s:%s@hub.lambdatest.com/wd/hub", username, accessKey);

            DesiredCapabilities capabilities = new DesiredCapabilities();
            capabilities.setCapability("browserName", browser);
            capabilities.setCapability("browserVersion", ConfigReader.getProperty("browser.version", "latest"));
            capabilities.setCapability("platform", ConfigReader.getProperty("platform.name", "macOS Monterey"));

            // LambdaTest specific capabilities
            capabilities.setCapability("build", ConfigReader.getProperty("build.name", "Build-" + System.currentTimeMillis()));
            capabilities.setCapability("name", "Test Session - " + browser);
            capabilities.setCapability("project", ConfigReader.getProperty("project.name", "Selenium Automation"));
            capabilities.setCapability("video", true);
            capabilities.setCapability("visual", true);
            capabilities.setCapability("network", true);
            capabilities.setCapability("console", true);
            capabilities.setCapability("timezone", "UTC+05:30");

            WebDriver driver = new RemoteWebDriver(new URL(hubUrl), capabilities);
            logger.info("LambdaTest {} driver created successfully", browser);
            return driver;

        } catch (Exception e) {
            logger.error("Error creating LambdaTest driver: {}", e.getMessage());
            throw new RuntimeException("Failed to create LambdaTest driver", e);
        }
    }

    /**
     * Create Sauce Labs WebDriver
     */
    private static WebDriver createSauceLabsDriver(String browser) {
        try {
            String username = ConfigReader.getProperty("saucelabs.username");
            String accessKey = ConfigReader.getProperty("saucelabs.accesskey");

            if (username == null || accessKey == null) {
                throw new RuntimeException("Sauce Labs credentials not found in config.properties");
            }

            String hubUrl = String.format("https://%s:%s@ondemand.us-west-1.saucelabs.com:443/wd/hub", username, accessKey);

            DesiredCapabilities capabilities = new DesiredCapabilities();
            capabilities.setCapability("browserName", browser);
            capabilities.setCapability("browserVersion", ConfigReader.getProperty("browser.version", "latest"));
            capabilities.setCapability("platformName", ConfigReader.getProperty("platform.name", "macOS 12"));

            // Sauce Labs specific options
            Map<String, Object> sauceOptions = new HashMap<>();
            sauceOptions.put("name", "Test Session - " + browser);
            sauceOptions.put("build", ConfigReader.getProperty("build.name", "Build-" + System.currentTimeMillis()));
            sauceOptions.put("tags", java.util.Arrays.asList("selenium", "automation", "java11"));
            sauceOptions.put("recordVideo", true);
            sauceOptions.put("recordScreenshots", true);
            sauceOptions.put("extendedDebugging", true);

            capabilities.setCapability("sauce:options", sauceOptions);

            WebDriver driver = new RemoteWebDriver(new URL(hubUrl), capabilities);
            logger.info("Sauce Labs {} driver created successfully", browser);
            return driver;

        } catch (Exception e) {
            logger.error("Error creating Sauce Labs driver: {}", e.getMessage());
            throw new RuntimeException("Failed to create Sauce Labs driver", e);
        }
    }

    /**
     * Create AWS Device Farm WebDriver
     */
    private static WebDriver createAWSDeviceFarmDriver(String browser) {
        try {
            String hubUrl = ConfigReader.getProperty("aws.devicefarm.url");

            if (hubUrl == null) {
                throw new RuntimeException("AWS Device Farm URL not found in config.properties");
            }

            DesiredCapabilities capabilities = new DesiredCapabilities();
            capabilities.setBrowserName(browser);

            switch (browser.toLowerCase()) {
                case "chrome":
                    capabilities.setCapability(ChromeOptions.CAPABILITY, getChromeOptions());
                    break;
                case "firefox":
                    capabilities.setCapability(FirefoxOptions.FIREFOX_OPTIONS, getFirefoxOptions());
                    break;
                default:
                    throw new IllegalArgumentException("Browser not supported for AWS Device Farm: " + browser);
            }

            WebDriver driver = new RemoteWebDriver(new URL(hubUrl), capabilities);
            logger.info("AWS Device Farm {} driver created successfully", browser);
            return driver;

        } catch (Exception e) {
            logger.error("Error creating AWS Device Farm driver: {}", e.getMessage());
            throw new RuntimeException("Failed to create AWS Device Farm driver", e);
        }
    }

    /**
     * Get Chrome options optimized for different execution modes
     */
    private static ChromeOptions getChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // Common options
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-web-security");
        options.addArguments("--disable-features=VizDisplayCompositor");

        // macOS specific options
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");

        // Headless mode
        if (Boolean.parseBoolean(ConfigReader.getProperty("headless", "false"))) {
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
        }

        // Performance options
        options.addArguments("--memory-pressure-off");
        options.addArguments("--max_old_space_size=4096");

        return options;
    }

    /**
     * Get Firefox options
     */
    private static FirefoxOptions getFirefoxOptions() {
        FirefoxOptions options = new FirefoxOptions();

        if (Boolean.parseBoolean(ConfigReader.getProperty("headless", "false"))) {
            options.addArguments("--headless");
        }

        // Performance preferences
        options.addPreference("dom.webnotifications.enabled", false);
        options.addPreference("media.volume_scale", "0.0");

        return options;
    }

    /**
     * Get Edge options
     */
    private static EdgeOptions getEdgeOptions() {
        EdgeOptions options = new EdgeOptions();

        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        if (Boolean.parseBoolean(ConfigReader.getProperty("headless", "false"))) {
            options.addArguments("--headless");
        }

        return options;
    }

    /**
     * Get Safari options (macOS specific)
     */
    private static SafariOptions getSafariOptions() {
        SafariOptions options = new SafariOptions();

        // Safari specific settings
        options.setAutomaticInspection(false);
        options.setAutomaticProfiling(false);

        return options;
    }

    /**
     * Configure driver with common settings
     */
    private static void configureDriver(WebDriver driver) {
        // Window management
        driver.manage().window().maximize();

        // Timeouts
        int implicitWait = Integer.parseInt(ConfigReader.getProperty("implicit.wait", "10"));
        int pageLoadTimeout = Integer.parseInt(ConfigReader.getProperty("page.load.timeout", "30"));

        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitWait));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pageLoadTimeout));

        logger.info("Driver configured with timeouts - Implicit: {}s, Page Load: {}s", implicitWait, pageLoadTimeout);
    }

    /**
     * Get current driver instance
     */
    public static WebDriver getCurrentDriver() {
        return driverThreadLocal.get();
    }

    /**
     * Quit driver and cleanup
     */
    public static void quitDriver() {
        WebDriver driver = driverThreadLocal.get();
        if (driver != null) {
            try {
                driver.quit();
                logger.info("Driver quit successfully");
            } catch (Exception e) {
                logger.warn("Error while quitting driver: {}", e.getMessage());
            } finally {
                driverThreadLocal.remove();
            }
        }
    }

    /**
     * Mark test status for cloud platforms
     */
    public static void markTestStatus(String status, String reason) {
        WebDriver driver = getCurrentDriver();
        if (driver instanceof RemoteWebDriver) {
            String executionMode = ConfigReader.getProperty("execution.mode", "local");

            try {
                switch (executionMode.toLowerCase()) {
                    case "browserstack":
                        ((RemoteWebDriver) driver).executeScript(
                                String.format("browserstack_executor: {\"action\": \"setSessionStatus\", \"arguments\": {\"status\":\"%s\", \"reason\": \"%s\"}}",
                                        status, reason)
                        );
                        break;

                    case "lambdatest":
                        ((RemoteWebDriver) driver).executeScript(
                                String.format("lambda-status=%s", status)
                        );
                        break;

                    case "saucelabs":
                        ((RemoteWebDriver) driver).executeScript(
                                String.format("sauce:job-result=%s", status)
                        );
                        break;
                }

                logger.info("Test status marked as {} for {} platform", status, executionMode);

            } catch (Exception e) {
                logger.warn("Failed to mark test status: {}", e.getMessage());
            }
        }
    }
}
