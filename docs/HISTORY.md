# Historie rozpoznávání – specifikace

## Datový model (`DetectionEntity`)

| Pole | Význam | Automaticky | Editace |
|---|---|---|---|
| `recordedAt` | Čas pořízení (ms) | ano (čas triggeru) | ano (`RRRR-MM-DD HH:MM`) |
| `durationMs` | Délka klipu | ano | ne |
| `audioPath` | WAV klip (relativní cesta) | ano | ne (přehrání/sdílení) |
| `sampleRate` | Vzorkovačka (Hz) | ano | ne |
| `speciesCommon` | Obecný název | ano (klasifikátor) | ano |
| `speciesScientific` | Vědecký název | ano | ano |
| `confidence` | Spolehlivost 0..1 | ano | nepřímo (ruční oprava → 1.0) |
| `source` | `HEURISTIC` / `TFLITE` / `MANUAL` | ano | nepřímo (oprava → `MANUAL`) |
| `behavior` | Odhad chování | ano (jen při ≥ 0.4) | ano |
| `behaviorConfidence` | Spolehlivost chování | ano | ne |
| `latitude` / `longitude` | GPS | ano (fix ≤ 6 s) | ano (+ tlačítko GPS) |
| `placeName` | Název místa | ne (prázdné) | ano |
| `note` | Poznámka | ano (zdroj detekce) | ano |
| `photoPaths` / `videoPaths` | Přílohy (JSON seznam) | ne | ano (foťák/přidat/odebrat) |
| `createdAt` / `updatedAt` | Auditní časy | ano | ne |

## Automatické přidávání

Každý trigger (automatický i ruční) projde stejnou pipeline:

1. Snapshot 4 s z ring bufferu (včetně předstihu před triggerem).
2. Klasifikace → nejlepší kandidát + žebříček `topLabels`.
3. Gating chování prahem 0.4 (`RecordViewModel.CONFIDENCE_GATE`).
4. Uložení WAV (`audio/rec_RRRRMMDD_HHMMSS.wav`).
5. GPS fix (čerstvý ≤ 6 s, jinak poslední známá, jinak prázdné).
6. Vložení řádku do Room; UI ukáže kartu + žebříček.

Duplicity řeší cooldown 5 s mezi triggery.

## Editace a validace

- Čas: striktní formát, neplatný vstup se neuloží (snackbar).
- Souřadnice: rozsahy −90..90 / −180..180, prázdné = neznámé.
- Oprava druhu → `source = MANUAL`, `confidence = 1.0`.
- Mazání záznamu maže kaskádovitě i WAV, fotky a videa.

## Vyhledávání

Fulltext přes druh (oba názvy), místo a poznámku (`LIKE`, řazení od
nejnovějšího). Prázdný dotaz vrací vše.

## Sdílení

`ACTION_SEND`: textové shrnutí (druh, čas, místo, GPS, spolehlivost,
poznámka) + WAV příloha přes `FileProvider`. Export CSV/JSON je plánován.
