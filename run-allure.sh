#!/bin/bash
# ------------------------------------------
# Local Allure workflow: run tests + generate report + preserve history
# ------------------------------------------

# 1️⃣ Ensure persistent history folder exists
HISTORY_BACKUP="./allure-history-backup"
mkdir -p $HISTORY_BACKUP

# 2️⃣ Copy previous history into results folder (if any)
if [ -d "$HISTORY_BACKUP" ]; then
  cp -r $HISTORY_BACKUP/* target/allure-results/ 2>/dev/null || true
fi

# 3️⃣ Run tests (clean ensures no duplicate retries)
mvn clean test

# 4️⃣ Generate Allure report
allure generate target/allure-results -o target/allure-report --clean

# 5️⃣ Update backup with latest history for next run
cp -r target/allure-report/history/* $HISTORY_BACKUP

# 6️⃣ Open report in browser
allure open target/allure-report
