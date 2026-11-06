# Protokol testů na Galaxy S23 Ultra

Datum: 2026-11-06. Zařízení: **SM-S918B** (Galaxy S23 Ultra, `dm3q`),
Android 17 (SDK 37), sériové číslo končící `W2E`. Aplikace 0.1.0 (debug).

## Souhrn

| Sada | Výsledek |
|---|---|
| Unit testy (JVM) | **6/6** (`SpectrumTest` 4, `HeuristicClassifierTest` 2) |
| Instrumentované testy na S23 Ultra | **4/4** (`SmokeTest`) |
| Instalace + start aplikace | OK, 0 pádů, `MainActivity` resumed |

## Unit testy (`:app:testDebugUnitTest`)

- `fft_detectsSinePeak` – FFT 2048, sinus 440 Hz → špička v binech 17–20.
- `melBank_dimensionsAndCentroid` – 64 Mel pásem, centroid 3 kHz tónu ±500 Hz.
- `resample_changesLengthProportionally` – 48→24 kHz = poloviční délka.
- `levelDb_silenceAndFullScale` – ticho −90 dB, plná úroveň 0 dB.
- `topLabels_matchTopResultAndConfidenceRange` – žebříček neprázdný, top-1
  odpovídá výsledku, spolehlivosti v 0..1, seřazeno sestupně.
- `loudSignal_scoresHigherThanQuietSignal` – hlasitější signál ≥ tišší.

## Instrumentované testy (`:app:connectedDebugAndroidTest`, SM-S918B)

- `database_insertQueryDelete` – Room: insert → get → count → search → delete.
- `audioEngine_opensMicrophone` – **reálný mikrofon**: `AudioRecord` se otevřel,
  během 1,2 s dorazily snímky, vzorkovačka **48 000 Hz**, snapshot 1 s neprázdný.
- `wavWriter_writesValidHeader` – hlavička RIFF/WAVE, správná délka souboru.
- `heuristicClassifier_returnsResult` – 3s tón 2 kHz → výsledek HEURISTIC,
  spolehlivost v 0..1.

Reporty: `android/app/build/reports/androidTests/connected/debug/`,
surová XML: `android/app/build/outputs/androidTest-results/connected/debug/`.
(Adresář `build/` se necommituje; výsledky jsou evidovány zde.)

## Ověření instalace a startu

```bash
adb install -r app-debug.apk                      # Success (2×, vč. rebuildu)
adb shell pm grant … RECORD_AUDIO, ACCESS_FINE_LOCATION
adb shell am start -n com.bioakustic.app/.MainActivity
# → topResumedActivity=com.bioakustic.app/.MainActivity, 0× FATAL EXCEPTION
```

## Živý test na odemčeném telefonu (15:15–15:35)

Telefon byl odemčen, aplikace ovládána přes `uiautomator` + tapnutí:

- Monitoring spuštěn, spektrogram živý, automatická detekce uložila 5 klipů
  během 3 minut (práh −25 dB, nastavil uživatel).
- **Zdroj zvuku:** `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` není k dispozici,
  engine spadl na `VOICE_RECOGNITION / float @ 48000 Hz` – dle návrhu.
- **BirdNET model** (FP16, 26 MB z `sammlapp/BirdNET_v2.4`) nahrán do
  `files/models/` a v aplikaci načten (`Klasifikátor: BirdNET (TFLite)`).
- **Nalezené a opravené chyby:**
  1. Formát popisků je `Scientific_Common` (ne naopak) – opraven parser.
  2. Chyběl artefakt `tensorflow-lite-gpu-api` → `NoClassDefFoundError`
     a pád při startu – přidána závislost + catch `Throwable`.
  3. GPU delegát model odmítl (`Error applying delegate`) – přidán
     automatický retry bez delegátu (CPU), inference funguje.
  4. Výstup modelu jsou **logity** – bez sigmoidu vycházelo vše 0 %.
     Přidán `sigmoid()` + unit testy (`BirdnetPostprocessTest`).
- **Varování 16KB stránek:** na zařízení neškodné (stránky 4 KB,
  `getconf PAGESIZE` = 4096); TFLite 2.16.1 `.so` nejsou 16KB-zarovnané.
  Sledovat při přechodu na novější zařízení.
- **Aktuální unit testy: 8/8** (4 FFT/Mel + 2 heuristika + 2 postprocessing).

### Co telefon slyšel (BirdNET inference nad staženými klipy)

- 15:32 (tichá místnost, −51 dB): prvních ~3 s **lidský hlas ~100 %**
  (uživatel u telefonu), pak ticho – žádné zvíře.
- 15:18 (silné výbuchy 6–12 kHz): BirdNET **žádného ptáka nerozpoznal**
  (vše ≈ 0 %) – pravděpodobně nebiologický zvuk, ne zvíře.
- 15:17 (tichý šum 300–3000 Hz): nic (≈ 0 %).

## Zbývá ověřit po opětovném připojení telefonu

Telefon se během testu odpojil (kabel). Po připojení: přeinstalovat aktuální
build (sigmoid fix), spustit monitoring, ověřit procenta v žebříčku naživo,
projít Historie → detail → přílohy a doplnit screenshoty.
