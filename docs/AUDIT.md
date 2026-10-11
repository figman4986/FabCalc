# FABCalc Codebase Audit

Audit date: 2026-10-11
Audited tree: FabCalc Android project source snapshot
Method: direct inspection of Gradle files, manifest, source, resources, and tests; JVM unit tests run locally.

## 1. Executive Summary

**Critical scope discrepancy:** despite the task title and expected Weld Setting Log feature list, the checked-out source is **FabCalc**, an offline shop-floor calculator with a welding-wire datasheet PDF library and calculation history. The app name resource is `FabCalc`. There is no weld-setting logbook implementation. Specifically absent are weld entries, photo capture/storage, offline OCR, EAV/custom-field definitions and values, welder/machine memory, date-grouped weld entries, logbook filters/sort, and orphan-photo sweeping. The only welding-specific feature is a locally imported PDF datasheet library. No phase notes or documentation were present to reconcile the discrepancy; source is treated as authoritative.

The repository contains one Android app module using Kotlin/Compose, manual dependency injection, Room for calculation history and wire datasheet metadata, and DataStore Preferences for settings. The Room database is schema version 3 with explicit migrations 1→2 and 2→3. No `fallbackToDestructiveMigration` call was found. The manifest declares a single exported launcher activity and no permissions, including no network permission. Source/dependencies show no networking client or network use.

Baseline verification before corrections: `bash gradlew :app:testDebugUnitTest` completed `BUILD SUCCESSFUL`; generated reports show 128 tests, 0 failures, 0 errors, 0 skipped. `bash gradlew :app:connectedDebugAndroidTest` completed successfully with both instrumented classes passing (2 tests total), including v2→v3 migration and history preservation. `bash gradlew :app:assembleDebug` and `bash gradlew :app:assembleRelease` both passed. Although release emitted a warning that it would be unsigned, APK certificate inspection showed `app-release.apk` was signed by the Android Debug certificate (SHA-256 `fdbef4504ef89bbd37179aedbae1cbd62ff93cf5d223c14e5c9ef6c5c7ca94c4`).

**Top five risks / decisions:**

1. The implementation does not match the intended Weld Setting Log product, so any weld-log test plan cannot be applied to this tree until the correct source/repository is identified.
2. Room schema v3 is now exported and the v2→v3 history-preservation instrumented test passes on emulator; a v1→v3 device-upgrade path is not independently covered.
3. Datasheet deletion now aborts if an existing PDF cannot be removed and restores the PDF if Room row deletion fails. Tests cover both. Cross-store process death during the delete transaction remains a residual row/file consistency risk.
4. Room insert failures now remove the staged PDF; cancellation leaves it for the 24-hour startup sweep. Both outcomes have emulator test coverage.
5. User-visible history deletion/clear is permanent and has confirmation dialogs. History is intentionally unbounded and there is no in-app export/restore feature. Explicit cloud-backup/device-transfer includes now cover the Room DB, DataStore file, and wire PDF directory. Emulator `bmgr backupnow com.figapps.fractioncalculatorv1` returned “Backup is not allowed”; end-to-end backup and restore therefore remain unverified.

## 2. Architecture Diagram and Module Map

```text
Android process
└── FractionApp (Application)
    └── lazy AppContainer (manual DI; one instance)
        ├── SettingsRepository ── DataStore Preferences (settings.preferences_pb)
        ├── HistoryRepository ───┐
        └── WireDatasheetRepository ─┴─ HistoryDatabase / Room (history.db, v3)
             └── PDFs in filesDir/wireDatasheets/<uuid>.pdf

MainActivity
└── AppRoot
    ├── LocalAppContainer CompositionLocal
    ├── collects AppSettings; applies theme / keep-screen-on window flag
    └── FractionCalculatorApp (single-activity Compose shell; enum-based destinations)
        ├── tool screens: Fraction Calculator, Offset Calculator, Circle Divider,
        │                 Unit Converter, Bend Allowance, Wire Library
        └── overlays: History, Settings; About dialog
```

### Project/module map

| Path/package | Responsibility |
|---|---|
| Root Gradle project | `settings.gradle.kts` includes only `:app`; dependency/plugin repositories are Google, Maven Central, and Gradle Plugin Portal for plugins. |
| `app/src/main/java/.../calculator` | Calculator key events, mutable Compose-backed ViewModel state, fraction-expression evaluation. |
| `.../domain` | Exact rational math, measurement parsing, formatting/precision, offset/circle/bend calculations, diagram label layout, constants and operators. Mostly Android-free. |
| `.../domain/convert` | Unit catalog and exact factor/affine conversion engine; sheet-gauge lookup tables and formatting. |
| `.../history` | Room history entity/DAO/database/migrations/repository and recorder abstraction. |
| `.../settings` | Manual `AppContainer`, immutable `AppSettings`, DataStore-backed preferences repository. |
| `.../wire` | Wire datasheet Room entity/DAO/schema SQL, PDF repository, SHA-256 de-duplication, search/filter/sort, size normalization. |
| `.../ui` | Compose root/navigation shell and six tool screens, history/settings screens, PDF viewer. |
| `.../ui/components`, `.../ui/theme` | Reusable calculator controls, diagrams/annotation rendering, colors and Material theme. |
| `app/src/test` | JVM tests for pure math, formatting, converter/catalog, wire library pure helpers and schema DDL, plus saveable key uniqueness. |
| `app/src/androidTest` | Package-context smoke test and Room migration integration test. |

### Gradle, SDK, manifest, and dependencies

- One module: `:app`. No product flavors are configured. Build types are the Android defaults with a customized `release` block.
- Plugin versions from `gradle/libs.versions.toml`: AGP 9.4.1, Kotlin Compose plugin 2.2.10, KSP 2.2.10-2.0.2. Room 2.8.5, Compose BOM 2026.02.01, DataStore 1.2.1. Gradle wrapper distribution is configured in `gradle/wrapper/gradle-wrapper.properties`.
- `compileSdk = 37`, `minSdk = 24`, `targetSdk = 37`; Java source/target compatibility 11. No explicit Kotlin JVM target is configured in the module.
- `applicationId`/namespace: `com.figapps.fractioncalculatorv1`; versionCode 1, versionName 1.0.
- Manifest: `.FractionApp` application, one `.MainActivity`, exported true with MAIN/LAUNCHER filter. No other components and no permissions. In particular, no INTERNET, camera, storage, or media permission.
- Release signing secrets are read from `local.properties` or environment. Release tasks fail configuration if required signing values are absent; a debug-signing fallback is not configured. Release minification remains disabled. A signed release was verified with the configured release certificate; R8 enablement remains deferred until the full instrumented suite and mapping-file retention conditions are satisfied.
- Declared runtime dependencies: Compose BOM, Activity Compose, Material3, Compose UI/graphics/tooling-preview, Core KTX, Lifecycle runtime KTX and ViewModel Compose, Material Icons Core, DataStore Preferences, Room runtime and Room KTX; Room compiler via KSP.
- Declared tests: JUnit 4, AndroidX JUnit, Espresso Core, Compose UI test JUnit4 and debug test manifest. `androidx.compose.ui.test.junit4`/Espresso are unused by current instrumented test source; `androidx.lifecycle.runtime-ktx` appears unnecessary for current code, which uses ViewModel Compose. These are candidates for dependency cleanup, not confirmed dead transitive artifacts. `buildConfig = true` is needed by the About dialog.
- There is no version catalog dependency for `androidx.sqlite` explicitly; Room supplies the needed API transitively.
- `app/src/main/keepRules/rules.keep` is only the default commented template; no custom R8 rules are present.

### DI, persistence, and startup behavior

- DI is manual, not Hilt: `FractionApp.container` is a lazy singleton; `AppContainer` constructs one settings repo, one history repo, and one wire repo around a singleton `HistoryDatabase`. Compose gets it through `LocalAppContainer`.
- Room entities: `HistoryEntry` table `history`; `WireDatasheet` table `wire_datasheets`. Version 3. DAO APIs observe/insert/find/delete/clear history and observe/insert/update/delete/find/count wire rows.
- Migrations: 1→2 adds `history(timestamp)` and `history(toolId)` indices; 2→3 creates wire table plus unique `sha256` and `metalType` indices via `WireDatasheetSchema.ALL`. `ALL_MIGRATIONS` is registered. No destructive fallback was found.
- Room schema export is enabled and v3 is checked in under `app/schemas`; KSP is configured for Room schema output and the schema directory is on androidTest assets. Migration SQL has a JVM structural test and v2→v3 history preservation is verified on emulator.
- DataStore persists precision, enabled/active length units, history-enabled, theme, haptics, keep-screen-awake, diagram visibility, and per-converter unit pairs. An `IOException` in DataStore flow emits empty/default preferences; other errors rethrow.
- `FractionApp.onCreate` launches the aged, unreferenced wire staging file sweep on an application IO coroutine. The container and Room database are constructed lazily when accessed. No OCR or photo processing exists.

## 3. Feature Inventory

| Feature | Status | Entry point / key classes | Test coverage |
|---|---|---|---|
| Fraction calculator | Implemented | Tool menu → `CalculatorScreen`, `CalculatorViewModel`, `CalculatorState`, `Fraction`, `Operator`, keypad/display components. Exact rational input; left-to-right evaluation; π constant; result history. | `FractionTest`; no direct ViewModel/key-transition tests. |
| Offset calculator | Implemented | `OffsetCalculatorScreen`, `MeasurementParser`, `OffsetMath`, `OffsetDiagram`. Circumference/diameter input, units, radial offset, swap warning, explanation and diagram. | `OffsetMathTest`, `SettingsAndMeasurementTest`; no Compose screen test. |
| Circle divider | Implemented | `CircleDividerScreen`, `CircleDividerMath`, `CircleDividerDiagram`. N equal divisions, start angle, points/chord/arc/areas, optional bolt pattern. | `CircleDividerMathTest`; no gesture/UI test. |
| Unit converter | Implemented | `UnitConverterScreen`, `ConverterCatalog`, `ConverterEngine`, `ConverterFormat`, `SheetMetalGauge`. Length, mass, temperature, pressure, force, torque, area, volume, angle, and gauge tables. Remembers unit pairs. | `ConverterMathTest`, `ConverterFormatTest`, `SheetMetalGaugeTest`; no UI/persistence test. |
| Bend allowance / flat pattern | Implemented | `BendAllowanceScreen`, `BendAllowanceMath`, `BendDiagram`; multi-bend chain and leg-length cross-check. | `BendAllowanceMathTest`; no UI-state/validation test. |
| Wire datasheet PDF library | Implemented | Tool menu → `WireLibraryScreen`, `WireDatasheetRepository`, Room entity/DAO, `WireLibraryFilter`, `WireViewerScreen`. OpenDocument picker, PDF header check/copy/hash, metadata tags, filters, viewer/pinch zoom, edit/delete. Local/offline. | Hash, duplicate, filter, size, migration-DDL JVM tests; 15-case emulator repository/filesystem/Room suite. No Compose UI tests. |
| Calculation history | Implemented | History overlay via app bar/menu; `HistoryScreen`, `HistoryDetailDialog`, `HistoryRepository`, `HistoryDao`. Per-tool groups; detail; delete one or clear all. | No repository/DAO/UI history behavior test. Migration integration test verifies history preservation on emulator. |
| Settings | Implemented | Settings overlay → `SettingsScreen`, `SettingsRepository`, DataStore. Precision, unit availability, history toggle, theme, haptics, keep-awake. | Only `AppSettings.primaryUnit` fallback and measurement validation are covered; DataStore read/write/corruption not tested. |
| Welder display OCR | **Absent** | No OCR library/dependency, scanner route, camera component, or OCR code. | None. |
| Weld entry logging with photos | **Absent** | No weld-entry screen/entity, camera/picker flow for photos, photo lifecycle, or photo references. | None. |
| EAV custom weld fields / custom-field manager | **Absent** | No field definitions/values tables or management UI. Wire PDF tags are fixed schema fields, not EAV. | None. |
| Weld entries grouped by day; multi-token search; machine/process/date filters; sort toggle; entry detail/edit; machine memory | **Absent** | No corresponding logbook route/domain model. Wire library search only matches one trimmed whole query against brand/product/AWS; filters are single-select metal/process; rows group by metal. | `WireLibraryFilterTest` only covers this wire-library behavior. |
| Date range presets/custom range, `SearchQuery`, `EntryGrouping`, `DateRangePresets`, `OrphanPhotoSweep`, empty-state selector | **Absent** | No classes by these names or equivalent weld-log functionality found in source. | None. |

**Skeleton check:** there is no weld-log skeleton in this tree. The features actually present are implemented screens/tools; `ExampleUnitTest` and `ExampleInstrumentedTest` are generated smoke tests.

## 4. Data-Loss Risk Register

No photo data exists in this FabCalc tree. An age-gated startup sweep now deletes only unreferenced PDF staging files older than 24 hours. Ranked paths below concern calculation history and welding-wire PDFs/metadata.

| Rank | Path / trigger | Data affected and behavior | Assessment |
|---|---|---|---|
| 1 | History screen → long-press row → confirm Delete; Settings or History → confirm Clear history | `HistoryRepository.delete/clear` → Room `@Delete` / `DELETE FROM history`. Permanently deletes one/all calculation records. Both clear entry points show confirmation; single delete also confirms. | Intentional user-authorized destructive action, but no undo/export. A tap or state/automation error after confirmation loses history. High impact, explicit confirmation mitigates. |
| 2 | Wire library → delete datasheet, or viewer error → Remove | Repository backs up a regular PDF to a staging file, deletes the original and removes the Room row; a Room error restores the PDF before propagating. A missing file is a repair path that removes the stale row. | Resolved behavior is covered by emulator tests: delete-false aborts before Room deletion; Room failure leaves row and restores PDF; missing-file row is removed. Cross-store process death during the delete transaction remains a residual risk because filesystem and SQLite are not atomic. UI now reports delete errors and returns to the library. |
| 3 | Import duplicate detected | `stagePdf` hashes staged copy; when a matching row exists it deletes only the new staged target and retains existing record/file. | Intended deduplication, not existing data loss. If an existing row already references a missing file, duplicate handling does not repair it. |
| 4 | Import exception while copying / process death before commit | `stagePdf` copies into `.staging-<UUID>.pdf`, verifies reported byte length, and removes the partial target on copy failure. `FractionApp.onCreate` starts the approved 24-hour sweep. | AndroidTests confirm thrown/short streams clean staging and preserve source. DB reference-query failures disable the sweep. Cancellation is still caught by existing broad `Throwable` handling. |
| 5 | Cancel tag dialog | `repository.discard(staged.file)` deletes the staged, not-yet-committed copy. | Intended cancellation cleanup; there is no user data row yet. Failure to delete can leak private storage. |
| 6 | Process death between stage and `commit`/`discard` | Staged file exists as `.staging-<UUID>.pdf` in `filesDir/wireDatasheets`, not referenced by Room until commit. Startup sweep checks both age and DB references. | Old unreferenced stages are removed; recent or referenced files remain. Any DB open/query failure skips all deletion. A failed commit can leave an unreferenced stage until it passes the 24-hour gate. |
| 7 | Room migration/open for unsupported or broken path | Only migrations 1→2 and 2→3 are registered; no fallback. An unsupported historical version or migration/validation error prevents opening rather than silently wiping. | Correct no-destructive-fallback posture. Schema v3 exported; v2→v3 history preservation passed on emulator. v1 path remains unverified on a real shipped v1 database. |
| 8 | Android uninstall, clear storage, device loss, backup/restore | App-private Room DB, DataStore, and PDFs are subject to OS app-data lifecycle. Explicit rules include database, preferences, and `wireDatasheets/`. | API 36 LocalTransport returns “Backup is not allowed” for the app, so end-to-end backup/restore remains unverified. No in-app export/recovery path. |

**Explicit deletion-site inventory:** `HistoryDao.clear()`; `HistoryRepository.delete/clear()`; confirmation actions in `HistoryScreen` and `SettingsScreen`; `WireDatasheetRepository.stagePdf` failure and duplicate cleanup; `discard`; datasheet `delete`; startup staged-file sweep; instrumented test DB/table/file cleanup (test-only). No destructive Room migration exists.

**Resolved P0 findings with test evidence:** (1) Room insert failure deletes the staged PDF, preserves the thrown database error, and leaves no row; an injected `CancellationException` propagates without cleanup, leaving the staged file for the startup sweep. (2) `File.delete()==false` throws before Room deletion; the row and path remain. (3) If Room row deletion fails after the original PDF is removed, the repository restores the PDF and propagates the error; the row remains. (4) A missing PDF remains a repair path that removes the stale row. Each has an emulator test. The normal default ContentResolver path is separately exercised. Cross-store process death in the brief interval between removing the PDF and completing the Room transaction remains a residual risk; the staged copy is a recovery copy, but automatic crash-recovery reconciliation is not yet implemented.

## 5. Test Coverage Matrix

### JVM test files (17 files, 136 passing tests)

| Test class/file | Count | Purpose |
|---|---:|---|
| `BendAllowanceMathTest` | 12 | Bend allowance/setback/deduction formulas, extremes, chain totals, validation, cross-check, geometry, unit agnosticism, presets. |
| `CalculatorViewModelTest` | 8 | Calculator input focus, implicit denominator, invalid digit rejection, operator replacement, left-to-right evaluation, divide-by-zero, clear, π, and nine-digit limit. |
| `CircleDividerMathTest` | 12 | Diameter conversion, geometric relationships, division bounds, start-angle wrapping, trig rounding/non-finite conversion. |
| `ConverterFormatTest` | 7 | Number formatting, tiny/large values, inch fractions, feet/inches, DMS. |
| `ConverterMathTest` | 11 | Exact conversion factors, affine temperature conversion, round trips across categories/ranges/defaults. |
| `DiagramLayoutTest` | 13 | Candidate/clamp/overlap/segment geometry and label placement, leader routing, obstacles. |
| `DuplicateDecisionTest` | 7 | Pure SHA-256 duplicate decision, case normalization, tags independence. |
| `ExampleUnitTest` | 1 | Generated arithmetic smoke test (`2 + 2`). |
| `FractionTest` | 10 | Rational normalization/arithmetic/decimal parsing/output, mixed numbers, divide-by-zero, operators, π. |
| `OffsetMathTest` | 10 | Offset conversion/computation, swapped/identical measurements, parser and formatter, rounding. |
| `ResultFormatTest` | 12 | Fraction/decimal rounding, HALF_UP ties, exact notes, mixed/fraction output. |
| `SaveableStateKeyTest` | 2 | Stable/unique tool and overlay keys for `SaveableStateHolder`. |
| `SettingsAndMeasurementTest` | 7 | Positive measurement validation and `AppSettings.primaryUnit` fallback/default. |
| `SheetMetalGaugeTest` | 7 | Gauge range/table values, material differences, exact mm derivation. |
| `WireDatabaseMigrationTest` | 8 | Structural reflection/DDL checks for v2→v3 entity columns/nullability/indices/idempotency. |
| `WireHashingTest` | 7 | SHA-256 known vectors, determinism, change sensitivity, lowercase hex, streaming equivalence. |
| `WireLibraryFilterTest` | 18 | Metal/process/search filtering, combined predicates, order and grouping. |
| `WireSizeNormalizationTest` | 11 | Common/custom unit normalization, display, and round trips. |

The original suite had 128 tests across 16 reports. WS-3 added six green JVM tests and the approved decision follow-ups add two tests (numerator-only implicit denominator and invalid-digit rejection), for 136 passing tests across 17 reports.

### Instrumented tests (5 files; emulator executed)

| Test class/file | Purpose | Execution status |
|---|---|---|
| `ExampleInstrumentedTest` | Asserts target package name. | Emulator PASS (1 test). |
| `WireDatabaseMigrationInstrumentedTest` | Builds a v2 SQLite database with a history row, opens through production migrations, verifies history preservation and wire-table insert/count on device. | Emulator PASS (1 test). |
| `HistoryRepositoryInstrumentedTest` | In-memory Room tests for record/observe/find present+absent, equal-timestamp ordering, single delete, clear, and Flow updates. | Emulator PASS (3 tests). |
| `SettingsRepositoryInstrumentedTest` | Real serialized DataStore round-trip, all settings fields, unknown enum/unit coercion, active-unit fallback, rapid sequential writes. | Emulator PASS (1 scenario). IOException fallback branch remains untested; see WS-2 note. |
| `WireDatasheetRepositoryInstrumentedTest` | 16 real Room/filesystem tests: default ContentResolver path, valid/non-PDF/missing/invalid import, thrown/short streams, duplicate retention, commit insert failure and cancellation, file+row deletion, missing-file deletion, false File.delete outcome, transaction rollback/restoration, cleanup naming/age/reference/DB-failure behavior. | Emulator PASS (16 tests). |

An earlier supplied context said five instrumented suites had never executed; the original source then contained two suites. The repository, history, and settings suites are now also present, for five instrumented test classes total. No Compose test API (`createComposeRule`, `createAndroidComposeRule`, `StandardTestDispatcher`, immediate test dispatcher) is used in these tests.

### Coverage map / gaps

- Covered pure domain: `Fraction`, `ResultFormat`, `MeasurementParser` subset, `OffsetMath`, `CircleDividerMath`, `BendAllowanceMath`, `DiagramLayout`, converter math/format/catalog, gauge tables, wire filter/hash/size, duplicate decision, and structural migration SQL.
- No tests: history grouping/timestamp generation, the `SettingsRepository` IOException catch branch, `WireTags` transformations, PDF renderer/viewer lifecycle, or Compose UI interactions/error presentation. Numerator-only and invalid-digit contracts now have JVM tests.
- Wire-library query behavior is not comprehensively covered; the device migration test checks migration plus a simple insert/count. No test covers the production DB singleton/open path or an upgrade from v1 to current.
- Date/time-zone and DST audit for weld-log date grouping is not applicable because no such date logic exists. History timestamp rendering uses default locale/time zone (`DateFormat.getDateTimeInstance`) and has no timezone/DST tests.
- Saveable slot key uniqueness is covered, but actual restoration of screen state through rotation/process death is not. Many Compose state values use `rememberSaveable`; some dialog/import/viewer state uses plain `remember` and is intentionally transient or could be lost on recreation. No UI-state restoration test exists.
- The full build is not established by the unit-test task alone. User context says debug/release assemble passed previously; this audit did not independently run assemble tasks.

## 6. Proposed Test Plan

Ordered by data-loss risk first, then failure likelihood and current coverage gaps. The plan below is for the **actual FabCalc code**, not the absent Weld Setting Log. If the intended codebase is provided, create a separate weld-log plan covering its actual data model.

### P0: data retention and storage consistency

1. Stage/import AndroidTests cover valid/non-PDF/missing/invalid URI, copy exception and short stream, duplicate detection, source preservation, no row on failed imports, default Android ContentResolver behavior, Room insert failure cleanup, and cancellation preservation.
2. Delete tests cover success, missing file, `File.delete()==false` abort, and DAO transaction failure after file removal with restoration. Delete failures are surfaced to the caller and the UI reports them.
3. Approved staged-file cleanup recognizes `.staging-<UUID>.pdf` import files and `.staging-delete-<UUID>.pdf` rollback copies. Both use the same 24-hour gate and Room reference check. Tests cover malformed names, referenced backups, fresh files, and DB-check failure.
4. Execute database migration integration on a device/emulator. Cover fresh create, 1→2, 2→3, and (if historical schema v1 is supported) direct v1→3 path, preserving multiple history rows and index/query behavior. Keep destructive migration prohibited.
5. Schema export is now enabled and schema v3 snapshot checked in. Add automated migration verification in CI and confirm production upgrade from every shipped schema is represented.
6. Decide and test backup behavior for Room, DataStore and PDF directory as a coherent set. If user data is expected to survive device transfer, verify that DB metadata and associated files restore together; if not, explicitly document/disable backup rather than relying on template rules.

### P1: existing app correctness with no current tests

1. History DAO/repository tests now cover insertion, Flow emissions, find-by-id, delete, clear, and equal-timestamp order on the emulator. Query orders by timestamp only; current emulator returns newer insertion first, but SQL does not explicitly specify a secondary tie-break, so deterministic order across SQLite versions remains a review point.
2. Settings DataStore tests now cover all AppSettings fields, unknown enum/unit values, empty-unit fallback, active-unit coercion, repository recreation, and rapid sequential writes. The IOException-to-defaults branch is not directly covered: the repository hardwires a process-wide preferencesDataStore delegate initialized by the app Application; injecting an IOException after initialization would require a production seam or a fresh target process. The test did not corrupt the app's live preferences file to force this branch.
3. Calculator tests cover focus, numerator-only implicit denominator, invalid digit rejection, operator replacement, left-to-right evaluation, divide-by-zero, clear, π and max digits. The approved numerator-only decision matches existing behavior. The approved invalid-digit guard now rejects values outside 0–9. Remaining transition edges (sign behavior, decimal input, empty-operator) are still untested.
4. Add Compose UI tests for each destructive confirmation, PDF import/cancel/duplicate flows, edit tags, viewer error/remove, history grouping/detail/delete/clear, and history-disabled retention semantics.
5. Add rotation/process recreation tests for selected tool, inputs, selected filters/viewer page, active dialog/import state, and settings overlay. Distinguish durable model state from intentionally transient dialog state.
6. Test PDF renderer open/close behavior, missing/corrupt files, rapid page changes, cache limits, zoom rerender, and cancellation/disposal while rendering.

### P2: defensive coverage and release verification

1. Property/boundary tests for extreme `BigInteger` input sizes, fraction/decimal rounding boundaries, negative zero, malformed mixed numbers, maximum circle divisions/start angles, bend angle 180° and near-boundary trig.
2. Validate published converter factors and sheet-gauge tables against pinned authoritative source data; tests currently verify selected values and internal round trips, not every source value.
3. Add multi-locale formatting checks for `DateFormat`, wire sort normalization, and decimal keyboard/input behavior.
4. Run `:app:assembleDebug`, `:app:assembleRelease`, lint, JVM tests, and androidTest as separate CI gates; verify release signing and R8 policy. The current successful unit-test task is not evidence that release packaging works.
5. Add static policy checks that there is no network permission, networking dependency/client, or accidental external request in this offline-only product.

### If FABCalc/Weld Setting Log was intended instead

Once the correct source tree is available, first tests should prioritize Room entity/DAO/migration preservation; EAV custom-field serialization and search; photo capture/copy/commit/delete and orphan sweep age-gate tests; OCR parser confidence/units/range behavior on representative display images; multi-token search across built-in/custom fields; date boundaries across DST/time zones; filter/sort/grouping combinations; edit/detail/rotation restoration; and all destructive paths. None can be specified against the current source because the corresponding implementations are absent.

## 7. Open Questions for the User

1. Is this the intended repository? The app source is FabCalc (fraction/shop calculator) with a wire PDF library, not the Weld Setting Log originally described. If incorrect, the audit should be run against the intended source tree.
2. Should `history.db`, DataStore settings, and wire PDF files be included in Android backup/device transfer? Rules include them, but the emulator transport refused app backup, so paired DB/file restore is unverified.
3. What retention guarantee is required for calculation history and imported datasheets? Current deletion is permanent with confirmation, no export/undo exists, and OS uninstall/clear-data removes private storage.
4. Should R8 optimization remain disabled until device coverage and mapping-file retention requirements are met? It is currently disabled pending those gates.

## 8. Baseline Corrections Follow-Up

### Phase 1 baseline results (before source/config corrections)

| Command | Result |
|---|---|
| `bash gradlew :app:connectedDebugAndroidTest` | **PASS**, 2 tests, 0 failures/errors/skips on an API 36 emulator. `ExampleInstrumentedTest.useAppContext` passed (1); `WireDatabaseMigrationInstrumentedTest.migration2To3_createsTheWireTableAndPreservesHistory` passed (1). |
| `bash gradlew :app:testDebugUnitTest` | **PASS**, 128 tests, 0 failures/errors/skips (all 16 JVM reports). |
| `bash gradlew :app:assembleDebug` | **PASS**. |
| `bash gradlew :app:assembleRelease` | **PASS, unsafe prior behavior found**: warning claimed unsigned, but `apksigner verify --print-certs` identified Android Debug signer SHA-256 `fdbef4504ef89bbd37179aedbae1cbd62ff93cf5d223c14e5c9ef6c5c7ca94c4`. Release fallback has been removed; now expects a clean configuration failure without credentials. |

The generated connected-test XML report records the two per-class results above. The final post-correction run regenerated the same result: 2 total, no failures/errors/skips.

### Phase 2 file changes

- `app/src/main/res/xml/backup_rules.xml`: explicit legacy full-backup inclusions for Room database files (`history.db`, `history.db-wal`, `history.db-shm`), DataStore preferences (`datastore/settings.preferences_pb`), and `wireDatasheets/`.
- `app/src/main/res/xml/data_extraction_rules.xml`: same explicit includes under both `cloud-backup` and `device-transfer` for Android 12+.
- `app/build.gradle.kts`: release tasks throw `GradleException` when required signing settings are absent; removed debug signing fallback. With secrets, release is signed by configured release key. Release minification remains disabled.
- `app/src/main/keepRules/rules.keep`: tracked TODO requires full instrumented suite green before considering R8, plus release smoke-test gate and preservation/upload of each `mapping.txt`.
- `README.md`: contributor signing-secret setup for `local.properties`/environment, optional PKCS12 key password, and backup caveat.
- `docs/AUDIT.md`: baseline results and backup verification status recorded.
- Post-correction: `bash gradlew :app:testDebugUnitTest` passed with 128 tests and zero failures/errors/skips; `bash gradlew :app:connectedDebugAndroidTest` passed with 2 tests and zero failures/errors/skips; `bash gradlew :app:assembleDebug` passed. `bash gradlew :app:assembleRelease` now fails at configuration as intended without release credentials, naming the required settings. A signed release was later verified with the configured release certificate.
- Offline policy check after changes: no INTERNET permission or networking dependencies/clients were introduced; no Room migration code was changed.

### Backup verification attempt

The emulator exposes `com.android.localtransport/.LocalTransport`; `bmgr backupnow com.figapps.fractioncalculatorv1` ran but returned `Backup is not allowed`. The backup service therefore did not produce an app-data backup/restore proof. Rules are explicit, but both capture and restore semantics for the database/PDF pairing remain to be verified manually.

Manual procedure for a later physical device (or emulator configured to permit backup): install a debug build with identical package/signature on source and destination; enable/select an Android backup transport/account; create history and settings values and import at least one PDF; invoke `bmgr backupnow com.figapps.fractioncalculatorv1` and inspect `bmgr list sets`; restore the package onto a clean destination using `bmgr restore <token>` (or a supported device-transfer path); open History and Settings, then open the imported PDF in Wire Library; compare restored record counts/metadata and PDF hashes to source. Repeat for cloud backup and device-transfer because Android versions/transports may apply different policy.

## 9. P0 Follow-Up: Backup Retry, Failure Tests, and Schema Export

### Backup retry command log (emulator API 36)

Corrected installed-app verification in this phase: before any source edits, `pm list packages | grep figapps` initially produced no matching package, so the debug APK was installed (`Performing Streamed Install` / `Success`). Rechecking produced `package:com.figapps.fractioncalculatorv1`. Then `bmgr enable true` returned `Backup Manager now enabled`; `bmgr backupnow com.figapps.fractioncalculatorv1` returned `Running incremental backup for 1 requested packages. Package com.figapps.fractioncalculatorv1 with result: Backup is not allowed. Package @pm@ with result: Success. Backup finished with result: Success`. This confirms the installed package was refused by the active transport, unlike the previous “Package not found” attempt. No application backup dataset was created; restore and row/PDF verification could not be attempted. This is SDK/transport-deferred.

- `adb shell bmgr enable true` → `Backup Manager now enabled`.
- `adb shell bmgr enabled` → `Backup Manager currently enabled`.
- `adb shell bmgr list transports` → `* com.android.localtransport/.LocalTransport` (the asterisk marks the active transport; no other transport is installed).
- `adb shell bmgr backupnow com.figapps.fractioncalculatorv1` while the app was not installed → `Running incremental backup for 1 requested packages. Package ... with result: Package not found. Package @pm@ with result: Success. Backup finished with result: Success`.
- Installed `app/build/outputs/apk/debug/app-debug.apk` successfully, then repeated `bmgr backupnow`: `Package com.figapps.fractioncalculatorv1 with result: Backup is not allowed. Package @pm@ with result: Success. Backup finished with result: Success`.
- Explicit `bmgr transport com.android.localtransport/.LocalTransport`, app install, and `bmgr enable true` did not change app policy result. `dumpsys package` reported the app's `ALLOW_BACKUP` flag. Prior attempts to whitelist/activate app also did not produce app backup eligibility.
- A Google APIs/non-Play API 32 image was unavailable in the verification environment, so that alternative could not be tried. No transport-level app backup or restore occurred; do not infer data movement or restore correctness from the successful `@pm@` result.
- **Outcome:** backup/restore verification remains deferred. The local transport refuses backup for this package on this emulator image despite enablement, selection, installation, and manifest eligibility. No row/file restore validation is possible without a successful backup dataset.
- One-shot option check, `adb shell bmgr backupnow --offline com.figapps.fractioncalculatorv1` → `Running incremental backup for 2 requested packages. Package --offline with result: Package not found. Package com.figapps.fractioncalculatorv1 with result: Package not found. Backup finished with result: Success`. This API interpreted `--offline` as a package argument; it did not perform a backup.

### Repository failure-boundary tests

Constructor signature now accepts `PdfContentAccess`, defaulting to `AndroidPdfContentAccess(context.contentResolver)`. The adapter delegates URI stream and descriptor-length calls to the same real Android `ContentResolver` path; a passing AndroidTest imports a real temporary `file://` PDF using the default constructor. Other repository tests inject the interface to deterministically model missing, throwing, and short streams. The copy path now rejects bytes shorter/longer than a positive reported length, so truncated source documents are not accepted as successful imports.

The repository creates `.staging-<UUID>.pdf` import files and `.staging-delete-<UUID>.pdf` delete rollback copies; `WireDatasheetDao.referencedFileNames()` supplies the cleanup guard. `WireDatasheetRepository.sweepStagedFiles()` queries Room first, catches DB query/open failure by returning without deleting, filters the two UUID naming forms to regular files, checks the shared 24-hour age gate, then deletes only unreferenced candidates. `FractionApp.onCreate` launches this sweep on an application IO scope. Existing migration declarations and fallback policy were not modified.

`app/src/androidTest/java/com/figapps/fractioncalculatorv1/WireDatasheetRepositoryInstrumentedTest.kt` now contains 16 passing emulator tests. It injects `PdfContentAccess`, not a manifest provider or mocking framework; filesystem paths and Room transactions are real. A separate test exercises the default resolver adapter through the actual Android `ContentResolver` using a `file://` URI. The seam abstracts input/length operations because framework `ContentResolver` methods are final.

Instrumented repository cases, one-line purpose each: `validPdfStagesCopyWithoutDeletingSource` (copy/source/no-row checks); `defaultResolverDelegatesToRealAndroidContentResolver` (default-path neutrality); `nonPdfContentIsRejectedWithoutRowOrStagedFile` (invalid content cleanup); `missingAndInvalidUrisLeaveNoStageOrRoomRow` (URI resolution failure); `streamThrowingMidCopyCleansStageAndLeavesSourceAndRoomUntouched` (copy exception cleanup); `shortStreamComparedWithReportedLengthCleansStageAndLeavesSourceAndRoomUntouched` (truncation detection); `duplicateKeepsExistingRowAndPdfAndDeletesOnlyNewStage` (dedupe lifecycle); `roomInsertFailureDeletesStagedFileAndLeavesNoRow` (insert error cleanup); `cancellationDuringInsertPropagatesWithoutCleaningStagedFile` (cancellation/backstop semantics); `deleteExistingFileAndRowRemovesBoth` (normal delete); `deletingRowWithMissingPdfStillRemovesRow` (missing file repair); `fileDeleteReturningFalseAbortsAndPreservesRow` (false delete preserves data and reports error); `rowDeleteFailureAfterFileRemovalRestoresFileAndPreservesRow` (restore after Room failure); `cleanupDeletesOldUnreferencedStageButKeepsNewerAndReferencedFiles` (age/reference filters); `cleanupSkipsDeletionWhenRoomReferenceCheckFails` (fail-closed sweep); and `cleanupMatchesStagingAndDeleteBackupNamesButRejectsMalformedNames` (valid import/delete temp names are recognized, malformed names are not).

Test-first evidence: the new expected-outcome tests were executed against the unfixed implementation and failed exactly at insert-orphan cleanup and false-delete error/row-preservation assertions. After the fixes, both pass. Cancellation, missing-file repair, Room failure restoration, and default ContentResolver success also pass.

### Approved aged staged-file cleanup (implemented)

Imports use exact `.staging-<UUID>.pdf` names; delete rollback copies use `.staging-delete-<UUID>.pdf`. Both are matched by the startup sweep, which applies the named `STAGED_FILE_MAX_AGE_MILLIS` (24 hours) age gate and Room reference check to both forms. Malformed names are ignored; DB open/reference failure skips deletion. AndroidTests cover stale normal and delete-backup cleanup, referenced backup immunity, malformed names, fresh-file retention, and DB-check failure. Successful imports retain their staging-prefixed names but are protected by the Room reference.

### Room schema export

- `HistoryDatabase.exportSchema` is true; KSP writes to `app/schemas` via `room.schemaLocation`; the schema directory is configured as androidTest assets for migration tooling.
- Generated snapshot: `app/schemas/com.figapps.fractioncalculatorv1.history.HistoryDatabase/3.json` (schema version 3; history and wire_datasheets entities and all four indices represented).
- `app/src/main/java/com/figapps/fractioncalculatorv1/history/HistoryDatabase.kt` migration definitions were not modified. The final migration instrumented test passed after enabling schema export.
- After WS-1, `bash gradlew :app:testDebugUnitTest` remained at **128 tests**; `bash gradlew :app:connectedDebugAndroidTest` passed **20 tests**, zero failures/errors/skips: Example (1), migration (1), history (3), wire repository (15).
- WS-1 history coverage added in-memory Room instrumentation: 3 tests passed for insert/observe/find, equal-timestamp order, and delete/clear emissions. In-memory Room isolates test state while retaining actual DAO SQL and Room invalidation behavior.
- WS-2 settings coverage adds one emulator integration scenario over the process-wide serialized DataStore. It snapshots/restores the complete pre-test Preferences map so test values are not retained. The specific IOException catch branch remains a documented blocker; no production seam or unsafe live-file corruption was introduced.
- After WS-2, JVM tests remained at **128** and emulator instrumented tests passed **21 tests**, zero failures/errors/skips.
- WS-3 added six passing JVM calculator transition tests. Test-first probes found numerator-only input returns `3` (now approved as intended) and `Digit(12)` is accepted (still unresolved). No production behavior was changed.
- After WS-3, JVM tests passed **134 tests** and connected Android tests passed **21 tests**, zero failures/errors/skips.
- Decision 1 follow-up adds one JVM assertion for numerator-only → whole number and updates the stale `FractionInput.toFraction()` documentation; parser behavior already conformed. JVM suite became **135 tests**.
- Decision 2 adds a JVM guard test and a single `0..9` handler check; invalid keys are ignored before operand state is modified. JVM suite is now **136 tests**.
- WS-4 distinguishes delete rollback copies with `.staging-delete-<UUID>.pdf`; the startup sweep pattern covers both staging forms. The first pattern test caught an extra hyphen in the generated delete-backup name, which kept it outside the accepted pattern; the name construction was corrected and the expanded emulator case now passes for valid/malformed names plus reference and age gates. No migration changed.
- After WS-4, JVM tests remain **134** and emulator instrumented tests pass **22**, zero failures/errors/skips.
- After approved decisions 1–2, JVM tests pass **136** and emulator instrumented tests remain **22**, zero failures/errors/skips.

### Deferred from this phase

- Signed release build with real keystore and physical-device backup/restore, as explicitly requested.
- Backup restore verification on a transport/image allowing app backup; current API 36 LocalTransport outcome is recorded above.
- Backup restore remains deferred because API 36 LocalTransport rejects the installed app; no dataset was available for restore verification.
- A process death between removing the original PDF and a failed Room row deletion can bypass in-process rollback restoration; future crash-recovery reconciliation remains a residual design risk.

### Deferred to physical device

- End-to-end Android cloud backup and device-transfer restore of Room + DataStore + PDF content; emulator backup was not allowed.
- Validate Android Settings/account/transport backup opt-in behavior and actual restore UX on supported OEM/API versions.
- No other current test or build step requires a physical device; the available emulator executed the full checked-in instrumented suite.
