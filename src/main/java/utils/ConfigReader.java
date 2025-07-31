package utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Enhanced ConfigReader with environment variable support
 */
public class ConfigReader {

    private static final Logger logger = LogManager.getLogger(ConfigReader.class);
    private static Properties properties;
    private static String currentConfigFile = "config.properties";

    static {
        loadProperties();
    }

    /**
     * Load properties based on environment or use default
     */
    private static void loadProperties() {
        try {
            // Check if specific environment is requested
            String testEnv = System.getProperty("test.env");
            if (testEnv != null && !testEnv.isEmpty()) {
                currentConfigFile = "config-" + testEnv + ".properties";
                logger.info("Loading configuration for environment: {}", testEnv);
            }

            properties = new Properties();
            InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream(currentConfigFile);

            if (input == null) {
                logger.warn("Configuration file '{}' not found, falling back to default", currentConfigFile);
                currentConfigFile = "config.properties";
                input = ConfigReader.class.getClassLoader().getResourceAsStream(currentConfigFile);
            }

            if (input == null) {
                logger.error("Default config.properties file not found in classpath");
                throw new IOException("config.properties file not found in classpath");
            }

            properties.load(input);
            input.close();

            logger.info("Configuration loaded from: {}", currentConfigFile);
            logger.info("Total properties loaded: {}", properties.size());

        } catch (IOException e) {
            logger.error("Error loading configuration: {}", e.getMessage());
            properties = new Properties();
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * Get property value with environment variable support
     * @param key Property key
     * @return Property value, environment variable value, or null
     */
    public static String getProperty(String key) {
        if (properties == null) {
            loadProperties();
        }

        // First check system properties (command line -D arguments)
        String systemValue = System.getProperty(key);
        if (systemValue != null) {
            logger.debug("Retrieved system property: {} = {}", key, systemValue);
            return systemValue;
        }

        // Then check properties file
        String value = properties.getProperty(key);

        // If value contains environment variable placeholder, resolve it
        if (value != null && value.contains("${")) {
            value = resolveEnvironmentVariables(value);
        }

        if (value == null) {
            logger.warn("Property not found: {}", key);
        } else {
            logger.debug("Retrieved property: {} = {}", key, maskSensitiveValue(key, value));
        }

        return value;
    }

    /**
     * Get property with default value and environment variable support
     * @param key Property key
     * @param defaultValue Default value if key not found
     * @return Property value, environment variable value, or default value
     */
    public static String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Resolve environment variables in property values
     * @param value Property value that may contain ${ENV_VAR} placeholders
     * @return Resolved value with environment variables substituted
     */
    private static String resolveEnvironmentVariables(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        String result = value;

        // Pattern to match ${VARIABLE_NAME}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(value);

        while (matcher.find()) {
            String envVarName = matcher.group(1);
            String envVarValue = System.getenv(envVarName);

            if (envVarValue != null) {
                result = result.replace("${" + envVarName + "}", envVarValue);
                logger.debug("Resolved environment variable: {} = {}", envVarName, maskSensitiveValue(envVarName, envVarValue));
            } else {
                logger.warn("Environment variable not found: {}", envVarName);
                // Keep the placeholder if environment variable is not found
            }
        }

        return result;
    }

    /**
     * Mask sensitive values in logs
     * @param key Property key
     * @param value Property value
     * @return Masked value for logging
     */
    private static String maskSensitiveValue(String key, String value) {
        if (key.toLowerCase().contains("password") ||
                key.toLowerCase().contains("key") ||
                key.toLowerCase().contains("secret") ||
                key.toLowerCase().contains("token") ||
                key.toLowerCase().contains("accesskey")) {
            return "***MASKED***";
        }
        return value;
    }

    /**
     * Load specific configuration file
     * @param configFileName Configuration file name
     */
    public static void loadSpecificConfig(String configFileName) {
        currentConfigFile = configFileName;
        loadProperties();
    }

    /**
     * Get current configuration file name
     * @return Current config file name
     */
    public static String getCurrentConfigFile() {
        return currentConfigFile;
    }

    /**
     * Reload properties (useful for testing)
     */
    public static void reloadProperties() {
        logger.info("Reloading configuration...");
        loadProperties();
    }

    // ... (keep all your existing methods: getPropertyAsInt, getPropertyAsBoolean, etc.)

    /**
     * Get property as integer with environment variable support
     */
    public static int getPropertyAsInt(String key, int defaultValue) {
        String value = getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                logger.warn("Property '{}' value '{}' is not a valid integer, using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * Get property as boolean with environment variable support
     */
    public static boolean getPropertyAsBoolean(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value.trim());
        }
        return defaultValue;
    }

    /**
     * Print configuration info (for debugging)
     */
    public static void printConfigInfo() {
        logger.info("=== Configuration Information ===");
        logger.info("Current config file: {}", currentConfigFile);
        logger.info("Test environment: {}", System.getProperty("test.env", "default"));
        logger.info("Execution mode: {}", getProperty("execution.mode", "unknown"));
        logger.info("Browser: {}", getProperty("browser", "unknown"));
        logger.info("Base URL: {}", getProperty("base.url", "unknown"));
        logger.info("=== End Configuration Info ===");
    }
}