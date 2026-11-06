# BioAkustic

Android aplikace pro **akustickou identifikaci zvířat a analýzu jejich chování**,
cílená na **Samsung Galaxy S23 Ultra** (SM-S918B).

Aplikace nahrává zvuk z mikrofonu, zobrazuje živý spektrogram, automaticky
detekuje zvukové události, klasifikuje je (heuristika, případně BirdNET-TFLite)
a ukládá vše do **historie rozpoznávání** s časem, místem (GPS), zvukem,
fotkami a videi. Vše běží **on-device**, nahrávky zůstávají v telefonu.

## Funkce (MVP 0.1.0)

- Živý monitoring: `UNPROCESSED` 48 kHz float, spektrogram, měřič úrovně.
- **Automatická detekce** s nastavitelným prahem + ruční uložení detekce.
- **Žebříček pravděpodobných výsledků** s procenty, seřazený od nejlepšího.
- Práh spolehlivosti 0.4 pro vyhodnocení chování (pod ním „neurčeno“).
- **Historie rozpoznávání**: automatické záznamy, vyhledávání, editace
  (druh, čas, místo, souřadnice, chování, poznámka), přehrávání WAV,
  focení/natáčení příloh, sdílení.
- Klasifikátor BirdNET-TFLite (runtime stažení modelu, GPU/NNAPI/CPU),
  fallback heuristický detektor.
- Detekce USB audio zařízení (příprava na ultrazvukový mikrofon).

## Dokumentace

| Dokument | Obsah |
|---|---|
| [BioAkustic.md](BioAkustic.md) | Původní vědecko-technické zadání |
| [BioAkustic_S23Ultra.md](BioAkustic_S23Ultra.md) | Implementační specifikace pro S23 Ultra |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architektura aplikace a modulů |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | Uživatelská příručka |
| [docs/HISTORY.md](docs/HISTORY.md) | Specifikace historie rozpoznávání |
| [docs/CLASSIFIER.md](docs/CLASSIFIER.md) | Klasifikátory a instalace modelu BirdNET |
| [docs/USB_AUDIO.md](docs/USB_AUDIO.md) | USB / ultrazvukový hardware |
| [docs/BUILD.md](docs/BUILD.md) | Sestavení, instalace, testy |
| [docs/TESTING.md](docs/TESTING.md) | Protokol testů na S23 Ultra |
| [CHANGELOG.md](CHANGELOG.md) | Přehled změn |

## Rychlý start

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Požadavky: JDK 17, Android SDK (platforma 35), Galaxy S23 Ultra s Androidem 13+.
Podrobnosti viz [docs/BUILD.md](docs/BUILD.md).

## Struktura repozitáře

```
BioAkustic/
├── android/                 # Android projekt (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/bioakustic/app/
│       ├── audio/           # AudioEngine, WAV, STFT/spektrogram
│       ├── classify/        # Klasifikátory + správce modelu
│       ├── data/            # Room databáze + repozitář historie
│       ├── ui/              # Obrazovky Záznam / Historie / Detail / USB / Nastavení
│       ├── location/        # GPS fixy
│       └── playback/        # Přehrávač nahrávek
├── docs/                    # Sada dokumentů
├── BioAkustic.md            # Původní zadání
└── BioAkustic_S23Ultra.md   # Specifikace pro S23 Ultra
```

## Stav a roadmap

MVP 0.1.0: interní mikrofon, heuristika + připravený BirdNET loader, historie,
USB detekce. Další fáze (192/384 kHz UAC2 záznam, savčí modely, S Pen anotace,
infrazvuk) viz `BioAkustic_S23Ultra.md` §9.
