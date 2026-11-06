# Changelog

Formát: datum, verze, změny (česky, stručně).

## 2026-11-06 – 0.1.0 (opravy z živého testu)

- BirdNET: oprava formátu popisků (`Scientific_Common`), sigmoid na logity,
  retry bez GPU delegátu (na S23U odmítnut → CPU fallback).
- Přidána závislost `tensorflow-lite-gpu-api`, fallback chytá i `Error`.
- Výchozí URL modelu: `sammlapp/BirdNET_v2.4` (HuggingFace, FP16).
- Nové unit testy `BirdnetPostprocessTest` (8/8).

## 2026-11-06 – 0.1.0 (MVP)

- Živý monitoring: AudioRecord UNPROCESSED 48 kHz float, spektrogram, měřič úrovně.
- Automatická detekce (práh dB, cooldown) + ruční uložení; 4s WAV klipy s předstihem.
- Tabulka pravděpodobných výsledků s procenty, řazená od nejlepšího.
- Práh spolehlivosti 0.4 pro vyhodnocení chování (`CONFIDENCE_GATE`).
- Historie rozpoznávání: Room DB, vyhledávání, editace (druh, čas, místo, GPS,
  chování, poznámka), přehrávání, fotky/videa, mazání, sdílení.
- Heuristický klasifikátor + BirdNET-kompatibilní TFLite loader
  (GPU → NNAPI → CPU), stahování modelu v Nastavení.
- GPS fixy k záznamům (Fused Location).
- Detekce USB audio zařízení (příprava na ultrazvuk).
- Unit testy (FFT, Mel, heuristika) + instrumentované smoke testy na S23 Ultra.
