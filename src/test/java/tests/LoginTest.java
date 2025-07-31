package tests;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.Login;
import utils.ConfigReader;

public class LoginTest extends BaseTest {

    @Test(priority = 1, description = "Verify complete login flow with valid credentials")
    public void testCompleteLoginFlow()
    {
        Login loginPage = new Login();

        // Get test data from config.properties
        String testEmail = ConfigReader.getProperty("test.learner.email");
        String testPassword = ConfigReader.getProperty("test.learner.password");
        String expectedHeaderText = ConfigReader.getProperty("test.learner.active.course.page.header.name");

        test.info("Starting complete login flow test");

        try {
            // Step 1: Click accept cookies button from home page
            loginPage.clickOnAcceptCookies();
            test.pass("Step 1: Successfully clicked accept cookies button from home page");

            // Step 2: Click login button from home page
            loginPage.clickLoginButtonFromHomePage();
            test.pass("Step 1: Successfully clicked login button from home page");

            // Step 3: Click login button on popup
            loginPage.clickLoginButtonOnPopup();
            test.pass("Step 2: Successfully clicked login button on popup");

            // Step 4: Click continue with email
            loginPage.clickContinueWithEmailButton();
            test.pass("Step 3: Successfully clicked continue with email button");

            // Step 5: Enter email
            loginPage.enterEmail(testEmail);
            test.pass("Step 4: Successfully entered email");

            // Step 6: Enter password
            loginPage.enterPassword(testPassword);
            test.pass("Step 5: Successfully entered password");

            // Step 7: Click next button
            loginPage.clickNextButton();
            test.pass("Step 6: Successfully clicked next button");

            // Step 8: Get header text and perform assertion
            String actualHeaderText = loginPage.getActiveCourseHeaderText();
            Assert.assertNotNull(actualHeaderText, "Header text should not be null");
            Assert.assertEquals(actualHeaderText, expectedHeaderText, "Header text should match expected value");
            test.pass("Step 7: Successfully retrieved header text: " + expectedHeaderText);

            test.pass("Complete login flow test passed successfully");

        } catch (Exception e) {
            test.fail("pages.Login flow test failed: " + e.getMessage());
            throw e;
        }
    }


}
