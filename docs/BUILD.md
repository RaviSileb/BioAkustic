# Sestavení, instalace a testy

## Požadavky

- JDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64` nebo `JAVA_HOME`).
- Android SDK s platformou 35 a build-tools 35+ (`ANDROID_HOME`
  nebo `android/local.properties` – ten je lokální, necommituje se).
- Připojená Galaxy S23 Ultra s povoleným laděním USB (pro testy na zařízení).

## Sestavení

```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

První build stahuje Gradle distribuci, AGP a závislosti (~5–10 minut).

## Instalace a oprávnění

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.bioakustic.app android.permission.RECORD_AUDIO
adb shell pm grant com.bioakustic.app android.permission.ACCESS_FINE_LOCATION
adb shell am start -n com.bioakustic.app/.MainActivity
```

## Testy

```bash
# Unit testy (JVM): FFT, Mel, heuristika
./gradlew :app:testDebugUnitTest

# Instrumentované testy na připojené S23 Ultra:
# databáze, mikrofon (AudioRecord 48 kHz), WAV, heuristika
./gradlew :app:connectedDebugAndroidTest
```

Výsledky: `app/build/test-results/` a `app/build/reports/`.

## Užitečné příkazy

```bash
adb logcat --pid=$(adb shell pidof com.bioakustic.app)  # log aplikace
adb exec-out screencap -p > shot.png                    # screenshot
adb shell svc power stayon true                         # displej vzhůru při testech
```

## Verzování

`versionCode` / `versionName` v `android/app/build.gradle.kts`.
Změny evidujte v [CHANGELOG.md](../CHANGELOG.md).
