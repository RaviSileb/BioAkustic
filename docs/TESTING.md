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

## Známá omezení testu

- Telefon byl po celou dobu **zamčený zabezpečeným zámkem** (PIN/biometrika),
  který nelze přes adb obejít – proto nebylo možné pořídit screenshoty UI
  ani proklikat aplikaci. Logcat potvrzuje první vyrenderovaný frame
  (`onFrameAvailable the first frame is available`).
- End-to-end UI scénář (spuštění monitoringu → automatická detekce → záznam
  v historii → editace → příloha) je proto zatím ověřen po částech
  (engine, DB, WAV, klasifikátor) a **čeká na ruční potvrzení na odemčeném
  telefonu**. Doporučený postup viz `docs/USER_GUIDE.md`.
- GPS fix a BirdNET model se na zařízení netestovaly (model není součástí
  repa; GPS vyžaduje signál a čas venku).
