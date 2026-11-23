# Build and release

Use the Gradle wrapper with JDK 21 and Android SDK Platform 37. Set `ANDROID_HOME` to your SDK path. Run `scripts/bin/test-profile.sh preflight` and `scripts/bin/test-profile.sh full`. Build the APK with `./gradlew :app:assembleRelease`. The debug package suffix is `.s8lab`.

## Public preview checklist

1. Decide the project license and review redistribution terms and notices for dependencies packaged in the APK. Keep model downloads tied to their catalog revisions. [Licensing](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
2. Select a release signing key and compare its certificate with the previous APK. Plan the update path or document a fresh install if the signing identities differ. Store the key and backups outside the repo. [Android signing](https://developer.android.com/studio/publish/app-signing)
3. Build from a clean `main` commit and record its SHA, version, increasing `versionCode`, APK SHA-256 and signing certificate. On S22+ in IDLE, preserve a rollback APK and check clean installation, Phone role, Wireless ADB, host startup, SIM and model. Verify English and Polish UI, prompts and speech.
4. With consent, verify an incoming call, two-way audio, STOP/takeover and END_CALL after TTS on that APK. Record the exact SHA and any unverified language or outgoing scope. Exclude private audio and identifiers from evidence.
5. After approval, tag the verified commit and publish a GitHub pre-release with the signed APK, `SHA256SUMS.txt`, tested scope and upgrade notes. Keep published assets immutable. Verify the download and checksum links in the README.
