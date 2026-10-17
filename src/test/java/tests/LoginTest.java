package tests;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import pages.LoginPage;
import utils.ConfigReader;

public class LoginTest extends BaseTest {

    private LoginPage loginPage;

    /**
     * Page objects capture the driver in their constructor, so they can only be built
     * once BaseTest.setUp has created one. A field initializer would run during class
     * construction, before any driver exists.
     */
    @BeforeMethod(dependsOnMethods = "setUp")
    public void initPages() {
        loginPage = new LoginPage();
    }

    @Test(priority = 1, description = "Verify complete login flow with valid credentials", enabled = true, groups = "login")
    public void completesLoginWithValidCredentials() {

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


    @Test(priority = 2, description = "Verify an error is shown for incorrect email and password credentials that do not match an account", groups = "login")
    public void showsErrorForIncorrectEmailAndPassword()
    {
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

    @Test(priority = 3, description = "Verify an error is shown for correct email and incorrect password", groups = "login")
    public void showsErrorForIncorrectPassword()
    {

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
