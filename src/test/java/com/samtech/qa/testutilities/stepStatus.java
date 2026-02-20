package com.samtech.qa.testutilities;

/**
 * stepStatus — A simple enum representing the pass/fail outcome of a test step.
 *
 * Enums are used instead of plain strings (like "PASSED" / "FAILED") to prevent
 * typos and make status comparisons type-safe — the compiler catches invalid
 * values at build time rather than at runtime.
 *
 * Used by step-level reporting utilities (e.g. TestProofsCollection) to record
 * and attach the outcome of individual steps to the Allure report.
 */
public enum stepStatus {
    PASSED,  // The step completed successfully
    FAILED   // The step encountered an error or assertion failure
}