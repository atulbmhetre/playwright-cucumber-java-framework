package com.samtech.qa.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * ConfigLoader — Central class for loading and accessing all test configuration.
 *
 * This class uses the SINGLETON pattern (like ExcelReader) — one shared instance
 * exists for the entire test run, loaded once at startup.
 *
 * How configuration is resolved (priority order, highest to lowest):
 *   1. Maven CLI flag     → -Dbrowser=firefox  (overrides everything)
 *   2. Environment config → qa.config.properties (env-specific settings)
 *   3. Base config        → config.properties   (shared defaults)
 *   4. Built-in defaults  → hardcoded in getDefault() (last resort fallback)
 *
 * Two config files are always loaded:
 *   - config.properties           → shared settings (applies to all environments)
 *   - {env}.config.properties     → environment-specific settings (e.g. qa.config.properties)
 *     Values in the env file override matching keys from the base file.
 *
 * The "env" value is determined by (in order):
 *   1. Maven CLI flag: -Denv=qa
 *   2. "env" key in config.properties
 *   3. RuntimeException if neither is set
 */
public class ConfigLoader {

    // Holds all loaded properties (base config + env config merged together)
    private final Properties properties = new Properties();

    // ── Singleton — instance is created immediately when the class is loaded ──
    // This is "eager initialization" — unlike ExcelReader which creates the
    // instance lazily on first call, this one is ready before getInstance() is called.
    private static final ConfigLoader loader = new ConfigLoader();

    // Stores the resolved environment name (e.g. "qa", "staging") for reference
    private String environment;

    /**
     * Returns the single shared ConfigLoader instance.
     * Safe to call from anywhere — always returns the same object.
     */
    public static ConfigLoader getInstance() {
        return loader;
    }

    /**
     * Private constructor — called once when the class loads.
     * Loads both config files and determines the active environment.
     *
     * Loading sequence:
     *   1. Read base config.properties (shared settings)
     *   2. Determine environment from CLI flag or base config
     *   3. Read {env}.config.properties and merge into properties
     *      (env-specific values overwrite any matching base values)
     */
    private ConfigLoader() {

        // Check if environment was passed as a Maven flag (-Denv=qa)
        String env = System.getProperty("env");

        try {
            // ── Step 1: Load the base config file (common to all environments) ──
            FileInputStream baseFis = new FileInputStream(
                    "src/test/resources/config/config.properties");
            properties.load(baseFis);

            // ── Step 2: Resolve the environment name ──
            // If not passed via CLI, fall back to the value defined in base config
            if (env == null || env.isEmpty()) {
                env = properties.getProperty("env");
                if (env == null || env.isEmpty()) {
                    throw new RuntimeException("Environment not specified. Please set -Denv or define env in config.properties");
                }
            }

            environment = env;

            // ── Step 3: Load the environment-specific config file ──
            // e.g. for env=qa → loads qa.config.properties
            // Properties loaded here override any same-named keys from base config
            FileInputStream fis = new FileInputStream(
                    "src/test/resources/config/" + env + ".config.properties");
            properties.load(fis);

        } catch (IOException e) {
            // Fail fast — if config can't be loaded, nothing else can work
            throw new RuntimeException("Failed to load config file for env: " + env);
        }
    }

    /**
     * Fetches a required configuration value.
     * Throws an exception if the key is not found anywhere (CLI, config files, or defaults).
     * Use this for settings that the framework cannot function without (e.g. "browser", "baseUrl").
     *
     * @param key — The property key to look up (e.g. "browser", "base.url")
     * @return    — The resolved value as a String
     * @throws RuntimeException if the key has no value in any source
     */
    public String getMandatoryProp(String key) {
        String value = resolve(key);

        if (value == null) {
            throw new RuntimeException(
                    "Required configuration missing for key: " + key);
        }

        return value;
    }

    /**
     * Fetches an optional configuration value.
     * Returns null if the key is not found — no exception thrown.
     * Use this for settings that have sensible defaults (e.g. "headless", timeouts).
     *
     * @param key — The property key to look up
     * @return    — The resolved value, or null if not found anywhere
     */
    public String getOptionalProp(String key) {
        return resolve(key);
    }

    /**
     * Resolves a property value by checking sources in priority order:
     *   1. System/Maven property  (-Dkey=value)       → highest priority
     *   2. Loaded config files    (base + env merged)
     *   3. Built-in default value (from getDefault())  → lowest priority
     *
     * This means a CLI flag always wins over config files,
     * and config files always win over hardcoded defaults.
     */
    private String resolve(String key) {

        // ── Priority 1: Maven CLI flag overrides everything ──
        String sysValue = System.getProperty(key);
        if (sysValue != null) {
            return sysValue;
        }

        // ── Priority 2: Value from loaded config files ──
        String configValue = properties.getProperty(key);
        if (configValue != null) {
            return configValue;
        }

        // ── Priority 3: Built-in fallback default ──
        return getDefault(key);
    }

    /**
     * Returns the active environment name (e.g. "qa", "staging").
     * Useful when test code needs to know which environment it's running against.
     */
    public String getEnvironment() {
        return environment;
    }

    // ============================================================
    // Built-in Default Values
    // ------------------------------------------------------------
    // These are last-resort fallbacks when a key is not found in
    // any config file or CLI flag.
    //
    // Why define defaults here instead of in config files?
    //   → Guarantees the framework always has safe values to fall
    //     back on, even if a config file is incomplete or missing a key.
    //
    // To change a default for your environment, simply add the key
    // to your {env}.config.properties — it will take priority over these.
    // ============================================================
    private String getDefault(String key) {

        switch (key) {
            case "headless":
                return "false";                 // Run with visible browser by default (local dev)

            case "screenshot.on.scenario.failure":
                return "false";                 // Don't auto-screenshot on scenario failure
            case "screenshot.on.scenario.success":
                return "false";                 // Don't auto-screenshot on scenario pass
            case "screenshot.on.scenario.skipped":
                return "false";                 // Don't auto-screenshot on skipped scenarios
            case "screenshot.for.step.passed":
                return "false";                 // Don't screenshot every passing step
            case "screenshot.for.step.failed":
                return "true";                  // DO screenshot on step failure (helps debugging)

            case "timeout.page.load":
                return "100000";                // 100 seconds max for a page to fully load
            case "timeout.global.wait":
                return "15000";                 // 15 seconds max for any element action (click, fill, etc.)
            case "timeout.default.assertion":
                return "5000";                  // 5 seconds max for an assertion to pass

            case "dataproviderthreadcount":
                return "2";                     // Default number of parallel threads for data-driven tests

            default:
                return null;                    // No default defined — caller decides how to handle null
        }
    }
}