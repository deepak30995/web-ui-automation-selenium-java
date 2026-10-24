package tests;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.LoginPage;
import utils.ConfigReader;

public class LoginTest extends BaseTest {

    @Test(priority = 1, description = "Verify complete login flow with valid credentials", groups = {"smoke", "regression", "login"})
    public void completesLoginWithValidCredentials() {

        LoginPage loginPage = new LoginPage();

        // Test data from config.properties
        String testEmail = ConfigReader.getForTarget("test.user.email");
        String testPassword = ConfigReader.getForTarget("test.user.password");
        String expectedHeaderText = ConfigReader.getProperty("test.user.active.course.page.header.name");

        getTest().info("Starting complete login flow test");

        // Dismiss the cookie banner if it is shown
        loginPage.clickOnAcceptCookies();
        getTest().pass("Handled cookie consent banner on home page");

        // Open the login form from the home page
        loginPage.clickLoginButtonFromHomePage();
        getTest().pass("Clicked login button from home page");

        // Disabled - the current website setup presents the login form directly,
        // so the sign-up popup variant (and its iframe switch) no longer applies.
        // loginPage.clickLoginButtonOnPopup();

        // Choose email as the login method
        loginPage.clickContinueWithEmailButton();
        getTest().pass("Clicked continue with email button");

        // Supply credentials
        loginPage.enterEmail(testEmail);
        getTest().pass("Entered email");

        loginPage.enterPassword(testPassword);
        getTest().pass("Entered password");

        // Submit and move into the My Courses view
        loginPage.clickNextButton();
        //loginPage.switchToMyCoursesFrame();
        getTest().pass("Submitted login form");

        // Verify the user landed on the Active Courses page
        String actualHeaderText = loginPage.getActiveCourseHeaderText();

        Assert.assertNotNull(actualHeaderText, "Header text should not be null");
        Assert.assertEquals(actualHeaderText, expectedHeaderText, "Header text should match expected value");
        getTest().pass("Verified header text: " + expectedHeaderText);
    }

    @Test(priority = 2, description = "Verify an error is shown for incorrect email and password credentials that do not match an account", groups = {"regression", "login"})
    public void showsErrorForIncorrectEmailAndPassword()
    {

        LoginPage loginPage = new LoginPage();
        String toastMessage = ConfigReader.getProperty("incorrect.email.and.incorrect.password.error.toast.message");
        String incorrectEmail = ConfigReader.getForTarget("test.incorrect.email");
        loginPage.clickOnAcceptCookies();
        loginPage.clickLoginButtonFromHomePage();
        loginPage.clickContinueWithEmailButton();
        loginPage.enterEmail(incorrectEmail);
        loginPage.enterPassword("IncorrectPassword");
        loginPage.clickNextButton();

        String errorMessage = loginPage.getErrorMessageForIncorrectCredentials();
        Assert.assertEquals(errorMessage, toastMessage);
    }

    @Test(priority = 3, description = "Verify an error is shown for correct email and incorrect password", groups = {"regression", "login"})
    public void showsErrorForIncorrectPassword()
    {

        LoginPage loginPage = new LoginPage();

        String toastMessage = ConfigReader.getProperty("correct.email.and.incorrect.password.error.toast.message");
        String correctEmail = ConfigReader.getForTarget("test.user.email");
        loginPage.clickOnAcceptCookies();
        loginPage.clickLoginButtonFromHomePage();
        loginPage.clickContinueWithEmailButton();
        loginPage.enterEmail(correctEmail);
        loginPage.enterPassword("IncorrectPassword");
        loginPage.clickNextButton();

        String errorMessage = loginPage.getErrorMessageForIncorrectCredentials();
        Assert.assertEquals(errorMessage, toastMessage);
    }
}
