Vývoj Android aplikace pro akustickou identifikaci zvířat a analýzu jejich chování je realizovatelný, avšak s výraznými fyzikálními, hardwarovými a etologickými limity.

## **1\. Vědecká proveditelnost funkcionalit**

### **A. Druhová identifikace (Bioakustika)**

> * **Ptáci (Aves):** *Prokázané / Vysoce spolehlivé.* Nejrozvinutější oblast. Modely jako BirdNET (Cornell Lab of Ornithology) dosahují přesnosti přes 90 % na základě konvolučních sítí (CNN) zpracovávajících Mel-spektrogramy.  
> * **Netopýři (Chiroptera) a hmyz (Insecta):** *Prokázané.* Vyžadují však ultrazvukový hardware (viz sekce 3).  
> * **Savci (Mammalia):** *Středně pravděpodobné / Variabilní.* Vnitrodruhová variabilita akustických projevů je vysoká. Úspěšné u vlků, kytovců nebo primátů; u volně žijících savců je problémem nízká hustota dat pro trénink sítí.  
> * **Ryby (Actinopterygii):** *Nízká spolehlivost / Fyzikálně omezené.* Ryby produkují zvuky třením kosterních struktur (stridulace) nebo rezonancí plynového měchýře (obvykle 100 Hz – 1 kHz). Smartphone je ve vzduchu neschopný tyto zvuky zachytit kvůli impedančnímu nepřizpůsobení rozhraní voda–vzduch. Vyžaduje hydrofon.

### **B. Analýza nálady a kontextu chování (Bioakustická etologie)**

Detekce emocí či záměru podléhá **Mortonovým strukturně-motivačním pravidlům (Morton's Structural-Motivation Rules, 1977\)**:

> * **Apatické / Agresivní / Útočné projevové vzorce:** Širokopásmové, drsné zvuky s nízkou základní frekvencí (f\_0).  
> * **Submisivní / Přátelské / Párovací vzorce:** Tónové zvuky s vysokou frekvencí a vysokou čistotou spektra.

**Míra jistoty a limity:**

> 1. **Varovná volání (Alarm calls) vs. Páření:** *Vysoce pravděpodobné.* U specifických druhů (např. drůbež, primáti, pěvci) mají varovná a teritoriální/párovací volání disjunktní akustické znaky (frekvenční modulace, délka trvání, pulzní rychlost).  
> 2. **Univerzální „detektor nálady“ napříč druhy:** *Spekulativní / Vědecky neudržitelné.* Zvířata nemají univerzální akustický kód emocí. Emoční stav je nutné modelovat **striktně druhovo-specificky**. Bez vizuálního nebo prostorového kontextu je chybovost akustického odhadu emočního stavu vysoko nad 40 %.

## **2\. Hardwarová rozšíření: Ultrazvuk a Infrazvuk**

Standardní mikrofony v chytrých telefonech (MEMS) a jejich analogově-digitální převodníky (ADC) mají hardwarové filtry:

> * **Frekvenční rozsah běžného smartphone:** cca 20 Hz – 20 kHz (při vzorkovací frekvenci 44.1 kHz nebo 48 kHz je teoretický Nyquistův limit 22.05–24 kHz, fyzikální akustický útlum MEMS membrány začíná nad 18 kHz a pod 50 Hz).

                      FREKVENČNÍ SPEKTRUM A DETEKCE  
Infrazvuk             Slyšitelné spektrum             Ultrazvuk  
(\< 20 Hz)             (20 Hz – 20 kHz)                (\> 20 kHz)  
\|---------------------\|-------------------------------\|---------------------\>  
Elefanti, kytovci,    Ptáci, většina savců,          Netopýři, hlodavci,  
seismické kmity       amfíbie                         hmyz  
\[Vyžaduje piezo/      \[Interní MEMS mikrofon\]        \[Vyžaduje ext. USB-C  
 barometr\]                                            ultrazvukový mic\]

### **A. Ultrazvuk (\> 20 kHz)**

> * **Využití:** Detekce echolokace netopýrů (20–150 kHz), sociálních a distress volání hlodavců (25–50 kHz), komunikace hmyzu (kobylky, cvrčci).  
> * **Hardwarové řešení:** Připojení externího ultrazvukového mikrofonu přes USB-C (např. *Dodotronic Ultramic 384K*, *Wildlife Acoustics Echo Meter Touch*).  
> * **Technické požadavky:**  
  * Vzorkovací frekvence ADC: minimálně 192 kHz (pro signály do 96 kHz) nebo 384 kHz (pro signály do 192 kHz).  
  * Android API: Využití AudioRecord v režimu neupravovaného zvuku (VOICE\_RECOGNITION nebo UNPROCESSED) s podporou High-Resolution Audio přes USB Audio Class 2.0.

### **B. Infrazvuk (\< 20 Hz)**

> * **Využití:** Komunikace slonů (14–24 Hz), velryb, frekvenční projevy velkých kočkovitých šelem (tygří vrčení \~18 Hz), seizmické signály.  
> * **Hardwarové řešení:** Interní mikrofon Androidu má mechanickou membránu a horní propust (High-pass filter), která infrazvuk utlumí. Je nutný buď **externí diferenční mikrobarometr**, nebo **nízkofrekvenční akustický snímač**.  
> * **Technické požadavky:**  
  * Odfiltrování větrného šumu (korelační algoritmy z více mikrofonních vstupů).  
  * Up-sampling nebo frekvenční posun (heterodynování) pro možnost akustického poslechu uživatelem.

## **3\. Technická architektura Android aplikace**

\[Mikrofon (Int/Ext)\]   
        │  
        ▼  
\[AudioRecord API (Android)\] ──(Audio Stream: PCM 16/32-bit)  
        │  
        ▼  
\[Signal Processing (FFT / STFT)\] ──(Generování Mel-Spektrogramu)  
        │  
        ▼  
\[On-Device AI Inference (TensorFlow Lite / ONNX Runtime)\]  
        │  
        ├── Druhová klasifikace (CNN / Audio Spectrogram Transformer)  
        └── Klasifikace chování (RNN / LSTM / temporal classification)  
        │  
        ▼  
\[Uživatelské rozhraní (Jetpack Compose)\]

### **Metodologie zpracování signálu a AI:**

> 1. **Předzpracování:** Krátkodobá Fourierova transformace (STFT) převede časový signál na **Mel-spektrogram**. Melova stupnice odpovídá nelineárnímu vnímání frekvencí.  
> 2. **Klasifikace druhů:** Použití architektur **EfficientNet-B0**, **ResNet-18** nebo **Audio Spectrogram Transformer (AST)** natrénovaných na databázích jako Xeno-Canto (ptáci), iNaturalist nebo Macaulay Library.  
> 3. **Klasifikace stavu (chování):** Použití sekvenčních sítí (LSTM / GRU nebo Temporal Convolutional Networks \- TCN) nad časovou řadou vyextrahovaných akustických příznaků (MFCC \- Mel-frequency cepstral coefficients, znělost, f\_0, spektrální centroid, spektrální entropie).

## **4\. Přehled proveditelnosti a vědecké jistoty jednotlivých funkcí**

| Funkce / Rozsah | Hardwarové požadavky | Metodická spolehlivost | Míra vědecké jistoty |
| :---- | :---- | :---- | :---- |
| **Identifikace ptáků** | Standardní mic | Konvoluční sítě (CNN) nad spektrogramy | **Prokázané** (Vysoká přesnost \>90 %) |
| **Identifikace netopýrů** | Ext. Ultrazvukový mic (192+ kHz) | Akustická analýza echolokačních pulzů | **Prokázané** (Při použití ext. HW) |
| **Identifikace savců (suchozemských)** | Standardní mic | CNN / Transformer modely | **Vysoce pravděpodobné** (Záleží na druhové čistotě dat) |
| **Identifikace ryb** | Hydrofon (podvodní mic) | Spektrální analýza nízkých frekvencí | **Sporné** (Extrémně nízký odstup signálu od šumu) |
| **Detekce teritoriálního / varovného volání** | Standardní / Ext. mic | Analýza f\_0, spektrální entropie a harmonických složek | **Vysoce pravděpodobné** (Striktně druhovo-specifické) |
| **Detekce emocí (strach, agresivita)** | Standardní / Ext. mic | Mortonova pravidla, klasifikace fyziologického arousalu | **Sporné až Spekulativní** (Vyžaduje vizuální validaci) |
| **Infrazvuková detekce (sloni, tygři)** | Ext. mikrobarometr / nízkofrekvenční snímač | Analýza signálu v pásmu 1–20 Hz | **Prokázané** (S ext. HW, interní mic selhává) |

