package listeners;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriverException;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import utils.ConfigReader;

/**
 * Retries a failed test a limited number of times, but only when the failure looks
 * like environment flakiness rather than a real defect.
 *
 * <p>The distinction matters. A retry analyzer that re-runs everything until it passes
 * turns a genuine regression into a green build - the worst possible outcome for a test
 * suite. So an {@link AssertionError} is never retried: it means the application
 * produced a different result, which is exactly what the test exists to catch.</p>
 *
 * <p>{@link WebDriverException} and its subclasses - timeouts, stale elements,
 * element-not-interactable, browser crashes - are retried, because on a slow or loaded
 * environment they say more about the environment than the application.</p>
 *
 * <p>Attempt count comes from {@code retry.count} (default 1).</p>
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger logger = LogManager.getLogger(RetryAnalyzer.class);

    private final int maxRetries = ConfigReader.getPropertyAsInt("retry.count", 1);
    private int attempts = 0;

    @Override
    public boolean retry(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        Throwable failure = result.getThrowable();

        if (!isRetryable(failure)) {
            logger.info("Not retrying {} - {} is a real failure, not flakiness",
                    testName, failure == null ? "unknown" : failure.getClass().getSimpleName());
            return false;
        }

        if (attempts >= maxRetries) {
            logger.warn("{} failed after {} retry attempt(s) - giving up", testName, maxRetries);
            return false;
        }

        attempts++;
        logger.warn("Retrying {} (attempt {} of {}) after {}",
                testName, attempts, maxRetries, failure.getClass().getSimpleName());
        return true;
    }

    /**
     * @return true when the failure is the kind a re-run might legitimately clear
     */
    private boolean isRetryable(Throwable failure) {
        if (failure == null) {
            return false;
        }
        // The application said something different from what we expected. That is a
        // finding, not a flake - re-running it would only hide the result.
        if (failure instanceof AssertionError) {
            return false;
        }
        return failure instanceof WebDriverException;
    }
}
