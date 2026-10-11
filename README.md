# FabCalc

FabCalc is an offline shop-floor calculator with calculation history and a local welding-wire
datasheet PDF library.

## Build from Source

Install Android Studio or the Android SDK and a JDK compatible with the Gradle wrapper, then build
the debug APK from the project root:

```bash
bash gradlew :app:assembleDebug
```

## Tests

Run the JVM unit tests with:

```bash
bash gradlew :app:testDebugUnitTest
```

With an emulator or Android device connected, run instrumented tests with:

```bash
bash gradlew :app:connectedDebugAndroidTest
```

## Release signing

Release tasks require a dedicated signing configuration. Do not use or distribute a release APK
signed with the Android debug key. Keep the keystore and passwords out of version control; provide
them either in the repository-root `local.properties` (which is gitignored) or through environment
variables:

```properties
RELEASE_KEYSTORE_PATH=relative/path/to/release.p12
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

`RELEASE_KEY_PASSWORD` is optional for PKCS12 keystores whose key uses the store password. The
equivalent environment variable names are `RELEASE_KEYSTORE_PATH`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. The keystore path is resolved relative to the
repository root when it is not absolute.

Without the required three values, `assembleRelease` and other release tasks fail during Gradle
configuration. Debug builds and JVM tests do not require release credentials.

The verified release signing certificate has SHA-256 fingerprint
`6d8d8d8412c9b659227c86baff1a107b10847b998595657e1c03989797756ab8`. Verify a release APK with
`apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` and compare its
reported SHA-256 certificate digest with this fingerprint.

## Data backup

Android backup rules explicitly include `history.db`, DataStore's
`datastore/settings.preferences_pb`, and PDFs under `filesDir/wireDatasheets`. Emulator `bmgr`
verification may be unavailable when the emulator does not allow backup for this package. Before
relying on device transfer or cloud restore, follow the manual backup verification procedure in
`docs/AUDIT.md` and confirm both database metadata and its associated PDF files restore together.

## License

FabCalc is licensed under the GNU General Public License, version 3 or (at your option) any later
version. See [`LICENSE`](LICENSE) for the full license text.
