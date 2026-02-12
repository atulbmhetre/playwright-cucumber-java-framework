package com.samtech.qa.factory;

import com.microsoft.playwright.*;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.samtech.qa.utils.ConfigLoader;
import com.samtech.qa.utils.ExcelUtility.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Arrays;

public class DriverFactory {

    private final Logger logger = LoggerFactory.getLogger(DriverFactory.class);
    private ThreadLocal<Playwright> tlPlaywright = new ThreadLocal<>();
    private ThreadLocal<Browser> tlBrowser = new ThreadLocal<>();
    private ThreadLocal<BrowserContext> tlBrowserContext = new ThreadLocal<>();
    private ThreadLocal<Page> tlPage = new ThreadLocal<>();

    public ThreadLocal<Playwright> getTlPlaywright() {
        return tlPlaywright;
    }
    public ThreadLocal<Browser> getTlBrowser() {
        return tlBrowser;
    }
    public ThreadLocal<BrowserContext> getTlBrowserContext() {
        return tlBrowserContext;
    }
    public ThreadLocal<Page> getTlPage() {
        return tlPage;
    }

    public Page initPlaywright(){
        String browserName = System.getProperty("browser", ConfigLoader.getInstance().getProperty("browser"));
        if (browserName == null || browserName.isEmpty()) {
            logger.error("CRITICAL ERROR: Browser name not specified in Maven flag (-Dbrowser) or config files.");
            throw new RuntimeException("Missing Browser Configuration: Please define 'browser' in your properties or pass it via CLI.");
        }
        logger.debug("Browser set to test on: {}", browserName);
        Boolean isHeadless = Boolean.parseBoolean(System.getProperty("headless", ConfigLoader.getInstance().getProperty("headless")));
        logger.debug("Headless flag set to: {}", isHeadless);
        tlPlaywright.set(Playwright.create());
        switch (browserName.trim().toLowerCase()){
            case "chromium":
                tlBrowser.set(tlPlaywright.get().chromium().launch(
                        new BrowserType.LaunchOptions()
                        .setHeadless(isHeadless)
                        .setArgs(Arrays.asList("--start-maximized",
                                "--disable-extensions",
                                "--allow-insecure-localhost")
                        ))
                );
                break;
            case "firefox":
                tlBrowser.set(tlPlaywright.get().firefox().launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(isHeadless)
                                .setArgs(Arrays.asList("--start-maximized",
                                        "--disable-extensions",
                                        "--allow-insecure-localhost")
                                ))
                );
                break;
            case "webkit":
                tlBrowser.set(tlPlaywright.get().webkit().launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(isHeadless)
                                .setArgs(Arrays.asList("--start-maximized",
                                        "--disable-extensions",
                                        "--allow-insecure-localhost")
                                ))
                );
                break;
            case "default":
                logger.debug("Provided browser \"{}\"  is not valid or configured", browserName);
                break;
        }
        tlBrowserContext.set(tlBrowser.get().newContext(
                new Browser.NewContextOptions().setViewportSize(null)
                        .setRecordVideoDir(Paths.get("test-output/videos/"))
                        .setRecordVideoSize(1280, 720)));
        PlaywrightAssertions.setDefaultAssertionTimeout(Long.parseLong(ConfigLoader.getInstance().getProperty("timeout.default.assertion","5000")));
        tlBrowserContext.get().setDefaultTimeout(Long.parseLong(ConfigLoader.getInstance().getProperty("timeout.global.wait","15000")));
        tlBrowserContext.get().setDefaultNavigationTimeout(Long.parseLong(ConfigLoader.getInstance().getProperty("timeout.page.load", "100000")));
        logger.debug("Default Assertion timeout, Global timeout, Navigation timeout set.");
        tlPage.set(tlBrowserContext.get().newPage());
        logger.debug("Browser \"{}\" launched successfully.", browserName);
        return tlPage.get();
    }

    public void closePlaywright(){
        if (tlPage.get() != null)
            tlPage.get().close();
        logger.debug("Browser Page closed successfully.");
        if (tlBrowserContext.get() != null)
            tlBrowserContext.get().close();
        logger.debug("Browser context closed successfully.");
        if (tlBrowser.get()!= null)
            tlBrowser.get().close();
        logger.debug("Browser closed successfully.");
        if(tlPlaywright.get() != null)
            tlPlaywright.get().close();
        logger.debug("Playwright closed successfully.");

        tlPage.remove();
        tlBrowserContext.remove();
        tlBrowser.remove();
        tlPlaywright.remove();
    }

}
