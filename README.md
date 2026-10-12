# FabCalc

FabCalc is a set of fabrication math tools and anything else i thought would be useful in my day to day. it is 100% coded with AI.
i am not a coder by any means and i learned a lot along the way. i am the foreman of an industrial metal shop and want to make a suite of tools that either dont currently exist, or are scattered and hard to keep track of. 
any suggestions or help with the app are welcome. i hope someone can find this app useful other than me.

## Build from Source

Install Android Studio or the Android SDK and a JDK compatible with the Gradle wrapper, then build
the debug APK from the project root:

```bash
bash gradlew :app:assembleDebug
```

## Tests

### JVM Unit Tests

These tests run on the local JVM and do not require an emulator or connected device:

```bash
bash gradlew :app:testDebugUnitTest
```

### Instrumented Tests

These Android and Compose UI tests require a booted emulator or connected device visible in
`adb devices`. With one target available, run:

```bash
bash gradlew :app:connectedDebugAndroidTest
```

If multiple targets are connected, select one with `ANDROID_SERIAL=<device-serial>` before invoking
the command.

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
