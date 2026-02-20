# How to Use — Playwright Cucumber Java Framework

> Practical guide for anyone working with this framework day to day. Read this before writing your first test.

---

## Before You Start

Make sure you've completed the setup steps in the README first:
- Java 21+ installed
- Maven 3.8+ installed
- Playwright browsers installed via `mvn exec:java`

If you can run `mvn clean test -Dtestng.suite.file=testng-smoke.xml -Denv=qa` and see tests execute, you're ready.

---

## Rules to Follow

These are not optional guidelines — they are the rules that keep the framework stable, especially when running tests in parallel. Following them avoids the most common problems before they happen.

**1. Every scenario must have a unique ScenarioName in the Excel sheet.**
The `ScenarioName` column is the key used to fetch test data. Duplicate names cause the wrong data to be returned silently — no error, just wrong behaviour.

**2. Leave cells blank if a scenario doesn't need that column's data.**
Do not delete columns or create new sheets per scenario. Leave unused cells empty — the framework returns an empty string `""` for blank cells. Guard against empty values in your step code before using them:
```java
String role = data.get("Role");
if (role != null && !role.isEmpty()) {
    elementUtils.enterText(role, ROLE_FIELD);
}
```

**3. Always create TestData.xlsx when adding a new environment.**
The file must exist at `src/test/resources/testdata/{env}/TestData.xlsx` before running tests for that environment. Create the file first, even if it only has column headers — a missing file causes a RuntimeException mid-run, not at startup.

**4. Never use shared accounts or shared records across parallel scenarios.**
If two scenarios run at the same time and both modify the same user account or data record, they will conflict and cause unpredictable failures. Each scenario must use its own isolated data row with a unique user, unique record, or data that is safe to read without modifying.

**5. Always tag scenarios correctly before committing.**
- `@smoke` → critical path tests only. Run on every push and gate the pipeline.
- `@regression` → full suite. Run after smoke passes.
- A scenario can carry both tags if it belongs to both suites.
- Untagged scenarios will never run in CI.

**6. Define selectors as constants at the top of your page class — never inline.**
```java
// Correct — easy to find and update when UI changes
private static final String LOGIN_BUTTON = "button[type='submit']";

// Wrong — scattered and hard to track down
elementUtils.clickElement("button[type='submit']");
```

**7. Always provide at least one fallback selector for critical elements.**
```java
elementUtils.clickElement(
    "#submit-btn",               // Primary
    "button[type='submit']",     // Fallback 1
    "//button[text()='Submit']"  // Fallback 2
);
```

---

## Adding a New Test Scenario

### Step 1 — Write the feature file

Feature files live in `src/test/resources/features/`:

```gherkin
@smoke
Scenario: User logs in with valid credentials
  Given the user is on the login page
  When the user enters valid credentials
  Then the user should see the dashboard
```

### Step 2 — Add test data

Open the Excel sheet in `src/test/resources/testdata/{env}/TestData.xlsx`. Add a new row with a unique `ScenarioName`. Fill required columns, leave unused ones blank.

Example LoginSheet:

| ScenarioName | Username | Password | Role | RememberMe |
|---|---|---|---|---|
| ValidLogin | admin@qa.com | Admin@123 | Admin | true |
| InvalidPassword | admin@qa.com | wrongpass | Admin | false |
| GuestLogin | guest@qa.com | Guest@123 | | false |

### Step 3 — Write step definitions

Step definitions live in `src/test/java/com/samtech/qa/stepdefinitions/`:

```java
@Given("the user is on the login page")
public void userIsOnLoginPage() {
    String url = ConfigLoader.getInstance().getMandatoryProp("url");
    loginPage.navigateTo(url);
}
```

### Step 4 — Create or update a Page Object

Page classes live in `src/test/java/com/samtech/qa/pages/`. Every page class extends `BasePage`:

```java
public class LoginPage extends BasePage {

    private static final String USERNAME_FIELD = "#username";
    private static final String PASSWORD_FIELD = "#password";
    private static final String LOGIN_BUTTON   = "button[type='submit']";

    public LoginPage(ElementUtils elementUtils) {
        super(elementUtils);
    }

    public void enterUsername(String username) {
        elementUtils.enterText(username, USERNAME_FIELD);
    }

    public void clickLogin() {
        elementUtils.clickElement(LOGIN_BUTTON);
    }
}
```

### Step 5 — Wire the page into the step definition

```java
public class LoginSteps {

    private LoginPage loginPage;

    public LoginSteps(TestContext testContext) {
        this.loginPage = new LoginPage(testContext.getElementUtils());
    }
}
```

---

## Fetching Test Data in Steps

```java
Map<String, String> data = DataManager.getTestData("LoginSheet", "ValidLogin");

String username = data.get("Username");
String password = data.get("Password");
```

`ScenarioName` must match the key you pass. It is case-insensitive.

---

## Adding a New Environment

1. Create `src/test/resources/config/{newenv}.config.properties`
2. Create `src/test/resources/testdata/{newenv}/TestData.xlsx` ← do this first (Rule 3)
3. Add environment-specific values to the config file (url, credentials, etc.)
4. Run with `-Denv=newenv`

No Java code changes needed.

---

## Running Tests Locally

### Basic run
```bash
mvn clean test -Dtestng.suite.file=testng-smoke.xml -Denv=qa -Dbrowser=chromium -Dheadless=true
```

### Run with visible browser (useful for debugging)
```bash
mvn clean test -Dtestng.suite.file=testng-smoke.xml -Denv=qa -Dheadless=false
```

### Run specific tags
```bash
mvn clean test -Dtestng.suite.file=testng-smoke.xml -Dcucumber.filter.tags="@login" -Denv=qa
```

### Run with more parallel threads
```bash
mvn clean test -Dtestng.suite.file=testng-regression.xml -Denv=qa -Dparallel.thread.count=4
```

---

## Debugging a Failing Test

**Step 1 — Check the Allure report**
Shows exactly which step failed with a screenshot attached (if `screenshot.for.step.failed=true`).

**Step 2 — Open the Playwright trace**
On failure, a `.zip` trace file is attached to the Allure report.
Upload it at [trace.playwright.dev](https://trace.playwright.dev) — full visual replay of every browser action, every page state, every network call.

**Step 3 — Watch the video**
On failure, a `.webm` video is attached to the Allure report.

**Step 4 — Check failedLocators JSON**
If the failure is "element not found", check `test-output/failedLocators_*.json`. Lists every selector that failed, grouped by locator, with affected scenarios per entry.

**Step 5 — Run with headless=false locally**
```bash
-Dheadless=false -Dparallel.thread.count=1
```
Watch one scenario run live in the browser.

---

## Updating Behaviour Without Changing Code

All behaviour flags live in your `{env}.config.properties` file:

| What you want | Property to set |
|---|---|
| See browser while tests run | `headless=false` |
| Screenshot on every step failure | `screenshot.for.step.failed=true` |
| Screenshot on every step pass | `screenshot.for.step.passed=true` |
| Screenshot at end of failed scenario | `screenshot.on.scenario.failure=true` |
| Increase element wait time | `timeout.global.wait=30000` |
| Run more tests in parallel | `dataproviderthreadcount=4` |

---

---

# Limitations

These are genuine hard limitations of the framework — things it cannot do regardless of how carefully you follow the rules above.

---

## Browser Support

**WebKit on CI is not the same as Safari on macOS.**
Playwright's WebKit engine runs on Linux in CI. Tests passing on WebKit in CI do not guarantee they pass on real Safari on macOS. For genuine Safari coverage, run locally on a Mac with `-Dbrowser=webkit`.

**Mobile browsers are not supported.**
Desktop browsers only. No mobile viewport emulation or device simulation.

---

## No API Testing Support

UI-only framework. No HTTP client or REST library built in. If you need to call an API in test setup or teardown (e.g. create a user via API before a UI test), you would need to add a library like REST Assured manually.

---

## No Step-Level Retry

`FailedTestRunner` gives failed scenarios one full rerun at the scenario level. There is no per-step retry — if a single step fails, the whole scenario is retried from the beginning. There is no built-in way to retry just one step N times before failing.

---

## No Automatic Test Data Cleanup

The framework does not delete or reset data created during tests. If a scenario creates a record, that record stays in the application after the test finishes. Tests must either clean up after themselves in an `@After` hook or use data that is safe to leave behind.

---

## Allure CLI Required for Local Reports

To view Allure reports locally, install the Allure CLI once:
```bash
npm install -g allure-commandline
```

Then generate and open:
```bash
allure generate target/allure-results -o allure-report --clean
allure open allure-report
```

Double-clicking `index.html` won't work — browsers block local HTML files from loading local resources for security reasons.

---

## CI Runs on Linux Only

GitHub Actions uses `ubuntu-latest`. Windows or macOS-specific browser behaviour won't be caught in CI. If your application behaves differently on Windows browsers, those differences won't surface in the pipeline.

---

## GitHub Actions Usage Limits

Each smoke + regression pipeline run consumes GitHub Actions minutes. Free GitHub accounts have a monthly limit. Monitor usage: GitHub → Settings → Billing.
