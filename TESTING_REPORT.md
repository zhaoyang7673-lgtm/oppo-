# Testing Report — OPPO Daily Check-in (V2.1)

## 1. Execution Summary
- **Static XML & Project Structure Verification (Executed in Sandbox VM)**: Passed.
- **JVM Unit Tests (`gradle test`)**: **Written (V2 + V2.1 Acceptance Scenarios A–E), Not Executed in Sandbox VM** (no Android SDK/Gradle in sandbox; ready to run in GitHub Actions or local Android Studio).
- **Android Instrumentation Tests (`MainActivityUiTest.kt`)**: **Written, Not Executed** (no emulator/device in sandbox).
- **Physical OPPO Device Integration Tests**: **Not Executed in Sandbox**.

## 2. V2.1 Mandatory Acceptance Tests (`CheckinStateMachineTest.kt`)
- **Scenario A (Not checked in)**: Verified button inside check-in card displays `签到` → `TODAY_NOT_CHECKED_IN` → clicks once → verifies button changes to `已签到` → `CHECK_IN_SUCCESS`.
- **Scenario B (Already checked in)**: Verified button inside check-in card displays `已签到` → `TODAY_ALREADY_CHECKED_IN` → zero clicks → `ALREADY_COMPLETED`.
- **Scenario C (Streak text false-positive guard)**: Page contains `累计签到` + `已签到 11 天` without an identifiable exact check-in button → times out as `CHECK_IN_STATE_UNKNOWN`, never `TODAY_ALREADY_CHECKED_IN`.
- **Scenario D (Unrelated action guard)**: Page contains `去参与` → rejected, never clicked.
- **Scenario E (Post-click button state transition)**: Initial `签到` → one click → same button becomes `已签到` → `CHECK_IN_SUCCESS`.

## 3. Known UI Recognition Limitations
- If OPPO Mall renders the Points Mall or daily check-in card inside an unexposed WebView/custom canvas without `AccessibilityNodeInfo` text labels, `inspectDailyCheckInCard` will safely report `CHECK_IN_STATE_UNKNOWN` instead of guessing or clicking by coordinates.
- If OPPO Mall changes the button text from `签到`/`已签到` to new phrasing, update the labels in **设置与指南**.
