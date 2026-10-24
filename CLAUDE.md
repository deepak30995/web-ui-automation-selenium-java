# CLAUDE.md

Selenium + TestNG UI automation for the Graphy-hosted learning site. Page Object Model,
Maven, ExtentReports + Allure, Log4j2.

## Commands

```bash
mvn clean test                      # every test: staging, local, chrome
mvn clean test -Psmoke              # test type:  smoke | sanity | regression | e2e
mvn clean test -Dtest.env=docker    # runs on:    local | docker | browserstack | lambdatest | saucelabs | aws
mvn clean test -Dbrowser=firefox    # browser:    chrome | firefox | edge | safari
mvn clean test -Dtarget.env=prod    # site:       staging | prod
./analyze-failure.sh                # diagnose the newest failure bundle
./fix-failure.sh                    # propose a fix (STALE_LOCATOR verdicts only)
```

All four axes combine freely:

```bash
mvn clean test -Psmoke -Dtarget.env=prod -Dtest.env=browserstack -Dbrowser=firefox
```

Profiles carry only the test type; the other three stay as `-D` flags, so there is no
combinatorial explosion of profile names.

Each profile selects a suite file under `testng/`, and that file selects by group and
scans by package - so adding a test to a suite means tagging the method, not editing
XML. The e2e suite has no group filter - it runs every test method, tagged or not -
and is the default, so a forgotten group can never hide a test. Suite files
exist so each type can carry its own configuration (regression runs
`parallel="methods"`, smoke must not) and so they can be run directly from the IDE.

**Every `@Before*`/`@After*` method must carry `alwaysRun = true`.** Group filtering
excludes configuration methods outside the selected group, so without it a profiled
run skips driver setup and every test fails with a null driver.

## Layout

Test code lives in `src/test/java`; `src/main/java` holds only the reusable utility
layer, which must stay free of TestNG imports (TestNG is `<scope>test</scope>`).

| Layer | Location | Owns | Must never contain |
|-------|----------|------|--------------------|
| Test | `src/test/java/tests` | Flow order, assertions, test data | Locators, waits, frame handling |
| Page Object | `src/test/java/pages` | Locators, actions on one page | Step numbers, assertions |
| Base | `src/test/java/base` | Waits, clicks, frame navigation | Anything page-specific |
| Utilities | `src/main/java/utils` | Driver, config, reporting | Test or page knowledge |

## Conventions

- **No step numbers in page objects.** Log the action ("Entered email"), never its
  position in a flow. A page object is reused by tests that call it in a different
  order; numbering it there forces edits across files whenever a test changes.
- **Do not double-wait.** `clickElement()` and `typeText()` already wait internally.
  Calling `waitForElementClickable()` first just doubles the timeout on failure.
- **Let exceptions propagate.** `BaseTest` already reports failures, captures a
  screenshot and writes a failure bundle. Catching only to re-log reports the same
  failure three times.
- **Promote shared mechanics to `BasePage`.** Example: `switchToFrameFromRoot()`
  returns to the top-level document before entering a frame, because frame entry
  resolves relative to the current context and would otherwise fail on a second call.
- Prefer stable locators (`id`, `data-*`) over class lists or deep XPath. Hashed
  CSS-module classes (e.g. `signup_lable_nr1k1`) regenerate on frontend builds.

## Conditionally rendered elements — read before "fixing" a missing element

Some UI is gated on keys set on the **organization record**, so it is legitimately
absent for some orgs and present for others. **An absent element of this kind is
expected behaviour, not a stale locator.**

Known cases:

| Element | Locator | Gate |
|---------|---------|------|
| Cookie consent banner | `id=acceptCookiesBtn` | Organization-level config key |

This matters because the DOM evidence is identical either way: an element that was
renamed and an element that is switched off both produce zero matches in the captured
page source. Never propose a locator change for one of these without confirming the
gate is enabled.

The pattern for handling them — guard, skip, and carry on:

```java
if (!isElementDisplayed(acceptCookiesButton)) {
    logger.info("Accept cookies button not present on home page - skipping");
    return;
}
```

If you meet a new conditional element, add it to the table above.

## Credentials and URLs

Nothing sensitive is committed. `config*.properties` hold only `${...}` placeholders,
resolved by `ConfigReader` in this order:

**`-D` JVM property → environment variable → `secrets.properties` (git-ignored)**

Keys: `BASE_URL`, `STAGING_USER_EMAIL`, `STAGING_USER_PASSWORD`. A blank value counts as
unset and logs a warning rather than resolving to an empty string.

The suite needs a **user** account. A creator/admin account logs in successfully but
lands on the business dashboard, where the user locators do not exist — a failure
that looks like stale locators but is actually wrong test data.

## Diagnosing failures

Every failure writes a bundle to `test-output/failures/`:

- `<test>_<ts>.json` — exception, parsed locator, URL, title, environment, last 80 log lines
- `<test>_<ts>.html` — **top-level** DOM at failure
- `<test>_<ts>_frame.html` — the iframe DOM, when the failure happened inside one
- `<test>_<ts>_diagnosis.md` — written by `./analyze-failure.sh`

Only the newest `failures.max.bundles` (default 5) are retained. Pruning is per bundle,
not per file type, so a surviving JSON always still has its page source.

Diagnose in this order. Each step rules out the ones below it:

1. **Is the browser on the expected page?** (`page.url`, `page.title`) If not, the
   locator is irrelevant — a step is missing or the test data is wrong.
2. **Is the locator present in the captured DOM?** Check both the top-level and frame
   files. Absent → stale locator *or* a conditional element (see above). Present → the
   locator is fine.
3. **Present but not interactive?** Exception type and element attributes.
4. **Were all steps slow, or only this one?** Compare `recentLogs` timestamps.

Categories, drawn on the line "is there something to fix in our code?":

| | Meaning | Fix in our code? |
|---|---|---|
| **A STALE_LOCATOR** | Element present under a different locator | Yes — update the locator |
| **B TIMING** | App healthy; the test did not wait correctly | Yes — fix the wait |
| **C TEST_DATA_OR_ENV** | Wrong account/env/config, or the site was slow or down | No |
| **D PRODUCT_BUG** | The application genuinely misbehaved | No — report it |
| **E UNKNOWN** | Evidence insufficient | No — say so |

`./fix-failure.sh` acts only on an **A STALE_LOCATOR** verdict. Every other category
exits without touching the repository, because changing the test would hide a real
problem rather than fix one. When it does act it re-runs the failing test and opens a
pull request only if that test now passes - never a merge, never a direct commit to main.

Hard rules, because a wrong fix produces a passing test that verifies the wrong thing:

- Never propose a locator change for B, C, D or E.
- **Never change an assertion's expected value to match what was observed.** If the app
  says something different from the expectation, that is a finding, not a fix.
- If the page source shows a different application area than the test targets, that is
  C, not a locator problem.
- When evidence is thin, answer UNKNOWN. That is a useful result.

## Retries

Suites register `listeners.RetryListener`, applying `RetryAnalyzer` to every test.
Retries happen **only** for `WebDriverException` and its subclasses. `AssertionError`
is never retried — the application behaving differently is the finding, and re-running
until green would hide it. Count comes from `retry.count` (default 1).

TestNG does not retry configuration methods, so a `@BeforeMethod` failure is final.

## Known gaps

- No explicit wait after login before entering the My Courses frame; environment
  slowness surfaces as a confusing element-level timeout.
- `test-output/` and `allure-results/` are git-ignored build output.
