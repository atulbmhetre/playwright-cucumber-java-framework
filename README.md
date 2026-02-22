# playwright-cucumber-java-framework

> A production-grade test automation framework built with Java, Playwright, Cucumber, TestNG, and Allure — with full CI/CD pipelines and a live GitHub Pages reporting dashboard.

---

## What This Is

This is an end-to-end UI test automation framework designed for real-world use — not a tutorial project. It handles parallel execution, multi-environment config, video recording, Playwright tracing, defect age tracking, and automated report publishing out of the box.

**Live Report Dashboard →** [View on GitHub Pages](https://atulbmhetre.github.io/playwright-cucumber-java-framework)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Browser Automation | Playwright |
| Test DSL | Cucumber 7 (BDD / Gherkin) |
| Test Runner | TestNG |
| Reporting | Allure + GitHub Pages |
| Build Tool | Maven |
| CI/CD | GitHub Actions |
| Data | Apache POI (Excel) |

---

## Key Features

- **Smoke → Regression pipeline** — Smoke tests gate regression automatically. Regression only runs if smoke passes on a code push.
- **Parallel execution** — Thread-safe architecture using `ThreadLocal` for Playwright, Browser, BrowserContext, and Page. Safe to run N scenarios simultaneously.
- **Multi-environment config** — Switch between `qa`, `staging`, or any environment with `-Denv=qa`. Base config + environment-specific override config.
- **Multi-browser support** — Chromium, Firefox, and WebKit. Pass `-Dbrowser=firefox` at runtime.
- **Fallback locator strategy** — Every element interaction accepts multiple selectors. If the first fails, it tries the next — reduces flakiness from UI changes.
- **Video recording** — Every test is recorded. Videos are attached to the Allure report on failure and deleted on pass to save disk space.
- **Playwright tracing** — Full trace (DOM snapshots, screenshots, source) saved on failure. Open at [trace.playwright.dev](https://trace.playwright.dev) for a visual replay.
- **Defect Age Report** — Custom utility that calculates how many consecutive builds each failing test has been failing. Helps prioritise what to fix first.
- **Failed locator tracking** — Broken selectors are collected throughout the run and written to a JSON report at the end, grouped by locator with affected scenarios listed.
- **GitHub Pages dashboard** — A live dashboard showing pass/fail status and report links for the last 20 smoke and regression builds.
- **Failed test rerun** — A dedicated runner reads `target/failed_scenarios.txt` and re-runs only failed scenarios, giving flaky tests a second chance.

---

## Project Structure

```
src/
├── main/
│   └── java/
│       └── com/samtech/qa/
│           ├── factory/           # DriverFactory — Playwright browser management
│           └── utils/             # ConfigLoader, ElementUtils, ExcelReader, FailedLocatorCollector
├── test/
│   ├── java/
│   │   └── com/samtech/qa/
│   │       ├── contexts/          # TestContext — shared state per scenario (DI)
│   │       ├── hooks/             # ApplicationHooks — before/after scenario lifecycle
│   │       ├── pages/             # Page Object classes (BasePage + feature pages)
│   │       ├── runners/           # TestNG runners (Smoke, Regression, FailedTest)
│   │       ├── stepdefinitions/   # Cucumber step definitions
│   │       └── testutilities/     # Allure helpers, DefectAge report, stepStatus enum
│   └── resources/
│       ├── config/                # config.properties + {env}.config.properties
│       ├── features/              # Cucumber .feature files
│       └── testdata/              # Excel test data files per environment
.github/
└── workflows/
    ├── smoke-tests.yml            # Smoke CI pipeline
    └── regression-tests.yml       # Regression CI pipeline
```

---

## Prerequisites

- Java 21+
- Maven 3.8+
- Git

No browser installation needed — Playwright downloads browser binaries automatically.

---

## Setup

```bash
# 1. Clone the repo
git clone https://github.com/atulbmhetre/playwright-cucumber-java-framework.git
cd playwright-cucumber-java-framework

# 2. Install dependencies and Playwright browsers
mvn install -DskipTests
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps" -Dexec.classpathScope=test
```

---

## Running Tests

### Smoke Tests
```bash
mvn clean test -Dtestng.suite.file=testng-smoke.xml -Denv=qa -Dbrowser=chromium -Dheadless=true
```

### Regression Tests
```bash
mvn clean test -Dtestng.suite.file=testng-regression.xml -Denv=qa -Dbrowser=chromium -Dheadless=true
```

### Available Parameters

| Parameter | Default | Options |
|---|---|---|
| `-Denv` | `qa` | Any environment with a matching config file |
| `-Dbrowser` | `chromium` | `chromium`, `firefox`, `webkit` |
| `-Dheadless` | `true` | `true`, `false` |
| `-Dcucumber.filter.tags` | suite default | `@smoke`, `@regression`, `@login`, etc. |
| `-Dparallel.thread.count` | `2` | Any integer |

### Run specific tags
```bash
mvn clean test -Dtestng.suite.file=testng-smoke.xml -Dcucumber.filter.tags="@login" -Denv=qa
```

---

## Configuration

Configuration is split across two files that are merged at runtime:

**`src/test/resources/config/config.properties`** — shared settings (applies to all environments)

**`src/test/resources/config/{env}.config.properties`** — environment-specific overrides

Priority order (highest wins):
1. Maven CLI flag (`-Dbrowser=firefox`)
2. Environment config (`qa.config.properties`)
3. Base config (`config.properties`)
4. Built-in defaults (defined in `ConfigLoader.java`)

### Key Config Properties

```properties
# Environment
env=qa
url=https://your-app-url.com
browser=chromium
headless=true

# Timeouts (milliseconds)
timeout.page.load=100000
timeout.global.wait=15000
timeout.default.assertion=5000

# Screenshots
screenshot.on.scenario.failure=true
screenshot.on.scenario.success=false
screenshot.for.step.failed=true
screenshot.for.step.passed=false

# Parallel threads
dataproviderthreadcount=2
```

---

## Adding a New Environment

1. Create `src/test/resources/config/{newenv}.config.properties`
2. Add environment-specific values (url, credentials, etc.)
3. Run with `-Denv=newenv`

No code changes required.

---

## Test Data

Test data is managed through Excel files stored per environment:

```
src/test/resources/testdata/{env}/TestData.xlsx
```

Each sheet represents a feature area. Each row represents one scenario's data with a `ScenarioName` column as the unique key.

Fetching data in a step definition:
```java
Map<String, String> data = DataManager.getTestData("LoginSheet", "ValidLogin");
String username = data.get("Username");
```

---

## CI/CD Pipelines

Two GitHub Actions workflows run automatically:

**Smoke** (`smoke-tests.yml`) — triggers on every push to `main`
**Regression** (`regression-tests.yml`) — triggers automatically after smoke passes

Both workflows publish results to the live GitHub Pages dashboard.

Regression can also be triggered manually from the GitHub Actions UI with custom parameters (environment, browser, tags, thread count).

If smoke was triggered manually, regression will **not** auto-run — manual smoke runs are treated as exploratory checks, not full pipeline triggers.

---

## Reports

After a run, Allure reports are published to GitHub Pages automatically.

**Live dashboard:** `https://atulbmhetre.github.io/playwright-cucumber-java-framework`

The dashboard shows:
- Pass/fail status for each build
- Links to the full Allure report for both smoke and regression
- Last 20 builds retained

To generate the report locally:
```bash
allure generate target/allure-results -o allure-report --clean
allure open allure-report
```

---

## Output Files

| File | Description |
|---|---|
| `target/allure-results/` | Raw Allure result data |
| `target/cucumber-reports/cucumber.html` | Standard Cucumber HTML report |
| `target/cucumber-reports/cucumber.json` | Machine-readable JSON report |
| `target/failed_scenarios.txt` | Failed scenario paths for rerun |
| `target/defect-age-report.csv` | How long each failing test has been failing |
| `test-output/failedLocators_*.json` | Selectors that failed during the run |
| `test-output/videos/` | Video recordings (failures only retained) |
| `test-output/traces/` | Playwright trace files (failures only) |
| `test-output/logs/` | Test execution logs |

---

## Architecture Decisions

**Why ThreadLocal for browser objects?**
Parallel test execution means multiple scenarios run simultaneously. ThreadLocal gives each thread its own isolated browser session, preventing tests from interfering with each other.

**Why Singleton for ConfigLoader and ExcelReader?**
Config files and Excel data are loaded once and reused. Creating new instances per test would add unnecessary I/O overhead on every scenario.

**Why is history restored after `mvn clean` and not before?**
`mvn clean` deletes the entire `target/` folder. Restoring history before the test run would delete it. History is restored into `allure-results` after tests complete but before `allure generate` runs.

**Why does regression not re-generate the dashboard index?**
The dashboard HTML is owned and regenerated by the smoke workflow. Regression only pushes its report data and metadata. The dashboard picks it up automatically on the next smoke run.

---

## Author

**Atul Bmhetre**
[GitHub](https://github.com/atulbmhetre)
