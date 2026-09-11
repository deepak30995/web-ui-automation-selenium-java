package pages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import base.BasePage;

public class LoginPage extends BasePage {

    private static final Logger logger = LogManager.getLogger(LoginPage.class);

    @FindBy(id = "acceptCookiesBtn")
    private WebElement acceptCookiesButton;

    @FindBy(id = "el-login-btn")
    private WebElement loginButtonHomePage;

    @FindBy(id = "microfe-popup-login")
    private WebElement loginPopupIframe;

    @FindBy(xpath = "//div[@class='signup_lable_nr1k1']//strong[text()='Log in']")
    private WebElement loginButtonOnCreateAccountPopup;

    @FindBy(xpath = "//button[@title='Continue with email']")
    private WebElement continueWithEmailButton;

    @FindBy(id = "input-email")
    private WebElement emailInputField;

    @FindBy(id = "input-password")
    private WebElement passwordInputField;

    @FindBy(xpath = "//button[text()='Next']")
    private WebElement nextButton;

    @FindBy(id = "collabIframe")
    private WebElement myCoursesIframe;

    @FindBy(xpath = "//h5[contains(@class,'text-title5  text-black font-semibold')]")
    private WebElement activeCourseHeaderText;

    public LoginPage() {
        logger.info("LoginPage initialized");
    }

    /**
     * Dismisses the cookie consent banner when it is shown.
     * The banner is not always rendered (varies by session/region), so its absence
     * is not a failure - only a genuine click problem is worth reporting.
     */
    public void clickOnAcceptCookies() {
        if (!isElementDisplayed(acceptCookiesButton)) {
            logger.info("Accept cookies button not present on home page - skipping");
            return;
        }
        try {
            clickElement(acceptCookiesButton);
            logger.info("Clicked on accept cookies button on home page");
        } catch (Exception e) {
            logger.warn("Could not dismiss cookie banner, continuing with login flow: {}", e.getMessage());
        }
    }

    public void clickLoginButtonFromHomePage() {
        clickElement(loginButtonHomePage);
        logger.info("Clicked on 'Login' button from home page");
    }

    /**
     * Switches into the login popup iframe and opens the login form from the
     * sign-up variant of the popup. Not part of the current flow - the site now
     * presents the login form directly - but kept for setups that still show it.
     */
    public void clickLoginButtonOnPopup() {
        switchToFrameFromRoot(loginPopupIframe);
        clickElement(loginButtonOnCreateAccountPopup);
        logger.info("Clicked on 'Log in' button in popup");
    }

    public void clickContinueWithEmailButton() {
        switchToFrameFromRoot(loginPopupIframe);
        clickElement(continueWithEmailButton);
        logger.info("Clicked on 'Continue with email' button");
    }

    public void enterEmail(String email) {
        typeText(emailInputField, email);
        logger.info("Entered email: {}", email);
    }

    public void enterPassword(String password) {
        typeText(passwordInputField, password);
        logger.info("Entered password (masked for security)");
    }

    public void clickNextButton() {
        clickElement(nextButton);
        logger.info("Clicked on 'Next' button");
    }

    /**
     * Leaves the login popup and enters the My Courses iframe.
     * Kept separate from the click actions so frame navigation is explicit.
     */
    public void switchToMyCoursesFrame() {
        switchToFrameFromRoot(myCoursesIframe);
        logger.info("Switched to 'My Courses' iframe");
    }

    public String getActiveCourseHeaderText() {
        switchToDefaultContent();
        String headerText = getText(activeCourseHeaderText);
        logger.info("Retrieved 'Active Courses' header text: {}", headerText);
        return headerText;
    }

    public void returnToMainContent() {
        switchToDefaultContent();
    }
}
