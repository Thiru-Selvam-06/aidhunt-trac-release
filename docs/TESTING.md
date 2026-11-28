# AIDHUNT TRAC — TESTING ARCHITECTURE & GUIDE
## Automated Test Suites, Compose Testing Patterns, CI Verification & Manual Protocols

---

## 1. Test Suite Overview

AIDHUNT TRAC employs a multi-tiered automated testing architecture designed to ensure zero regressions across financial computations, offline synchronization, responsive Jetpack Compose UI presentation, and hardware interaction.

### Test Distribution
- **Robolectric & Compose UI Tests**: Fast, deterministic local unit and UI tests running on the JVM without an attached Android emulator or physical device.
- **Room SQLite Tests**: In-memory database tests asserting schema integrity, transactions, and outbox operations.
- **Partitioned CI Test Suites**: Disjoint partitions (`unit`, `database`, `compose`, `outbox`) managed via `scripts/ci-tests.gradle`.
- **Physical Device Validation**: Hardware verification on real Android devices (`vivo I2304`) testing 120Hz rendering, touch targets, and hardware back dispatch.

---

## 2. Unit Test Structure

Unit tests reside under `app/src/test/java/com/example/`.

### Directory & Package Layout
- `com.example.HomeDashboardRedesignTest`: Tests for Today's Job Entries card, New Job red-to-pink color animation, and Workspace Banner isolation.
- `com.example.BusinessOverviewRedesignTest`: Tests for six financial summary cards, Driver/Operator donut charts, and Style 4 swipe-to-reveal actions (including foreground hit-testing regression).
- `com.example.ExpenseReportRedesignTest`: Tests for Fleet Expenses header, removal of decorative hero banner, filter controls, and category breakdown wave fill animations.
- `com.example.ExpenseRedesignUiTest`: Tests for Expense list item row, consolidated single header, shortened reference ID copying, and permission gating.
- `com.example.ResponsiveLayoutUiTest`: Matrix tests for 5 screen widths (320dp–480dp), 3 font scales (1.0x–1.30x), and English/Tamil localization.
- `com.example.ui.WorkspaceBannerStorageTest`: Storage isolation, persistence, and default restoration tests for workspace banners.

---

## 3. Compose UI Test Structure

All Compose UI tests use Robolectric and `createComposeRule()` configured for API 36 with `LedgerUiTestApplication`:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", application = LedgerUiTestApplication::class)
class MyComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun verifyComponentBehavior() {
        compose.setContent {
            MyApplicationTheme {
                MyComposable(...)
            }
        }
        compose.onNodeWithText("Expected Title").assertIsDisplayed()
        compose.onNodeWithTag("btn_action").performClick()
    }
}
```

---

## 4. Test Data & Fake Repository Conventions

- **Deterministic Fixtures**: All tests construct explicit, immutable domain entities (`JobEntryEntity`, `ExpenseEntity`, `AppSettingsEntity`).
- **No Live Firebase**: Automated tests must never connect to live Firestore or live Google Auth.
- **Zero Real Data Mutation**: Tests use fake callbacks and verify invocation arguments without writing to production storage.
- **Consistent Timestamps**: Use fixed epoch timestamps (e.g. `1700000000000L`) rather than `System.currentTimeMillis()` so assertions never drift.

---

## 5. How to Run Tests

### Running Individual Test Classes
```powershell
# Run Home Dashboard tests
.\gradlew.bat :app:testDebugUnitTest --tests com.example.HomeDashboardRedesignTest

# Run Business Overview tests
.\gradlew.bat :app:testDebugUnitTest --tests com.example.BusinessOverviewRedesignTest

# Run Expense Report tests
.\gradlew.bat :app:testDebugUnitTest --tests com.example.ExpenseReportRedesignTest

# Run Responsive Layout tests
.\gradlew.bat :app:testDebugUnitTest --tests com.example.ResponsiveLayoutUiTest

# Run Expense Redesign tests
.\gradlew.bat :app:testDebugUnitTest --tests com.example.ExpenseRedesignUiTest
```

### Running All UI Redesign Tests Together
```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests com.example.HomeDashboardRedesignTest `
  --tests com.example.BusinessOverviewRedesignTest `
  --tests com.example.ExpenseReportRedesignTest `
  --tests com.example.ResponsiveLayoutUiTest `
  --tests com.example.ExpenseRedesignUiTest `
  --tests com.example.ui.WorkspaceBannerStorageTest
```

### Running Partitioned CI Suites
```powershell
# Compose UI partition
.\gradlew.bat :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=compose

# Unit tests partition
.\gradlew.bat :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=unit

# Database partition
.\gradlew.bat :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=database
```

---

## 6. Animation Testing Approach

Jetpack Compose animations (such as the 1000ms New Job card color transition and the 700ms Expense Breakdown wave fill) are tested deterministically using Compose test clock control:

```kotlin
compose.mainClock.autoAdvance = false

compose.setContent {
    JobEntryItemCard(..., isNewlyCreated = true, onAnimationComplete = { completed = true })
}

// t = 0ms: animation in progress
assertFalse(completed)

// Advance clock by 500ms: midpoint
compose.mainClock.advanceTimeBy(500L)
assertFalse(completed)

// Advance clock to completion: 1000ms
compose.mainClock.advanceTimeBy(500L)
compose.waitForIdle()
assertTrue(completed)
```

This prevents flaky sleeps, eliminates wall-clock dependencies, and guarantees tests finish in milliseconds.

---

## 7. Responsive Layout Testing

The application supports diverse Android form factors from compact 320dp phones up to large displays. Responsive tests simulate these dynamically using `CompositionLocalProvider(LocalDensity provides ...)`:

- **Target Widths**: `320dp`, `360dp`, `393dp`, `412dp`, `480dp`.
- **Font Scales**: `1.0f` (standard), `1.15f` (accessible medium), `1.30f` (accessible large).
- **Languages**: English (`EN`) and Tamil (`TA`).
- **Assertions**: Verify that badges wrap using `FlowRow`, metadata cards stack when `maxWidth < 290dp` or `fontScale >= 1.2f`, and no horizontal clipping occurs.

---

## 8. Physical-Device-Only Checks

Certain characteristics require hardware validation:
1. **120Hz Touch Responsiveness**: Validating smooth swipe gestures on `SwipeableRecordCard` without frame drops.
2. **System Insets & IME**: Verifying `.statusBarsPadding()` and bottom navigation padding flush against real device punch-holes and camera cutouts.
3. **USB Hotplug & Sleep**: Ensuring app state survives display sleep/wake.
4. **Clipboard Toast**: Verifying native system Toast display upon copying Expense Reference ID.

---

## 9. Feature Coverage Matrix

| Feature | Automated Coverage | Manual Coverage | Status |
|---|---|---|---|
| **Today's Job Entries** | Initials avatar, customer name, amount badge, machinery icon, duration, click callback, long name wrapping | Physical phone appearance on vivo I2304 | **PASS** |
| **New Job Animation** | Initial red highlight, 500ms transition, 1000ms completion, recomposition stability | 60fps video capture on real device | **PASS** |
| **Workspace Banner** | Default fallback, custom saving, per-workspace isolation, local persistence | Photo Picker gallery integration | **PASS** |
| **Business Overview Tiles** | 6 summary card labels, formatted amounts, Expense & Withdrawal navigation callbacks | Physical scrolling & dark/light theme | **PASS** |
| **Driver Donut Charts** | 3 member slices, Collected/Due labels, amount selector toggle, legends | Touch rotation on physical screen | **PASS** |
| **Swipeable Records** | Normal tap navigation, swipe reveal, View & Share callbacks, foreground hit-testing fix | Physical swipe inertia & velocity | **PASS** |
| **Expense Report** | Header, back button callback, Add Expense button, removal of hero banner, summary cards | Real device screen capture | **PASS** |
| **Expense Breakdown** | 4 compact tiles, horizontal scroll discoverability, wave fill, 0% & 100% bounds, 700ms fill animation | Visual organic wave aesthetics | **PASS** |
| **Expense Entry Card** | Mint category container, tractor reference, Business Paid / Shared badges, pale-red amount, overflow menu | Live record display on vivo I2304 | **PASS** |
| **Expense Details** | Single consolidated header, summary card wave accent, shortened ID copy, structured metadata grid, delete button | Clipboard integration on Android 16 | **PASS** |
| **Responsive Layouts** | 5 widths × 3 font scales × 2 languages matrix tests | Layout validation on 412dp hardware | **PASS** |

---

## 10. How to Add New Tests for Future UI Changes

1. Place new test files in `app/src/test/java/com/example/`.
2. Follow the naming convention `*Test.kt`. If the test uses Compose rules, it will automatically run in the `compose` CI partition.
3. Use `MyApplicationTheme` as the outer wrapper.
4. If testing localized text, test both English and Tamil equivalents.
5. If adding clickable UI elements, run `python scripts/interaction_inventory.py` to regenerate the interaction manifest.
