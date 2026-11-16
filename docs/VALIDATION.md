# Provera podele — 14.11.2026.

Python testovi: svih 7 prošlo. Gradle :app:testDebugUnitTest, :app:lintDebug i :app:assembleDebug: BUILD SUCCESSFUL. Kotlin/JUnit: 6 testova prošlo. Lint: 0 grešaka, 33 prijavljene stavke/upozorenja u postojećem kodu. APK je lokalni debug build; nije distribuiran. Kamera, GPS i nadogradnja na telefonu nisu ručno provereni.

Aktivni servisi na NUC-u i VPS-u nisu menjani. Rezultati važe za lokalno pripremljenu podelu, ne potvrđuju stanje produkcije ili budućih izdanja.

## 0.5.0 — 15.11.2026.

- 13 Kotlin/JUnit testova prolazi, uključujući novu generaciju/reset, delta povlačenja, odbijanje nedoslednih strana i kursora, povratak revizije, istorijske NULL prekide, povlačenje, zakazanu aktivaciju i tačan URL izvora/formata.
- 7 Python/SQLite testova prolazi: pretraga/raspon, migracija sa očuvanim spiskom i konzistentno generisanje početnog kataloga.
- `lintDebug`: 0 grešaka, 39 upozorenja (uključujući postojeće hardkodirane tekstove/lokalizaciju). Debug APK uspešno izgrađen sa produkcijskim HTTPS API-jem.
- Početni katalog: 565.250 ponuda, generisan sa produkcije, SQLite integrity check prošao; asset nije deo Gita.
- Emulator Pixel 8 Pro API 35: sveža instalacija, raspakivanje početnog kataloga, uspešan HTTP 304 pri sinhronizaciji, izbor kategorije hrane/mlečnih proizvoda, poređenje iz rezultata i otvaranje istorije za tačan izvor/format. Prikaz potvrđenog perioda, grafikona i događaja pregledan na snimku ekrana. Nema AndroidRuntime greške.
- Nije rađena nova provera kamere na fizičkom telefonu niti Firebase objava. Postojeći spisak se i dalje čuva u odvojenoj tabeli; puna mrežna reset sinhronizacija na uređaju nije ponavljana u ovoj proveri (protokol je pokriven unit testovima).

## 0.5.1 — 15.11.2026.

14 Android unit testova prolazi; lint i assembleDebug završeni bez grešaka. Backend: 35 testova prošlo u zasebnoj PostgreSQL bazi na NUC-u, uključujući novo proširenje malog mesta na okolinu, lokalne poslovnice izvan radijusa, nedostajuće koordinate, dvosmislena imena i validaciju parametara. Proveren novi režim preko javnog produkcijskog API-ja.

Emulator API 35: izbor proizvoda i dodavanje na spisak; otvaranje zasebnog ekrana; izbor Banatskog Brestovca i geokodirana potvrda centra; pretraga 10 km, promena na 20 km i pamćenje oblasti; prikaz ponude iz Kovina sa adresom i udaljenošću na celom ekranu. Ekran pregledan preko snimka. Prazno stanje naknadno promenjeno da ne prikazuje iznos/uštedu 0 RSD i završni build/lint prošao. Fizički telefon i Firebase objava nisu obuhvaćeni.

## 0.5.2 — promena mesta

14 unit testova, lint i assembleDebug prolaze. U emulatoru API 35 učitana je sačuvana adresa „Pančevo, Cara Dušana 73”; pri unosu „Kovin” oba adresna polja odmah su prazna i ostaju prazna posle izbora mesta. Potvrđeno je i da se sačuvana adresa ispravno vraća pri početnom otvaranju. Promena mesta poništava prethodne koordinate i odgovore već pokrenutih upita za lokaciju/adresu. Backend nije menjan.

## Firebase build 0.5.3 (12), 15.11.2026

- Produkcijski HTTPS API; postojeći debug potpis i isti package.
- `testDebugUnitTest` (16 testova), `lintDebug`, `assembleDebug` i `apksigner verify` prolaze.
- Početni katalog osvežen produkcijskim delta podacima sa revizije 7 na 9 (45 promenjenih redova).
  Provereni SQLite integritet, prazan spisak kupovine i odsustvo početnih uzvičnika u nazivima.
- SHA-256 APK-a: `abce3faad43ca0c02ff2b1a49a9c3a21a66af9f93c434c01ea603e0f910fff2a`.
- Firebase upload uspešan: izdanje `7srj5ntkbvk6g`, 0.5.3 (12).
  https://console.firebase.google.com/project/intelisystems-potrosacka-korpa/appdistribution/app/android:dev.intelisystems.potrosackakorpa/releases/7srj5ntkbvk6g
  Beleške objavljene; testeri/grupe nisu automatski dodavani niti su slane pozivnice.

## 16.11.2026: kartice prodavnica i Google Maps

Rezultati kupovine su u zasebnim karticama po poslovnicama, sa međuzbirom,
artiklima i dugmetom Navigacija. Strukturirana adresa otvara Maps directions;
bez adrese otvara se Maps pretraga po nazivu poslovnice i njenom mestu.
Ako Maps nije instaliran, koristi se HTTPS link kroz dostupnu aplikaciju/pregledač.
Prošlo 18 unit testova i lintDebug; pokriveni null/prazna adresa, izbor mesta
poslovnice i kodiranje specijalnih znakova u Maps URL-u. APK nije pravljen niti
je novo izdanje objavljeno na Firebase; provera interakcije na uređaju ostaje
kroz Android Studio.

## 16.11.2026: ugrađeni ekran skenera

`PortraitScannerActivity` sada prikazuje `DecoratedBarcodeView` unutar našeg
rasporeda: zajedničko zaglavlje i tema, Nazad, uputstvo i dugme za svetlo kada
uređaj podržava blic. Google/eksterna skener aplikacija nije potrebna.
Postojeći CaptureActivity upravlja dozvolom kamere, pauzom/nastavkom, oslobađanjem
kamere i ScanContract rezultatom; obrada EAN/UPC koda ostaje ista.
Prošlo 18 unit testova i lintDebug, bez pravljenja APK-a.
Na telefonu proveriti dozvolu/odbijanje kamere, očitavanje stvarnog bar-koda,
Nazad bez rezultata, svetlo i odlazak aplikacije u pozadinu/povratak.
Te provere sa stvarnom kamerom nisu izvršene u ovom koraku.

## 16.11.2026: dodavanje nakon skeniranja

Rezultat skeniranja sadrži dugme Dodaj na spisak za kupovinu. Ponude za izbor
nalaze se u lokalnom katalogu po kanonskom GTIN-u; korisnik bira trgovca/cenu
ako postoji više ponuda. Koristi se stvarni ID kataloga i postojeći `addOffer`
put, pa se spisak odmah osvežava, a ponovno dodavanje povećava količinu.
Bez lokalne ponude prikazuje se poruka da treba osvežiti katalog, bez izmišljanja ID-ja.
Rezultati, istorija i dugmad koriste KorpaUi stil.
Prošlo 18 Android unit testova, lintDebug i 8 Python/SQLite testova; novi SQLite
scenario proverava odvajanje proizvoda/trgovaca, izabrani ID i povećanje količine.
APK nije pravljen; krajnji tok skeniranja proveriti na telefonu iz Android Studija.

## 16.11.2026: pretraga sa početnog ekrana kategorija

Ekran Kategorije ima polje za pretragu svih proizvoda/prodavnica. Dok postoji
upit, umesto mreže kategorija prikazuju se rezultati sa postojećim dodavanjem,
poređenjem i učitavanjem sledeće strane. Brisanje upita vraća kategorije;
Nazad takođe vraća početno stanje. Pretraga unutar kategorije ostaje ograničena
na tu kategoriju. Upit se čuva kroz postojeće stanje aktivnosti.
Prošlo 18 Android unit testova, lintDebug i 9 Python/SQLite testova,
uključujući pretragu preko više kategorija i očuvanje ograničenja kategorije.
APK nije pravljen niti objavljen.

## 16.11.2026: čekanje pretrage i tastatura

Pretraga prikazuje indikator „Pretražujemo katalog…“, čeka kratku pauzu u kucanju
(300 ms) i prekida zastarele SQLite upite pomoću CancellationSignal. Rasponi cena
računaju se tek za traženu stranicu rezultata, uz poređenje sa celim katalogom.
Greška upita prikazuje poruku i ponovni pokušaj umesto praznog ekrana.
Dok je IME vidljiv, aplikacija ne registruje svoj Android 13+ Back callback:
prvi povratak zatvara tastaturu, bez resetovanja upita.
Prošli Android unit testovi i lintDebug; svih 10 Python/SQLite testova prolazi,
uključujući paginaciju i cenu uporedive ponude izvan trenutne stranice.
Čitanje logova/snimka ekrana telefona nije uključivalo instalaciju ili unos na uređaju.
Popravku ponašanja tastature treba potvrditi pokretanjem novog koda iz Android Studija.

## 16.11.2026: počeci i rezultati pretrage sa brojačima

Tokom pretrage prikazuje se ukupan broj ponuda u aktivnom opsegu (ceo katalog
ili izabrana kategorija). Pre kartica prikazuje se ukupan broj pronađenih i
broj trenutno prikazanih ponuda. Brojevi i stranica čitaju se u istoj SQLite
transakciji; zastarele pretrage ne mogu promeniti prikaz. Ponude različitih
prodavaca broje se zasebno, što je naznačeno u ekranu.
Prošli Android unit testovi i lintDebug, kao i 11 Python/SQLite testova.
Test brojača obuhvata više od jedne stranice, opseg kategorije i nula rezultata.
APK nije pravljen.

## 16.11.2026: direktno dodavanje iz poređenja cena

Poređenje otvoreno iz kategorija nema dugme Novi kod; to dugme ostaje samo
posle skeniranja. Ponovni pokušaj zadržava način ulaska na ekran.
Dodaj na spisak odmah dodaje proizvod, bez dijaloga za izbor trgovca.
Kada već postoji aktivna ponuda tog GTIN-a na spisku, koristi se njen ID radi
povećanja količine. Inače se uzima najniža lokalna ponuda kao početna procena.
Kasnija Najjeftinija kupovina poredi po GTIN-u i nije ograničena tim trgovcem.
Prošli Android unit testovi i lintDebug. APK nije pravljen.

## Test izdanje 0.5.4 (13), 16.11.2026

- APK koristi produkcijski API `https://korpa.intelisystems.dev` i postojeći debug potpis.
- Prošli Android unit testovi (18), lintDebug i 11 Python/SQLite testova.
- Provereni APK potpis, package i verzija; produkcijski health endpoint odgovara.
- SHA-256: `3e8391c922528542e6dd985905517f92bc5d36306e2c8622d7fbcbaceea68445`.
- Početni katalog ostaje prethodni provereni snimak; pri pokretanju se sinhronizuje.
- Fizičko skeniranje, Maps i ponašanje tastature navedeni su testerima za proveru.
- Firebase potvrdio objavu beležaka i distribuciju svim 5 registrovanih testera.
- Izdanje: https://console.firebase.google.com/project/intelisystems-potrosacka-korpa/appdistribution/app/android:dev.intelisystems.potrosackakorpa/releases/4itjdonhme8o8

## 16.11.2026: prijave testera — pretraga i lokacija

- Više reči traži se nezavisno, uz AND između reči i OR između naziva/prodavca.
  „Grand 250“ nalazi „Grand kafa Gold 250 g“ i „Kafa 250g Grand“.
  Brojač i lista koriste isti filter; LIKE specijalni znaci ostaju literalni.
- Isključena lokacija nudi otvaranje sistemskih podešavanja; nakon povratka
  proveravaju se dozvola i uključeni provajderi pa se nastavlja lociranje.
  Trajno odbijena dozvola nudi podešavanja aplikacije. Dozvola nije isto što i GPS prekidač.
- Adresa/centar naselja imaju eksplicitno dugme Potvrdi i pretraži, sa izborom
  ako ima više rezultata. Tastatura se skriva; zastarela potvrda se ignoriše.
- Prošlo 20 Android unit testova, lintDebug i 12 Python/SQLite testova.
- Tok sistemskih podešavanja/dozvola i potvrdu adrese proveriti na telefonu;
  u ovom koraku APK nije pravljen niti je menjan povezani uređaj.
