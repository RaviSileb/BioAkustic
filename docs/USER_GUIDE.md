# Uživatelská příručka

## První spuštění

1. Nainstalujte APK a otevřete BioAkustic.
2. Na záložce **Záznam** klepněte **Spustit monitoring**.
3. Povolte mikrofon (povinné) a polohu (doporučené – GPS se ukládá k záznamům).

## Záznam a automatická detekce

- Živý spektrogram a měřič úrovně ukazují, co mikrofon slyší.
- Je-li zapnutá **Automatická detekce**, aplikace sama ukládá zvukové události
  nad nastaveným prahem do historie (4s klip včetně předstihu).
- Tlačítko **Uložit detekci** uloží posledních pár sekund ručně.
- Po každé detekci se zobrazí karta a **tabulka pravděpodobných výsledků**
  seřazená od nejpravděpodobnějšího, s procenty a pruhem spolehlivosti.
- Práh detekce (dB) i automatiku lze měnit zde i v Nastavení.

## Historie

- Záložka **Historie** vypisuje záznamy od nejnovějšího, včetně druhu, času,
  místa, GPS a spolehlivosti. Funguje fulltextové hledání.
- Klepnutím otevřete **detail**, ikonou koše záznam smažete (včetně médií).

## Detail záznamu

- **Druh** – obecný i vědecký název lze opravit; ruční oprava se označí
  „ručně“ se 100% spolehlivostí.
- **Čas** – formát `RRRR-MM-DD HH:MM`.
- **Místo** – název + souřadnice; tlačítko doplní aktuální GPS.
- **Chování a poznámka** – volné texty.
- **Zvuk** – přehrání uloženého WAV klipu.
- **Fotky / Videa** – focení a natáčení systémovým foťákem, náhled, smazání.
- Ikona sdílení odešle zvuk + shrnutí (druh, čas, GPS) do jiné aplikace.

## Nastavení

- **Klasifikační model** – bez modelu běží heuristický detektor (rozpozná zvuk,
  neurčí druh). Vlastní BirdNET-kompatibilní `.tflite` + popisky stáhnete
  zadáním URL (návod: [CLASSIFIER.md](CLASSIFIER.md)).
- **NNAPI** – experimentální běh na Hexagon DSP místo CPU (GPU delegát se
  zkouší automaticky jako první).
- **Detekce** – automatika a práh (sdílené se záložkou Záznam).

## USB

- Záložka **USB** vypíše připojená USB zařízení a označí USB audio.
- Slouží pro kontrolu ultrazvukového mikrofonu; plný 384kHz záznam je
  naplánovaná fáze (viz [USB_AUDIO.md](USB_AUDIO.md)).

## Tipy pro terén

- Telefon mějte mimo pouzdro zakrývající mikrofony (spodní hrana).
- Pro noční monitoring počítejte s vyšší spotřebou; displej zhasínejte.
- Nahrávky jsou v soukromém úložišti aplikace – zálohujte sdílením/exportem.
