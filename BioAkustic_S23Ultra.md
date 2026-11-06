# BioAkustik – Adaptace pro Samsung Galaxy S23 Ultra (SM-S918)

Cílená implementační specifikace odvozená z [BioAkustic.md](BioAkustic.md).
Fixace na jeden konkrétní telefon odstraňuje nutnost řešit fragmentaci Android
zařízení – všechny hodnoty níže platí pro S23 Ultra.

## 1. Hardwarový profil S23 Ultra

| Komponenta | Specifikace | Dopad na aplikaci |
|---|---|---|
| SoC | Snapdragon 8 Gen 2 for Galaxy (SM8550-AC, 4 nm) | On-device inference bez serveru |
| CPU | 1× Cortex-X3 3.36 GHz + 4× A715/A710 2.8 GHz + 3× A510 2.0 GHz | STFT + Mel-spektrogram v reálném čase na CPU |
| GPU | Adreno 740 | TFLite GPU delegate (OpenCL) pro CNN |
| NPU/DSP | Hexagon (Qualcomm AI Engine) | Volitelně NNAPI / QNN delegate |
| RAM / úložiště | 8–12 GB / 256 GB–1 TB | Modely 30–150 MB bez problému, lokální databáze nahrávek |
| Mikrofony | 3× MEMS, stereo nahrávání, ANC | Interní pásmo ~50 Hz – 18 kHz (použitelné) |
| Konektor | USB-C 3.2 s OTG / USB host | Externí UAC2 ultrazvukový mikrofon |
| 3.5mm jack | Není | Analogový externí mic jen přes USB-C DAC/ADC |
| Displej | 6.8" QHD+ (3088×1440), 120 Hz | Živý spektrogram, S Pen anotace |
| Senzory | Barometr, multi-GNSS, kompas | GPS + čas pro BirdNET meta-model |
| Baterie | 5000 mAh, 45 W | Limit pro kontinuální monitoring (viz §7) |
| Odolnost | IP68 | Terénní použití |
| OS (2026) | Android 15 / One UI 7 (startoval na 13) | `minSdk 33`, `targetSdk 35` |

## 2. Co z původního návrhu platí a co se upřesňuje

- **Ptáci (interní mic):** beze změny – plně realizovatelné, BirdNET-TFLite běží
  na Adreno 740 v reálném čase.
- **Savci:** beze změny – druhově-specifické modely, stejné limity dat.
- **Netopýři / hmyz (ultrazvuk):** ANO, ale **ne přes standardní `AudioRecord`**.
  Android `AudioRecord`/`AudioTrack` garantují max. 192 kHz (od API 23) a reálná
  podpora USB vzorkovaček v HAL je typicky ≤192 kHz. Pro plné pásmo do 150+ kHz
  je nutný **vlastní UAC2 driver** v aplikaci (§4.2). Echo Meter Touch 2 je
  s S23 Ultra kompatibilní, ale je to uzavřený ekosystém (vlastní appka,
  žádné veřejné SDK) – do vlastní aplikace **neintegrovatelný**.
- **Ryby (hydrofon):** beze změny – fyzikálně nemožné bez hydrofonu; přes USB-C
  lze připojit USB hydrofon (UAC1) jako běžný USB mikrofon.
- **Infrazvuk:** interní MEMS + high-pass filtr infrazvuk netransparentně
  utlumí. Vestavěný **barometr není náhrada** (vzorkování v jednotkách Hz,
  určen pro tlak, ne akustiku). Nutný externí USB mikrobarometr / LF snímač.
- **„Detektor nálady“:** beze změny – pouze druhově-specifická klasifikace
  kontextu (alarm vs. teritorium vs. páření), žádný univerzální detektor emocí.

## 3. Architektura aplikace (S23 Ultra)

```
[Interní 3× MEMS] ──AudioRecord 48 kHz float──┐
                                              ▼
[USB-C UAC2 mic 256/384 kHz] ──vlastní driver─┤
                                              ▼
                             [Audio Router: zdroj + kalibrace]
                                              │
                                              ▼
                             [DSP: STFT → Mel-spektrogram / MFCC]
                                              │
                   ┌──────────────────────────┼──────────────────────────┐
                   ▼                          ▼                          ▼
        [BirdNET-TFLite]            [Mammal-CNN]              [Bat-CNN + heterodyn]
        ptáci + meta-model          savci (druhově)           pulzní detekce, poslech
                   └──────────────────────────┼──────────────────────────┘
                                              ▼
                             [Behavior head: TCN/LSTM, druhově-specifický]
                                              ▼
                             [UI: Jetpack Compose, spektrogram 120 Hz]
                             [Store: Room + WAV/FLAC, GPS, export CSV/JSON]
```

## 4. Audio vstup – konkrétní konfigurace

### 4.1 Interní mikrofony (ptáci, savci, poslechové pásmo)

- Zdroj: `MediaRecorder.AudioSource.UNPROCESSED` (bez AGC/NS/EQ).
  Fallback: `VOICE_RECOGNITION`, pokud `UNPROCESSED` není dostupný.
- Formát: 48 000 Hz, `ENCODING_PCM_FLOAT`, mono (detekce) nebo stereo
  (směrová doplňková informace – rozteč mikrofonů ~15 cm umožňuje hrubé TDOA).
- Cesta s nízkou latencí není nutná (offline-okenní inference), prioritou je
  věrnost signálu. `AAudio`/`Oboe` volitelně, `AudioRecord` plně stačí.

```kotlin
// Doporučená konfigurace AudioRecord pro S23 Ultra (interní mic)
val format = AudioFormat.Builder()
    .setSampleRate(48_000)
    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
    .build()
val record = AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED) // fallback: VOICE_RECOGNITION
    .setAudioFormat(format)
    .setBufferSizeInBytes(
        AudioRecord.getMinBufferSize(48_000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4
    ).build()
// Inference okno: 3 s rolling buffer (BirdNET standard), krok 1 s.
```

- Kalibrace: při prvním spuštění ověřit `getMinBufferSize`, dostupnost
  `UNPROCESSED` (`AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`)
  a reálnou vzorkovačku; odchylku resamplovat na 48 kHz v DSP (např. libsamplerate).

### 4.2 Externí ultrazvukový mikrofon (netopýři, hmyz)

Doporučený hardware (USB-C, UAC2, bez potřeby vlastního SDK):

- **Dodotronic Ultramic 384K** (384 kHz, pásmo do ~190 kHz), nebo
- **Vleermic 384** (UAC2, 384 kHz, pásmo 0.1–150 kHz).

Integrace:

1. USB host přes `android.hardware.usb` – vyžádat permission pro UAC2 zařízení,
   detekce přes `UsbManager.deviceList` + filtr ve `device_filter.xml`.
2. **Vlastní UAC2 isochronní přenos** (nativní vrstva, NDK + `libusb`-styl přístup
   přes `UsbDeviceConnection`), protože `AudioRecord` 384 kHz negarantuje.
   Referenční přístup: stejný, jaký používá USB Audio Recorder PRO.
3. Zjednodušená varianta MVP: spokojit se s 192 kHz přes Android USB HAL
   (Nyquist 96 kHz – pokryje většinu evropských druhů jako *Pipistrellus* ~45 kHz
   a *Nyctalus* ~20–30 kHz, ale ne vysokofrekvenční *Rhinolophus* ~80–110 kHz).
4. Heterodyn / time-expansion v DSP pro odposlech uživatelem + ukládání
   plného spektra do WAV (32-bit float, 384 kHz).

### 4.3 Infrazvuk a hydrofon

- Infrazvuk: externí USB mikrobarometr / LF snímač, pásmo 1–20 Hz, vlastní
  sériový/USB protokol dle výrobce; korelace s interním barometrem S23 Ultra
  pouze pro potlačení pomalého driftu tlaku, ne pro detekci.
- Hydrofon: USB (UAC1) hydrofon se chová jako běžný USB mikrofon –
  `AudioRecord` se zdrojem `DEFAULT` po přepnutí `AudioManager` na USB zařízení.

## 5. DSP a AI pipeline na SD 8 Gen 2

### 5.1 Předzpracování (CPU, NEON)

- STFT: okno 2048 / hop 512 @48 kHz → Mel-spektrogram 128 pásem (ptáci/savci).
- Pro netopýry: STFT nad 384 kHz proudem, log-frekvenční škála, detekce pulzů
  (energy onset, min. délka 1 ms, min. mezera 5 ms).
- Příznaky pro behavior head: MFCC-40, f₀ (PYIN), spektrální centroid,
  entropie, harmonicita, pulzní rychlost.
- Implementace: nativní C++ (NDK) – FFT (pocketfft / FFTW-NEON), Mel banka
  předpočítaná; rolling buffer 3 s / krok 1 s.

### 5.2 Modely (TFLite on-device)

| Úloha | Model | Vstup | Běh na S23U |
|---|---|---|---|
| Ptáci | BirdNET v2.4 TFLite (~50 MB, 6512 tříd) + meta-model (GPS + týden v roce) | 3 s @48 kHz | GPU delegate (Adreno 740), <100 ms/okno |
| Savci | EfficientNet-B0 / ResNet-18 fine-tune, druhově-specifické hlavy | 3–5 s Mel 128 | GPU delegate; NNAPI/Hexagon volitelně |
| Netopýři | CNN nad pulzními spektrogramy (např. BatDetect2-styl) | pulzní výřezy | CPU/GPU, datově lehké |
| Chování | TCN/LSTM nad sekvencí příznaků (§5.1), **striktně per-druh** | 5–10 s kontext | CPU, malý model (<5 MB) |

- Delegáti: primárně **TFLite GPU delegate** (stabilní na Adrenu), NNAPI jako
  experimentální volba; kvantizace INT8 s kalibrací, FP16 fallback.
- Pravidlo: behavior head se spouští **jen když species head překročí práh
  spolehlivosti** (≥0.4, viz `RecordViewModel.CONFIDENCE_GATE`) –
  jinak „neurčeno“, nikdy nehádat emoci. Aplikace vždy zobrazuje seřazený
  žebříček kandidátů s procenty (`Classifier.Result.topLabels`).
- Všechna inference on-device (terén bez signálu), modely stahovatelné per-druh.

### 5.3 Závislosti (Gradle, verze ověřit při scaffoldu)

```kotlin
// app/build.gradle.kts – klíčové závislosti
implementation("org.tensorflow:tensorflow-lite:2.x")
implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:2.x")
implementation("org.tensorflow:tensorflow-lite-support:0.4.x")
implementation("androidx.compose.material3:material3:1.x") // UI
implementation("androidx.room:room-ktx:2.x")               // lokální DB nálezů
implementation("com.google.android.gms:play-services-location:21.x") // GPS meta-model
// NDK: Oboe (volitelně), pocketfft, libsamplerate – přes CMake/prefab
```

```xml
<!-- AndroidManifest.xml – oprávnění -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-feature android:name="android.hardware.usb.host" android:required="false" />
<uses-feature android:name="android.hardware.microphone" android:required="true" />
```

## 6. UI/UX na S23 Ultra

- **Živý spektrogram** (Jetpack Compose + Canvas / OpenGL): 120 Hz obnovování,
  pinch-zoom ve frekvenční ose, přepínač lineární/log škála, heterodynový
  odposlech pro ultrazvuk.
- **S Pen:** anotace spektrogramu, označování pulzů/volání pro zpětnou vazbu
  modelu (active learning export).
- **Režimy:** Ptáci / Savci / Netopýři (vyžaduje USB mic – detekce připojení
  + průvodce) / Terénní monitoring (služba na pozadí).
- Každý výsledek: druh + spolehlivost + **explicitní nejistota behavior
  odhadu** (např. „varovné volání 0.82 – validováno jen akusticky“).
- Export: WAV/FLAC + CSV/JSON (čas, GPS, druh, skóre), sdílení do Xeno-Canto /
  iNaturalist formátu.

## 7. Napájení a termika (5000 mAh)

- Kontinuální monitoring: `ForegroundService` (typ `microphone`) + částečný
  wake-lock; inference v dávkách 1×/s, DSP duty-cycling.
- Odhad: kontinuální 48 kHz nahrávání + BirdNET okno 1×/3 s ≈ 8–12 % baterie/h
  (ověřit měřením na zařízení); pro celonoční monitoring netopýrů doporučit
  externí napájení přes USB-C hub (mic + nabíjení současně).
- Termální throttling: při přehřátí degradovat na CPU-only inference a delší
  krok oken (2–3 s); sledovat `PowerManager.getThermalHeadroom()` (API 29+).

## 8. Funkční matice – co na S23 Ultra funguje s čím

| Funkce | HW | Stav |
|---|---|---|
| Identifikace ptáků | interní mic | ✅ Plná podpora |
| Identifikace savců | interní mic | ✅ Dle dostupnosti druhových dat |
| Varovné / teritoriální / pářící volání | interní mic | ✅ Striktně druhově-specificky |
| Identifikace netopýrů (běžné druhy) | USB UAC2 mic @192 kHz (HAL) | ✅ MVP varianta |
| Identifikace netopýrů (plné pásmo) | USB UAC2 mic @384 kHz + vlastní driver | ✅ Plná varianta (§4.2) |
| Identifikace hmyzu | dtto | ✅ |
| Ryby | USB hydrofon | ⚠️ Omezeně (nízké SNR) |
| Infrazvuk (sloni, velké šelmy) | externí mikrobarometr | ⚠️ Vyžaduje ext. HW |
| Univerzální detektor emocí | — | ❌ Vědecky neudržitelné, neimplementovat |

## 9. Doporučený MVP a roadmap

1. **MVP (interní mic):** nahrávání 48 kHz → Mel-spektrogram → BirdNET-TFLite
   (GPU delegate) → UI + GPS + export. + Behavior head pro 2–3 modelové druhy
   (např. kos, sýkora – alarm vs. zpěv).
2. **Fáze 2 (USB):** detekce UAC2 mic, 192 kHz přes HAL, heterodynový odposlech,
   bat-CNN pro běžné evropské druhy.
3. **Fáze 3:** vlastní UAC2 384 kHz driver (plné pásmo), savčí modely,
   S Pen anotace + active learning smyčka.
4. **Fáze 4:** infrazvukový modul, hydrofon, serverová synchronizace nálezů.

## 10. Rizika specifická pro S23 Ultra

1. Samsung audio HAL a `UNPROCESSED` – ověřit na zařízení, mít fallback.
2. USB-C hub pro současné nabíjení + mic při nočním monitoringu.
3. Velikost BirdNET modelu (~50 MB) – stahovat po instalaci, ne v APK.
4. One UI správa baterie – vyžádat výjimku z optimalizace pro monitoring.
5. Displej QHD+ 120 Hz + kontinuální spektrogram = žrout baterie – tlumit
   jas / snižovat obnovování při monitoringu na pozadí.
