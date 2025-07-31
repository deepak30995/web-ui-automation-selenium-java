package pages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import base.BasePage;

public class Login extends BasePage {

    private static final Logger logger = LogManager.getLogger(Login.class);


    @FindBy(id="acceptCookiesBtn")
    WebElement accept_cookies;

    @FindBy(xpath = "//a[text()='Login']")
    WebElement login_button_home_page;

    @FindBy(id="microfe-popup-login")
    WebElement login_popup_iframe;

    @FindBy(xpath = "//div[@class='signup_lable_nr1k1']//strong[text()='Log in']")
    WebElement login_button_on_Create_an_Account_popup;

    @FindBy(xpath = "//button[@title='Continue with email']")
    WebElement ContinueWithEmail_button_on_login_popup;

    @FindBy(id = "input-email")
    WebElement inputEmail_input_field_on_login_popup;

    @FindBy(id="input-password")
    WebElement password_input_field_on_login_popup;

    @FindBy(xpath = "//button[text()='Next']")
    WebElement next_button_on_login_popup;

    @FindBy(id="collabIframe")
    WebElement myCourse_page_iframe;

    @FindBy(xpath = "//div[@id='mycourses']/h3")
    WebElement activeCourse_header_text;

    public Login() {
        super();
        logger.info("pages.Login page initialized");
    }

    public void clickOnAcceptCookies() {
        try {
            waitForElementClickable(accept_cookies);
            //accept_cookies.click();
            clickElement(accept_cookies);
            logger.info("Step 1: Clicked on accept cookies button on home page");
        } catch (Exception e) {
            logger.error("Error clicking accept cookies button on home page: " + e.getMessage());
            throw e;
        }
    }

    public void clickLoginButtonFromHomePage() {
        try {
            waitForElementClickable(login_button_home_page);
            //login_button_home_page.click();
            clickElement(login_button_home_page);
            logger.info("Step 2: Clicked on 'Login' button from home page");
        } catch (Exception e) {
            logger.error("Error clicking login button from home page: " + e.getMessage());
            throw e;
        }
    }


    public void clickLoginButtonOnPopup() {
        try {
            switchToFrame(login_popup_iframe);
            waitForElementClickable(login_button_on_Create_an_Account_popup);
            //login_button_on_Create_an_Account_popup.click();
            clickElement(login_button_on_Create_an_Account_popup);
            logger.info("Step 3: Clicked on 'Log in' button in popup");
        } catch (Exception e) {
            logger.error("Error clicking 'Log in' button in popup: " + e.getMessage());
            throw e;
        }
    }


    public void clickContinueWithEmailButton() {
        try {
            waitForElementClickable(ContinueWithEmail_button_on_login_popup);
            //ContinueWithEmail_button_on_login_popup.click();
            clickElement(ContinueWithEmail_button_on_login_popup);
            logger.info("Step 4: Clicked on 'Continue with email' button");
        } catch (Exception e) {
            logger.error("Error clicking 'Continue with email' button: " + e.getMessage());
            throw e;
        }
    }

    public void enterEmail(String email) {
        try {
            waitForElementVisible(inputEmail_input_field_on_login_popup);
            //inputEmail_input_field_on_login_popup.sendKeys(email);
            typeText(inputEmail_input_field_on_login_popup, email);
            logger.info("Step 5a: Entered email: " + email);
        } catch (Exception e) {
            logger.error("Error entering email: " + e.getMessage());
            throw e;
        }
    }

    public void enterPassword(String password) {
        try {
            waitForElementVisible(password_input_field_on_login_popup);
            //password_input_field_on_login_popup.sendKeys(password);
            typeText(password_input_field_on_login_popup, password);
            logger.info("Step 5b: Entered password (masked for security)");
        } catch (Exception e) {
            logger.error("Error entering password: " + e.getMessage());
            throw e;
        }
    }

    public void clickNextButton() {
        try {
            waitForElementClickable(next_button_on_login_popup);
            //next_button_on_login_popup.click();
            clickElement(next_button_on_login_popup);
            switchToDefaultContent();
            switchToFrame(myCourse_page_iframe);
            logger.info("Step 6: Clicked on 'Next' button");
        } catch (Exception e) {
            logger.error("Error clicking 'Next' button: " + e.getMessage());
            throw e;
        }
    }

    public String getActiveCourseHeaderText() {
        try {
            waitForElementVisible(activeCourse_header_text);
            String headerText = activeCourse_header_text.getText();
            switchToDefaultContent();
            logger.info("Step 7: Retrieved 'Active Courses' header text: " + headerText);
            return headerText;
        } catch (Exception e) {
            logger.error("Error getting 'Active Courses' header text: " + e.getMessage());
            throw e;
        }
    }







}
