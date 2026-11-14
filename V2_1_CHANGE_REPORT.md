# V2.1 Incremental Update Change Report

## Overview
V2.1 upgrades the existing **OPPO 每日签到助手** (`io.github.oppocheckin.helper`, `versionName = "2.1.0"`, `versionCode = 2`) with screenshot-aligned OPPO Mall UI hierarchy validation, authoritative check-in button state detection, real-time progress notifications, bounded cleanup/return, and a developer Screenshot-Reference Test Mode—while preserving all V2 scheduling, AccessibilityService package isolation, privacy, and security guarantees.

## Key Changes
1. **Bottom Dock "会员" Detection (`UiNodeInspector.findBottomDockTab`)**:
   - Verifies exact `"会员"` match within a bottom navigation / dock container (checked via sibling dock tabs `首页`, `选购`, `会员`, `服务`, `我的` or container resource IDs) rather than screen coordinates.
2. **Confidence-Based Page Verification (`verifyMembershipPage`, `verifyPointsPage`)**:
   - Membership page requires at least one strong membership anchor (`钻卡会员`, `会员专属权益`, `尊享积分`, `用机服务`, `购物特权`) plus dock/points context.
   - Points Mall page verifies `"积分商城"` or check-in card anchors (`累计签到`, `签到提醒`, `每日签到`), never hardcoding numeric point balances (e.g. `280`).
3. **Authoritative Button-Only Status Detection (`inspectDailyCheckInCard`)**:
   - Eliminates false positives from streak text such as `"已签到 11 天"`.
   - Locates the daily check-in card subtree first, then inspects only the actionable button's exact visible text (`"签到"` vs `"已签到"`), rejecting `"去参与"` and promotional/product cards.
   - If the button cannot be confidently identified, reports `CHECK_IN_STATE_UNKNOWN` (never `TODAY_ALREADY_CHECKED_IN`).
4. **Post-Click Verification & Cleanup (`CheckinStateMachine.kt`, `NotificationHelper.kt`)**:
   - Highest-confidence verification confirms the same button transitions from `"签到"` to `"已签到"`; fallback limited verification uses explicit success text (`"签到成功"`, `"已完成"`, `"已领取"`).
   - Every state emits Chinese real-time progress updates to both the in-app status and a low-priority progress notification, followed by `CLEANUP_AND_RETURN` → `IDLE`.
5. **Screenshot-Reference Test Mode (`CheckinModels.kt`, `CheckinRepository.kt`, `DashboardScreens.kt`)**:
   - Optional toggle in Settings that records structured diagnostic entries (`STATE`, expected/detected anchor, button text, hierarchy validation result, duration) without logging full accessibility trees or account data.
6. **ColorOS Installation Compatibility (`app/build.gradle.kts`)**:
   - Updated `applicationId` to `io.github.oppocheckin.helper` to avoid ColorOS `com.oppo.*` shared-user signature conflicts during APK installation.
