package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes a machine-readable bundle describing a test failure.
 *
 * <p>The bundle is what an automated diagnosis step reads instead of re-running the
 * test. A screenshot shows <em>that</em> something broke; the page source shows
 * <em>what to change it to</em>, which is why the DOM is captured alongside it.
 * Everything here must be collected while the driver is still alive.</p>
 *
 * <p>Produces, under {@code failures.path}:</p>
 * <ul>
 *   <li>{@code <test>_<timestamp>.json} - structured failure context</li>
 *   <li>{@code <test>_<timestamp>.html} - page source at the moment of failure</li>
 * </ul>
 */
public final class FailureReporter {

    private static final Logger logger = LogManager.getLogger(FailureReporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");

    /** Selenium's JSON-style locator, e.g. {"method":"css selector","selector":"#collabIframe"} */
    private static final Pattern JSON_LOCATOR =
            Pattern.compile("\\{\"method\":\"([^\"]+)\",\"selector\":\"([^\"]+)\"}");

    /** PageFactory's rendered locator, e.g. Proxy element for: ... 'By.id: acceptCookiesBtn' */
    private static final Pattern BY_LOCATOR =
            Pattern.compile("By\\.(\\w+):\\s*([^'\\n]+?)(?:'|\\s*\\(|$)");

    private static final Pattern HEAD_TAG = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE);

    /** A resolved element, e.g. [[ChromeDriver: chrome on mac (...)] -> xpath: //button[...]] */
    private static final Pattern ARROW_LOCATOR =
            Pattern.compile("->\\s*([\\w ]+):\\s*(.+?)\\]\\s*(?:\\(tried for|$)");

    private FailureReporter() {
    }

    /**
     * Collects and writes the failure bundle.
     *
     * @param driver         live driver - must not have been quit yet
     * @param testName       failing test method name
     * @param error          the throwable TestNG recorded
     * @param screenshotPath absolute path to the failure screenshot, or null
     * @return absolute path to the JSON bundle, or null if it could not be written
     */
    public static String capture(WebDriver driver, String testName, Throwable error, String screenshotPath) {
        try {
            String stamp = LocalDateTime.now().format(STAMP);
            Path directory = Paths.get(ConfigReader.getProperty("failures.path", "test-output/failures"));
            Files.createDirectories(directory);

            String baseName = testName + "_" + stamp;

            ObjectNode root = MAPPER.createObjectNode();
            root.put("testName", testName);
            root.put("timestamp", LocalDateTime.now().toString());

            root.set("environment", environmentNode());
            root.set("failure", failureNode(error));
            root.set("locator", locatorNode(error));
            root.set("page", pageNode(driver, directory, baseName));

            root.put("screenshotPath", screenshotPath);
            root.set("recentLogs", recentLogsNode());

            Path target = directory.resolve(baseName + ".json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), root);

            logger.info("Failure bundle written: {}", target.toAbsolutePath());

            cleanOldBundles(directory, ConfigReader.getPropertyAsInt("failures.max.bundles", 5));
            return target.toAbsolutePath().toString();

        } catch (Exception e) {
            // Diagnostics must never mask the real test failure
            logger.warn("Could not write failure bundle: {}", e.getMessage());
            return null;
        }
    }

    private static ObjectNode environmentNode() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("environment", ConfigReader.getProperty("environment", "UNKNOWN"));
        node.put("executionMode", ConfigReader.getProperty("execution.mode", "local"));
        node.put("browser", ConfigReader.getProperty("browser", "chrome"));
        node.put("configFile", ConfigReader.getCurrentConfigFile());
        node.put("baseUrl", ConfigReader.getProperty("base.url", "unknown"));
        return node;
    }

    private static ObjectNode failureNode(Throwable error) {
        ObjectNode node = MAPPER.createObjectNode();
        if (error == null) {
            return node;
        }
        node.put("type", error.getClass().getName());
        node.put("message", error.getMessage());

        // Only frames in our own packages - Selenium/TestNG internals add noise, not signal
        ArrayNode frames = MAPPER.createArrayNode();
        for (StackTraceElement frame : error.getStackTrace()) {
            String rendered = frame.toString();
            if (rendered.startsWith("pages.") || rendered.startsWith("base.") || rendered.startsWith("tests.")) {
                frames.add(rendered);
            }
        }
        node.set("projectStackFrames", frames);
        return node;
    }

    /**
     * Extracts the locator that failed, so a diagnosis step does not have to parse
     * free-form Selenium prose. Returns an empty node when no locator is identifiable.
     */
    private static ObjectNode locatorNode(Throwable error) {
        ObjectNode node = MAPPER.createObjectNode();
        if (error == null || error.getMessage() == null) {
            return node;
        }
        String message = error.getMessage();

        Matcher json = JSON_LOCATOR.matcher(message);
        if (json.find()) {
            node.put("strategy", json.group(1));
            node.put("value", json.group(2));
            return node;
        }

        Matcher by = BY_LOCATOR.matcher(message);
        if (by.find()) {
            node.put("strategy", by.group(1));
            node.put("value", by.group(2).trim());
            return node;
        }

        Matcher arrow = ARROW_LOCATOR.matcher(message);
        if (arrow.find()) {
            node.put("strategy", arrow.group(1).trim());
            node.put("value", arrow.group(2).trim());
        }
        return node;
    }

    /**
     * Records the page context, capturing the DOM of both the browsing context the test
     * failed in and the top-level document.
     *
     * <p>{@code getPageSource()} returns only the <em>current</em> browsing context. A test
     * that fails inside an iframe therefore yields just that frame's DOM, which is rarely
     * what a locator fix needs - so the top-level document is captured as well.</p>
     */
    private static ObjectNode pageNode(WebDriver driver, Path directory, String baseName) {
        ObjectNode node = MAPPER.createObjectNode();

        String url = null;
        try {
            url = driver.getCurrentUrl();
            node.put("url", url);
            node.put("title", driver.getTitle());
        } catch (Exception e) {
            logger.debug("Could not read page URL/title: {}", e.getMessage());
        }

        // The context the test was actually in when it failed
        String contextSource = safePageSource(driver);

        // Step back out to the top-level document. Safe here: the driver is about to be quit.
        String topSource = null;
        try {
            driver.switchTo().defaultContent();
            topSource = safePageSource(driver);
        } catch (Exception e) {
            logger.debug("Could not switch to default content: {}", e.getMessage());
        }

        boolean insideFrame = contextSource != null && topSource != null && !contextSource.equals(topSource);
        node.put("capturedInsideFrame", insideFrame);

        if (topSource != null) {
            node.put("pageSourcePath", writeHtml(directory, baseName + ".html", topSource, url));
        }
        if (insideFrame) {
            node.put("framePageSourcePath", writeHtml(directory, baseName + "_frame.html", contextSource, url));
        }
        return node;
    }

    private static String safePageSource(WebDriver driver) {
        try {
            return driver.getPageSource();
        } catch (Exception e) {
            logger.debug("Could not read page source: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Writes a captured DOM, injecting a {@code <base>} tag so the file renders with its
     * real styling when opened from disk instead of 404-ing every relative asset.
     */
    private static String writeHtml(Path directory, String fileName, String source, String url) {
        try {
            Path target = directory.resolve(fileName);
            Files.write(target, withBaseHref(source, url).getBytes(StandardCharsets.UTF_8));
            logger.info("Failure page source saved: {}", target.toAbsolutePath());
            return target.toAbsolutePath().toString();
        } catch (Exception e) {
            logger.warn("Could not write page source {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    private static String withBaseHref(String source, String url) {
        if (url == null || source == null) {
            return source;
        }
        Matcher head = HEAD_TAG.matcher(source);
        if (!head.find()) {
            return source;
        }
        String injected = "<!-- base tag added by FailureReporter so relative assets resolve -->"
                + "<base href=\"" + url + "\">";
        return source.substring(0, head.end()) + injected + source.substring(head.end());
    }

    /**
     * Tail of the execution log - the action sequence leading up to the failure,
     * which is what distinguishes a stale locator from a wrong page.
     */
    private static ArrayNode recentLogsNode() {
        ArrayNode node = MAPPER.createArrayNode();
        int wanted = ConfigReader.getPropertyAsInt("failure.log.tail.lines", 80);
        Path log = Paths.get("test-output/logs/automation.log");

        if (!Files.exists(log)) {
            return node;
        }
        try (Stream<String> lines = Files.lines(log, StandardCharsets.UTF_8)) {
            Deque<String> tail = new ArrayDeque<>(wanted);
            lines.forEach(line -> {
                if (tail.size() == wanted) {
                    tail.removeFirst();
                }
                tail.addLast(line);
            });
            tail.forEach(node::add);
        } catch (IOException e) {
            logger.debug("Could not read execution log: {}", e.getMessage());
        }
        return node;
    }

    /**
     * Keeps only the newest bundles, deleting every file belonging to older ones.
     *
     * <p>Pruning is done per bundle rather than per file type on purpose. One failure
     * writes a {@code .json}, a {@code .html}, optionally a {@code _frame.html} and later
     * a {@code _diagnosis.md}; trimming each extension independently would leave JSON
     * bundles whose page source had already been deleted, which cannot be diagnosed.</p>
     *
     * @param directory the failures directory
     * @param keep      how many bundles to retain, newest first
     */
    private static void cleanOldBundles(Path directory, int keep) {
        try {
            File[] bundles = directory.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            if (bundles == null || bundles.length <= keep) {
                return;
            }
            // Newest first, so everything from index `keep` onwards is surplus
            Arrays.sort(bundles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            int removed = 0;
            for (int i = keep; i < bundles.length; i++) {
                String baseName = bundles[i].getName().substring(0, bundles[i].getName().length() - ".json".length());
                File[] related = directory.toFile().listFiles((dir, name) -> name.startsWith(baseName));
                if (related == null) {
                    continue;
                }
                for (File file : related) {
                    if (file.delete()) {
                        removed++;
                    } else {
                        logger.warn("Failed to delete old failure file: {}", file.getName());
                    }
                }
            }
            if (removed > 0) {
                logger.info("Cleaned up {} file(s) from old failure bundles, keeping {} latest",
                        removed, keep);
            }
        } catch (Exception e) {
            logger.warn("Could not clean old failure bundles: {}", e.getMessage());
        }
    }
}
