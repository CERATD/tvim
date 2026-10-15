# Verificare Tvim 1.5

- Build release: reușit.
- 32 teste trecute, 0 erori/eșecuri.
- Android Lint: 0 erori.
- Semnătură APK și aliniere ZIP/16 KB: verificate.
- Pachet: com.example.tvim, versionCode 7, Android minim 7.0.
- SHA-256 APK: e0d7ae55b53fdb13b1da89c178a4523f71d47b27e1229d803233eece21dcc93a

## Propoziții aleatorii

16 propoziții în rusă au fost verificate textual în FB2-ul furnizat de utilizator: George R. R. Martin, „Игра престолов. Часть I”. Traducerile englezești sunt proprii, realizate din textul rus. Nu a fost inclusă cartea integrală.

Teste noi: selecție fără repetare consecutivă, traduceri disponibile pentru toate propozițiile, text identic după recrearea activității, păstrarea selecției între limbi, noua propoziție pentru o nouă alarmă, normalizarea majusculelor/punctuației/spațiilor, respingerea textului incomplet sau greșit.

Testele de componente rulează cu Robolectric pe API 28 și 33. Verificarea pe telefon fizic nu a fost efectuată.

APK-ul folosește aceeași cheie locală debug ca versiunea 1.4 livrată anterior. Pentru actualizare peste alte versiuni, semnătura instalării trebuie să coincidă.

## UI 1.5

Applied the generated black/red concept to time editing, sound selection, mission cards, vibration selection, gradual volume, alarm ringing, typing, shake progress, sleep and history. History charts use real recorded events. The home screen retains the next-alarm ring and four-tab navigation.

Build, existing 32 regression tests and lint passed. Visual layout has not been verified on an emulator or physical device. The concept is adapted to native Android controls; it is not a pixel-identical image reproduction.
