package pages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import base.BasePage;
import io.qameta.allure.Step;

public class LoginPage extends BasePage {

    private static final Logger logger = LogManager.getLogger(LoginPage.class);

    @FindBy(id = "acceptCookiesBtn")
    private WebElement acceptCookiesButton;

    @FindBy(id = "el-login-btn")
    private WebElement loginButtonHomePage;

    @FindBy(id = "microfe-popup-login")
    private WebElement loginPopupIframe;

    @FindBy(xpath = "//strong[text()='Log in']")
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

    @FindBy(xpath = "//h5[text()='Courses']")
    private WebElement activeCourseHeaderText;

    @FindBy(xpath = "//iframe[@id='microfe-popup-login']")
    private WebElement loginAuthPageIframe;

    @FindBy(xpath = "//div[contains(@class,'Toastify__toast--error')]//div[@role='alert']")
    private WebElement errorToastMessageForIncorrectCredentials;

    public LoginPage() {
        logger.info("LoginPage initialized");
    }

    /**
     * Dismisses the cookie consent banner when it is shown.
     *
     * <p>The banner is rendered only when the corresponding key is enabled on the
     * organization record, so it is legitimately absent for some orgs. Its absence is
     * therefore expected behaviour, not drift - only a genuine click problem is worth
     * reporting.</p>
     */
    @Step("Dismiss cookie banner if shown")
    public void clickOnAcceptCookies() {
        if (!isElementDisplayedWithin(acceptCookiesButton, 3)) {
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

    @Step("Open login form from home page")
    public void clickLoginButtonFromHomePage() {
        clickElement(loginButtonHomePage);
        logger.info("Clicked on 'Login' button from home page");
    }

    /**
     * Switches from the sign-up form to the login form inside the popup.
     *
     * <p>When the popup opens on "Create an account" rather than the login form, an
     * existing user reaches login through the "Already have an account? Log in" link.
     * Which form the popup opens on varies, so this step is not part of every flow -
     * it is skipped when the login form is presented directly.</p>
     */
    @Step("Switch to login form in popup when user is on the signup form")
    public void clickLoginButtonOnPopup() {
        switchToFrameFromRoot(loginPopupIframe);
        clickElement(loginButtonOnCreateAccountPopup);
        logger.info("Clicked on 'Log in' button in popup");
    }

    @Step("Choose email as login method")
    public void clickContinueWithEmailButton() {
        switchToFrameFromRoot(loginPopupIframe);
        clickElement(continueWithEmailButton);
        logger.info("Clicked on 'Continue with email' button");
    }

    @Step("Enter email: {email}")
    public void enterEmail(String email) {
        typeText(emailInputField, email);
        logger.info("Entered email: {}", email);
    }

    @Step("Enter password")
    public void enterPassword(String password) {
        typeText(passwordInputField, password);
        logger.info("Entered password (masked for security)");
    }

    @Step("Submit login form")
    public void clickNextButton() {
        clickElement(nextButton);
        logger.info("Clicked on 'Next' button");
    }

    /**
     * Leaves the login popup and enters the My Courses iframe.
     *
     * <p>The {@code collabIframe} only exists when the organization has
     * <strong>Legacy View</strong> selected under Settings &rarr; User experience &rarr;
     * Learner's experience. Under Basic or Advanced View the courses page renders in the
     * top-level document and this step must be skipped - its absence is a configuration
     * difference, not a stale locator.</p>
     *
     * <p>Kept separate from the click actions so frame navigation is explicit.</p>
     */
    @Step("Enter My Courses frame (Legacy View only)")
    public void switchToMyCoursesFrame() {
        switchToFrameFromRoot(myCoursesIframe);
        logger.info("Switched to 'My Courses' iframe");
    }

    @Step("Read courses page heading")
    public String getActiveCourseHeaderText() {
        switchToDefaultContent();
        String headerText = getText(activeCourseHeaderText);
        logger.info("Retrieved 'Active Courses' header text: {}", headerText);
        return headerText;
    }


    @Step("Read login error message")
    public String getErrorMessageForIncorrectCredentials()
    {
        switchToFrameFromRoot(loginAuthPageIframe);
        String errorMessage = getText(errorToastMessageForIncorrectCredentials);
        return errorMessage;
    }
}
