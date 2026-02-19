@Login
Feature: User Authentication

  Background:
    Given the user is on the login page

  @smoke
  Scenario: Successful login with admin credentials__
    When the user logs into the application with user credentials
    Then the user should see the "Dashboard" overview

  @smoke
  Scenario: Invalid login using direct feature data
    When the user logs into the application with username "InvalidUser" and password "WrongPass123"
    Then the user should see the "Invalid credentials" error message

@regression
  Scenario: Test 1 Successful login with admin credentials
    When the user logs into the application with user credentials
    Then the user should see the "Dashboard" overview1

  @regression
  Scenario: Test 2 Successful login with admin credentials
    When the user logs into the application with user credentials
    Then the user should see the "Dashboard" overview

  @regression
  Scenario: Test 3 Successful login with admin credentials
    When the user logs into the application with user credentials
    Then the user should see the "Dashboard" overview
