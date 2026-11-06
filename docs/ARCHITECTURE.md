# Architektura aplikace

Cíl: Galaxy S23 Ultra, `minSdk 33`, `targetSdk 35`, Kotlin + Jetpack Compose,
Material3, Room, TensorFlow Lite. Bez DI frameworku (ruční `AppContainer`).

## Vrstvy

```
UI (Compose obrazovky + ViewModely)
  Záznam / Historie / Detail / USB / Nastavení
        │
        ▼
Doména (ViewModely)
  RecordViewModel – monitoring, trigger, ukládání detekcí, žebříček
  HistoryViewModel – seznam + vyhledávání
  DetailViewModel – editace záznamu, přílohy, přehrávání
  SettingsViewModel – model, prahy
  UsbViewModel – výpis USB zařízení
        │
        ▼
Data a služby
  audio/AudioEngine – AudioRecord UNPROCESSED 48 kHz float + ring buffer
  audio/SpectrumAnalyzer – STFT, Mel, centroid, plochost spektra
  audio/WavWriter – 16-bit PCM WAV
  classify/Classifier – rozhraní (top-1 + topLabels žebříček)
  classify/HeuristicClassifier – fallback bez modelu
  classify/BirdnetTfliteClassifier – BirdNET-kompatibilní TFLite
  classify/ModelManager – stahování/načítání modelu
  data/db – Room (DetectionEntity, DAO)
  data/repo – repozitář historie + soubory médií
  location/LocationProvider – GPS fixy (Fused)
  playback/AudioPlayer – přehrávání WAV
```

## Datový tok detekce

1. `AudioEngine` čte mikrofon po blocích 4096 vzorků, drží 12 s ring buffer.
2. `RecordViewModel` počítá Mel-sloupce pro spektrogram a sleduje hladinu.
3. Trigger: hladina nad prahem > 0,5 s + cooldown 5 s (nebo ruční uložení).
4. Snapshot 4 s → `Classifier.classify()` → WAV + GPS fix → `Room` záznam.
5. UI: karta „Poslední detekce“ + **žebříček kandidátů** (`topLabels`).

## Práh spolehlivosti

`RecordViewModel.CONFIDENCE_GATE = 0.4`. Odhad chování se uchovává jen tehdy,
když nejlepší kandidát dosáhne ≥ 0.4; jinak je chování `null` („neurčeno“)
a poznámka to explicitně uvádí. Žebříček se zobrazuje vždy, podprahlé
kandidáty UI zvýrazňuje červeně.

## Soubory (app-private `files/`)

- `audio/rec_*.wav` – klipy detekcí (16-bit PCM, 48 kHz).
- `photos/img_*.jpg`, `videos/vid_*.mp4` – přílohy (systémový foťák).
- `models/birdnet.tflite`, `models/birdnet_labels.txt` – volitelný model.
- `databases/bioakustic.db` – Room databáze historie.

Cesty v DB jsou relativní vůči `files/`; smazání záznamu maže i soubory.
Sdílení s foťákem/přehrávači probíhá přes `FileProvider`.

## Navigace

Ruční navigace v `MainActivity` (4 záložky + detail přes `detailId`),
bez Navigation knihovny. Stav záložky a detailu přežívá rotaci
(`rememberSaveable`).
