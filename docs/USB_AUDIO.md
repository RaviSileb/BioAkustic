# USB a ultrazvukový hardware

## Stav v MVP

Záložka **USB** vypisuje zařízení z `UsbManager` (název, VID/PID, třída)
a označuje USB audio (třída 1 nebo audio rozhraní). Slouží k ověření, že
telefon mikrofon vidí. Záznam přes USB zatím není implementován.

## Cílový hardware

- **Dodotronic Ultramic 384K** (384 kHz, UAC2), nebo
- **Vleermic 384** (384 kHz, UAC2).

Oba se hlásí jako standardní USB Audio Class 2.0 bez vlastních ovladačů.
S23 Ultra má USB-C 3.2 s OTG/host – připojení funguje přímo, případně přes
hub s napájením pro noční monitoring.

Echo Meter Touch 2 je s telefonem kompatibilní, ale jde o uzavřený
ekosystém (vlastní aplikace, žádné SDK) – do BioAkustic ho integrovat nelze.

## Plán implementace (fáze 2–3)

1. **Fáze 2 (192 kHz přes HAL):** `AudioRecord` na USB zařízení, Nyquist
   96 kHz – pokryje běžné evropské druhy (*Pipistrellus*, *Nyctalus*).
2. **Fáze 3 (384 kHz vlastní driver):** izochronní UAC2 přenos v NDK
   (`android.hardware.usb` + vlastní parser), plné pásmo do ~190 kHz,
   heterodynový odposlech + ukládání plného spektra.

## Infrazvuk a hydrofon

- Infrazvuk: externí USB mikrobarometr / LF snímač (interní MEMS + horní
  propust infrazvuk tlumí; vestavěný barometr není náhrada).
- Hydrofon: USB (UAC1) hydrofon se chová jako běžný USB mikrofon.
