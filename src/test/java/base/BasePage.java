package base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.interactions.Actions;
import utils.UnifiedDriverManager;

import java.time.Duration;
import java.util.List;

/**
 * Base Page class containing common web element operations
 * All page classes should extend this class
 */
public abstract class BasePage{

    protected WebDriver driver;
    protected WebDriverWait wait;
    protected Actions actions;
    protected JavascriptExecutor jsExecutor;
    private static final Logger logger = LogManager.getLogger(BasePage.class);

    /**
     * Constructor to initialize driver and other utilities
     */
    public BasePage() {
        this.driver = UnifiedDriverManager.getCurrentDriver();
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        this.actions = new Actions(driver);
        this.jsExecutor = (JavascriptExecutor) driver;
        PageFactory.initElements(driver, this);
    }




    /**
     * Wait for element to be visible
     * @param element WebElement to wait for
     * @return WebElement when visible
     */
    protected WebElement waitForElementVisible(WebElement element) {
        return wait.until(ExpectedConditions.visibilityOf(element));
    }

    /**
     * Wait for element to be clickable
     * @param element WebElement to wait for
     * @return WebElement when clickable
     */
    protected WebElement waitForElementClickable(WebElement element) {
        return wait.until(ExpectedConditions.elementToBeClickable(element));
    }

    /**
     * Wait for element to be present
     * @param locator By locator
     * @return WebElement when present
     */
    protected WebElement waitForElementPresent(By locator) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    /**
     * Click on element with wait
     * @param element WebElement to click
     */
    protected void clickElement(WebElement element) {
        waitForElementClickable(element).click();
        logger.info("Clicked on element: {}", element.toString());
    }

    /**
     * Type text into element
     * @param element WebElement to type into
     * @param text Text to type
     */
    protected void typeText(WebElement element, String text) {
        waitForElementVisible(element);
        element.clear();
        element.sendKeys(text);
        logger.info("Typed text '{}' into element", text);
    }

    /**
     * Get text from element
     * @param element WebElement to get text from
     * @return Element text
     */
    protected String getText(WebElement element) {
        String text = waitForElementVisible(element).getText();
        logger.info("Retrieved text '{}' from element", text);
        return text;
    }

    /**
     * Check if element is displayed
     * @param element WebElement to check
     * @return true if displayed, false otherwise
     */
    protected boolean isElementDisplayed(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if element is enabled
     * @param element WebElement to check
     * @return true if enabled, false otherwise
     */
    protected boolean isElementEnabled(WebElement element) {
        try {
            return element.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Select option from dropdown by visible text
     * @param element Select element
     * @param text Visible text to select
     */
    protected void selectByVisibleText(WebElement element, String text) {
        Select select = new Select(waitForElementVisible(element));
        select.selectByVisibleText(text);
        logger.info("Selected option '{}' from dropdown", text);
    }

    /**
     * Select option from dropdown by value
     * @param element Select element
     * @param value Value to select
     */
    protected void selectByValue(WebElement element, String value) {
        Select select = new Select(waitForElementVisible(element));
        select.selectByValue(value);
        logger.info("Selected option with value '{}' from dropdown", value);
    }

    /**
     * Select option from dropdown by index
     * @param element Select element
     * @param index Index to select
     */
    protected void selectByIndex(WebElement element, int index) {
        Select select = new Select(waitForElementVisible(element));
        select.selectByIndex(index);
        logger.info("Selected option at index '{}' from dropdown", index);
    }

    /**
     * Scroll to element
     * @param element WebElement to scroll to
     */
    protected void scrollToElement(WebElement element) {
        jsExecutor.executeScript("arguments[0].scrollIntoView(true);", element);
        logger.info("Scrolled to element");
    }

    /**
     * Scroll to top of page
     */
    protected void scrollToTop() {
        jsExecutor.executeScript("window.scrollTo(0, 0);");
        logger.info("Scrolled to top of page");
    }

    /**
     * Scroll to bottom of page
     */
    protected void scrollToBottom() {
        jsExecutor.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        logger.info("Scrolled to bottom of page");
    }

    /**
     * Hover over element
     * @param element WebElement to hover over
     */
    protected void hoverOverElement(WebElement element) {
        actions.moveToElement(waitForElementVisible(element)).perform();
        logger.info("Hovered over element");
    }

    /**
     * Double click on element
     * @param element WebElement to double click
     */
    protected void doubleClickElement(WebElement element) {
        actions.doubleClick(waitForElementClickable(element)).perform();
        logger.info("Double clicked on element");
    }

    /**
     * Right click on element
     * @param element WebElement to right click
     */
    protected void rightClickElement(WebElement element) {
        actions.contextClick(waitForElementClickable(element)).perform();
        logger.info("Right clicked on element");
    }

    /**
     * Drag and drop element
     * @param sourceElement Source element
     * @param targetElement Target element
     */
    protected void dragAndDropElement(WebElement sourceElement, WebElement targetElement) {
        actions.dragAndDrop(sourceElement, targetElement).perform();
        logger.info("Dragged and dropped element");
    }

    /**
     * Get page title
     * @return Page title
     */
    protected String getPageTitle() {
        return driver.getTitle();
    }

    /**
     * Get current URL
     * @return Current URL
     */
    protected String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    /**
     * Navigate to URL
     * @param url URL to navigate to
     */
    protected void navigateTo(String url) {
        driver.get(url);
        logger.info("Navigated to URL: {}", url);
    }

    /**
     * Refresh page
     */
    protected void refreshPage() {
        driver.navigate().refresh();
        logger.info("Page refreshed");
    }

    /**
     * Navigate back
     */
    protected void navigateBack() {
        driver.navigate().back();
        logger.info("Navigated back");
    }

    /**
     * Navigate forward
     */
    protected void navigateForward() {
        driver.navigate().forward();
        logger.info("Navigated forward");
    }

    /**
     * Get all elements matching locator
     * @param locator By locator
     * @return List of WebElements
     */
    protected List<WebElement> getElements(By locator) {
        return driver.findElements(locator);
    }

    /**
     * Get element attribute
     * @param element WebElement
     * @param attributeName Attribute name
     * @return Attribute value
     */
    protected String getElementAttribute(WebElement element, String attributeName) {
        String attributeValue = element.getAttribute(attributeName);
        logger.info("Retrieved attribute '{}' with value '{}' from element", attributeName, attributeValue);
        return attributeValue;
    }

    /**
     * Wait for page to load completely
     */
    protected void waitForPageLoad() {
        wait.until(webDriver -> jsExecutor.executeScript("return document.readyState").equals("complete"));
        logger.info("Page loaded completely");
    }

    /**
     * Switch to frame by index
     * @param frameIndex Frame index
     */
    protected void switchToFrame(int frameIndex) {
        driver.switchTo().frame(frameIndex);
        logger.info("Switched to frame with index: {}", frameIndex);
    }

    /**
     * Switch to frame by name or id
     * @param frameNameOrId Frame name or id
     */
    protected void switchToFrame(String frameNameOrId) {
        driver.switchTo().frame(frameNameOrId);
        logger.info("Switched to frame: {}", frameNameOrId);
    }

    /**
     * Switch to frame by WebElement
     * @param frameElement Frame WebElement
     */
    protected void switchToFrame(WebElement frameElement) {
        driver.switchTo().frame(frameElement);
        logger.info("Switched to frame element");
    }

    /**
     * Switch to a frame starting from the top-level document.
     *
     * <p>Switching into a frame is resolved relative to the current browsing context,
     * so a page object that is already inside a frame cannot reach another one - or
     * re-enter its own. Returning to the default content first makes frame entry
     * position-independent, and therefore safe to call from any step of a flow.</p>
     *
     * <p>Prefer this over {@link #switchToFrame(WebElement)} whenever more than one
     * method in a flow needs the same frame.</p>
     *
     * @param frameElement Frame WebElement, located from the top-level document
     */
    protected void switchToFrameFromRoot(WebElement frameElement) {
        switchToDefaultContent();
        switchToFrame(frameElement);
    }

    /**
     * Switch to default content
     */
    protected void switchToDefaultContent() {
        driver.switchTo().defaultContent();
        logger.info("Switched to default content");
    }

    /**
     * Accept alert
     */
    protected void acceptAlert() {
        driver.switchTo().alert().accept();
        logger.info("Alert accepted");
    }

    /**
     * Dismiss alert
     */
    protected void dismissAlert() {
        driver.switchTo().alert().dismiss();
        logger.info("Alert dismissed");
    }

    /**
     * Get alert text
     * @return Alert text
     */
    protected String getAlertText() {
        String alertText = driver.switchTo().alert().getText();
        logger.info("Alert text: {}", alertText);
        return alertText;
    }

    /**
     * Type text in alert
     * @param text Text to type
     */
    protected void typeInAlert(String text) {
        driver.switchTo().alert().sendKeys(text);
        logger.info("Typed '{}' in alert", text);
    }

    /**
     * Switch to window by title
     * @param windowTitle Window title
     */
    protected void switchToWindowByTitle(String windowTitle) {
        String currentWindow = driver.getWindowHandle();
        for (String windowHandle : driver.getWindowHandles()) {
            driver.switchTo().window(windowHandle);
            if (driver.getTitle().equals(windowTitle)) {
                logger.info("Switched to window with title: {}", windowTitle);
                return;
            }
        }
        driver.switchTo().window(currentWindow);
        logger.warn("Window with title '{}' not found", windowTitle);
    }

    /**
     * Close current window and switch to main window
     */
    protected void closeCurrentWindow() {
        String mainWindow = driver.getWindowHandles().iterator().next();
        driver.close();
        driver.switchTo().window(mainWindow);
        logger.info("Closed current window and switched to main window");
    }

    /**
     * Take screenshot
     * @return Screenshot as byte array
     */
    protected byte[] takeScreenshot() {
        return ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
    }
}