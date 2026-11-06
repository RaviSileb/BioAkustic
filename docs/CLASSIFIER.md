# Klasifikátory a model BirdNET

## Přehled

Aplikace volí klasifikátor automaticky:

1. **BirdNET-TFLite** – pokud jsou v `files/models/` soubory
   `birdnet.tflite` + `birdnet_labels.txt` a model se podaří načíst.
2. **Heuristický detektor** – fallback. Nerozpoznává druhy; hlásí
   „Neznámý zvuk“ se spolehlivostí odvozenou od SNR a hrubý Mortonovský
   vzorec (nízký/drsný vs. vysoký/tónový), explicitně označený jako heuristika.

Oba klasifikátory vracejí kromě top-1 i seřazený **žebříček kandidátů**
(`topLabels`, BirdNET top-5), který UI zobrazuje s procenty.

## Požadavky na TFLite model

- Vstup: surový průběh 3 s mono @ 48 kHz, float32, tvar `[1, 144000]`.
- Výstup: vektor skóre `[1, N]` nebo `[N]` (např. 6512 tříd BirdNET).
- Popisky: textový soubor, jeden druh na řádek,
  formát `Obecný název_Vědecký název` (dělení na posledním podtržítku).
- Kratší/delší klipy se automaticky středově oříznou/doplní na 3 s;
  jiná vzorkovačka se lineárně převzorkuje.

## Instalace modelu

1. Získejte BirdNET-kompatibilní `.tflite` a popisky (např. z projektu
   [BirdNET-Analyzer](https://github.com/kahst/BirdNET-Analyzer) – modely
   publikuje TU Chemnitz; přímé URL se mění, proto ho aplikace
   nehardcoduje).
2. V aplikaci otevřete **Nastavení → Klasifikační model**, zadejte obě URL
   a klepněte **Stáhnout model**. Průběh ukazuje procenta.
3. Po stažení se model ihned načte; stav ukazuje použitý delegát.

Alternativa bez stahování: nakopírujte soubory přes `adb`:

```bash
adb push birdnet.tflite /data/local/tmp/birdnet.tflite
adb push birdnet_labels.txt /data/local/tmp/birdnet_labels.txt
adb shell run-as com.bioakustic.app mkdir -p files/models
adb shell run-as com.bioakustic.app cp /data/local/tmp/birdnet.tflite files/models/birdnet.tflite
adb shell run-as com.bioakustic.app cp /data/local/tmp/birdnet_labels.txt files/models/birdnet_labels.txt
```

(Debug build umožňuje `run-as`; po restartu aplikace se model načte.)

## Delegáti na Snapdragonu 8 Gen 2

Pořadí: **GPU delegát** (Adreno 740) → při zapnutém NNAPI **Hexagon DSP** →
**CPU** (4 vlákna). Každý krok má try/catch fallback, použitý delegát hlásí
Nastavení. Kvantizace INT8 se podporuje automaticky dle modelu.

## Práh chování

Odhad chování se uchovává jen při top-1 ≥ **0.4**
(`RecordViewModel.CONFIDENCE_GATE`). Pod prahem je chování „neurčeno“.
Pravidlo platí pro všechny klasifikátory; druhově-specifické behavior
modely (TCN/LSTM) jsou plánovaná fáze.
