# Tvim 1.7

Aplicație Android pentru alarme, cu interfața existentă în rusă și engleză. Necesită Android 7.0 sau mai nou.

## Utilizare

1. Instalează APK-ul și deschide aplicația.
2. Permite notificările. Dacă pe pagina principală apare o avertizare, apasă pe ea pentru a permite alarmele exacte sau afișarea peste ecranul blocat.
3. Verifică volumul de sistem pentru alarme, apoi creează o alarmă.
4. Fără zile selectate, alarma sună o singură dată și se dezactivează. Cu zile selectate, se repetă în acele zile.
5. În selectorul de melodii poți asculta zece sunete incluse sau importa un fișier audio, apoi apăsa „Salvează”.
6. „Timer” înlocuiește „Somn”: setează ore, minute și secunde, pornește, pune pauză, continuă sau resetează. La final sună alarma. Alarmele de trezire existente rămân în pagina principală.
7. „Stopwatch” măsoară timpul și turele. Istoricul ultimelor 100 de alarme se află în Setări.

Sunetul folosește canalul de alarmă al telefonului și continuă când închizi ecranul aplicației. Volumul ales în aplicație se aplică în limitele volumului de sistem. Vibrația și creșterea treptată a volumului respectă setările fiecărei alarme. Dacă un fișier importat nu mai este disponibil, alarma folosește sunetul inclus „Morning Light”.

Oprirea poate fi simplă, prin calcul, prin tastare sau prin scuturare. Dacă telefonul nu are accelerometru, misiunea de scuturare este înlocuită cu tastarea. Semnalul se oprește automat după 15 minute și este marcat ca ratat. Alarmele simultane sunt tratate pe rând.

## Corecții față de 1.3

- Programare exactă și zile de repetare corecte.
- Serviciu de redare în prim-plan, notificare de alarmă și acces la ecranul de oprire.
- Reprogramare după repornire, actualizare, schimbarea orei sau fusului orar.
- Restaurarea semnalului activ dacă Android recreează serviciul.
- Șase melodii originale offline, previzualizare și import cu acces persistent.
- Volum, vibrație, tipare și creșterea treptată a volumului funcționale.
- Identificatori unici pentru programare: alarmele nu se mai suprascriu la coliziuni de hash.
- Citirea datelor vechi completează setările lipsă; datele ilizibile sunt păstrate separat pentru recuperare.
- Pagina „Următoarea alarmă” folosește data reală a următoarei declanșări.
- Istoric real și configurarea unui program de trezire în locul paginilor blocate.
- Verificarea actualizărilor compară numeric versiunile și deschide pagina oficială GitHub.
- Ecranul de oprire se poate derula pe ecrane mici și cu tastatura deschisă.

## Construire

JDK 21, Android SDK 37 și Build Tools 36.0.0. Specifică SDK-ul în local.properties sau ANDROID_HOME.

Comandă: gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease

Sunetele pot fi regenerate cu python tools/generate_sounds.py. Sunt tonuri sintetizate originale; nu includ înregistrări comerciale.

Configurația de semnare release existentă în repository folosește cheia locală debug. Pentru distribuție publică, configurează cheia originală a aplicației. Actualizarea peste un APK vechi este posibilă numai dacă semnăturile coincid; nu dezinstala versiunea veche dacă trebuie să păstrezi datele.

## Limite de verificare

Testele de componente Android folosesc Robolectric, pe API 28 și 33. Acestea verifică programarea, stocarea, pornirea serviciului, alegerea sunetului, volumul, bucla audio, oprirea și alarmele simultane. Nu înlocuiesc verificarea acustică și testarea pe un telefon real.

Nu a fost conectat un telefon pentru verificarea hardware. Înainte de folosirea zilnică, verifică o alarmă cu ecranul blocat, una după repornire și una cu aplicația scoasă din lista de aplicații recente. După repornire, deblochează telefonul o dată. Oprirea forțată din setările Android suspendă alarmele până când aplicația este deschisă din nou; unele telefoane aplică restricții suplimentare de baterie. Modul „Nu deranja” trebuie să permită alarmele.

Istoricul arată răspunsul la alarmă și nu măsoară somnul.


## Misiunea de tastare — 1.4.1

Selectează misiunea „Typing” / „Печать” în setările alarmei (denumirea depinde de limba interfeței).

Aplicația alege aleatoriu una dintre 16 propoziții scurte din cartea furnizată de utilizator: „Игра престолов. Часть I”, George R. R. Martin. Colecția este disponibilă offline în rusă și engleză, cele două limbi actuale ale aplicației. Textele englezești sunt traduceri proprii ale fragmentelor rusești, nu citate din ediția engleză.

Propoziția rămâne aceeași la rotirea ecranului și la recrearea activității. Următoarea alarmă cu această misiune primește o propoziție diferită de cea precedentă. Pentru oprire trebuie tastat întregul text; majusculele, punctuația și spațiile suplimentare sunt ignorate. În rusă, е și ё sunt acceptate ca echivalente. Textul greșit sau incomplet nu oprește alarma.

Arhiva cărții și textul integral nu sunt incluse în proiect sau APK.

## UI 1.5

Interfața folosește conceptul din design/tvim-ui-concept-v1.png: negru, carduri rotunjite, accent roșu, timp digital compact, selecție de sunete și misiuni, progres circular și istoric cu date reale.

## Download Tvim 1.7

[Download Tvim 1.7 APK](https://github.com/CERATD/tvim/raw/refs/heads/master/downloads/Tvim-1.7.apk)

[Release notes](RELEASE-1.7.md)

Android 7.0 or newer. This APK uses the same local debug signing key as the previously delivered 1.4 and 1.4.1 builds. See VERIFICATION.md for tests and limitations.

## Update 1.6

Analog clock selection is restored. The timer replaces Sleep. Vibration uses alarm attributes and includes a test button. Install over version 1.5 without uninstalling; the application ID and signing certificate match.


## Update 1.7

Stopwatch replaces Analysis; history is available in Settings. Timing survives app recreation and continues while the app is closed. After a device reboot it pauses at the last persisted checkpoint.

Imported audio is shared across all alarms. New imports are validated and copied into private app storage (50 MB maximum); identical content is stored only once. Existing content-URI alarm sounds are registered in the library without changing the alarms. Legacy URI entries still depend on the original file permission; reimport them to create a durable private copy.

The ten built-in loops are deterministic original synthesis with different rhythms and timbres. Run tools/generate_sounds.py to reproduce them.
