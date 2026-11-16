# Android repozitorijum

Aktuelna verzija u kodu: **0.5.4**, versionCode **13**. Beleške ispod opisuju prethodne funkcionalne etape; nisu potpuna istorija izdanja.

Samostalan projekat; backend se koristi isključivo preko HTTP API-ja. Za lokalne Python testove pokreni `python3 -m unittest discover -s tests -v`. Gradle komande se pokreću iz korena ovog repozitorijuma.

# Potrošačka korpa za Android

Nativna aplikacija u Kotlinu za Android 7.0 i noviji. Spisak i poslednji uspešno preuzeti katalog čuvaju se u SQLite bazi na telefonu. Pri povratku u aplikaciju proverava se verzija kataloga. Promene postaju vidljive tek kada sve stranice preuzimanja budu uspešno primenjene.

Spisak sabira količine po ceni izabrane ponude. Ako ponuda nestane iz objavljenog cenovnika, ostaje označena na spisku i izuzima se iz zbira. Cene se odnose na objavljene cenovnike trgovaca. Oni ne daju podatke o zalihama u pojedinačnim poslovnicama.

## Lokalni razvoj

1. Pokreni PostgreSQL, kolektor i web servis prema [uputstvu za bek-end](https://github.com/DragisaJevtic/potrosacka-korpa-backend). Kolektor primenjuje migraciju 7 i priprema prvi mobilni snimak. Za ručno osvežavanje koristi `docker compose exec collector python -m collector.catalog`.
2. Otvori direktorijum `android` u Android Studiju. Potrebni su JDK 17 ili noviji i Android SDK 36.
3. Pokreni aplikaciju na emulatoru. Debug varijanta koristi razvojni server `http://192.168.50.65:8088` iz `gradle.properties`. Uređaj mora da može da pristupi tom serveru. Ako pokrećeš zaseban bek-end na istom računaru kao emulator, koristi `-PkorpaDebugApiUrl=http://10.0.2.2:8080`.

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleDebug -PkorpaDebugApiUrl=http://192.168.1.100:8080
```

APK je u `app/build/outputs/apk/debug/app-debug.apk`.

## Google Play

Podrazumevani identifikator paketa je `dev.intelisystems.potrosackakorpa`. Potvrdi ga pre prve objave ili postavi svoj preko `-PkorpaApplicationId=...`. Svako naredno izdanje mora da zadrži identifikator i poveća `versionCode` u `app/build.gradle.kts`.

Postavi produkcioni bek-end na HTTPS i prosledi adresu bez putanje API-ja.

```sh
./gradlew :app:bundleRelease -PkorpaApiUrl=https://korpa.example.com
```

Gradle bez konfigurisanog ključa pravi nepotpisan AAB u `app/build/outputs/bundle/release/`. Za predaju koristi **Build → Generate Signed Bundle / APK** u Android Studiju i svoj upload ključ. Svojstva `korpaApiUrl` i, po potrebi, `korpaApplicationId` možeš podesiti u korisničkom `~/.gradle/gradle.properties` pre pokretanja čarobnjaka.

## AdMob

Aplikacija trenutno radi bez oglasa. Za uvođenje oglasa registruj isti identifikator paketa u AdMob-u, dodaj Google Mobile Ads SDK i App ID u manifest. U produkciji koristi svoj ID oglasne jedinice, a u razvoju Google test ID. Poveži UMP za prikupljanje pristanka i omogući podešavanje privatnosti tamo gde ga UMP zahteva. Učitaj oglase kada UMP dozvoli zahtev i uskladi Play Console deklaracije sa integracijom. [Google Mobile Ads vodič](https://developers.google.com/admob/android/quick-start) · [UMP vodič](https://developers.google.com/admob/android/privacy)

## Početni katalog u APK-u

Asset `app/src/main/assets/catalog.db.gzip` sadrži komprimovanu SQLite bazu sa verzijom serverskog kataloga i praznim spiskom za kupovinu. Pri novoj instalaciji baza se raspakuje u internu memoriju telefona. Ako postojeća instalacija ima praznu bazu jer preuzimanje nikada nije završeno, početne ponude se uvoze uz očuvanje spiska. Instalacije koje već imaju verziju kataloga nastavljaju redovnu delta sinhronizaciju.

Pre novog izdanja osveži početni katalog, pa izgradi APK ili AAB:

```sh
python3 tools/build_catalog_seed.py --api-url http://192.168.50.65:8088
./gradlew :app:assembleDebug
```

Generator proverava da sve stranice pripadaju istoj verziji i proverava integritet SQLite baze. Novi asset zamenjuje prethodni tek nakon uspešne pripreme. SQL šema je zajednička generatoru i Android aplikaciji u `app/src/main/assets/catalog-schema.sql`. Kada menjaš šemu, uskladi verziju baze i migracije. U APK ulaze cene važeće u trenutku generisanja, a aplikacija odmah po otvaranju proverava novije promene.

Ako asset nije prisutan, aplikacija preuzima kompletan katalog preko API-ja. Generisani asset je izuzet iz Gita i treba ga napraviti pre pakovanja iz svežeg checkout-a.

## Kupovina u okolini

Dugme za najjeftiniju kupovinu otvara izbor mesta. Korisnik može odobriti jednokratno određivanje lokacije ili ručno izabrati mesto i ulicu. Za naselja sa manje od 5.000 stanovnika pretraga obuhvata celo naselje. Za 5.000 i više korisnik bira 1, 2, 5, 10 ili 20 km oko lokacije ulice. Udaljenosti su vazdušnom linijom.

Broj stanovnika potiče iz [RZS Popisa 2022](https://popis2022.stat.gov.rs/sr-Latn/popisni-podaci-eksel-tabele). Lokalni asset `settlements.json` i serverski istoimeni fajl generišu se iz iste tabele. Gradovi Beograd i Niš sabrani su preko delova istog naselja po gradskim opštinama.

API `POST /api/mobile/cheapest` prima `area` sa identifikatorom naselja i, za radijus, koordinatama i udaljenošću. Kandidati se ograničavaju na poslovnice u toj oblasti. Poznati cenovnik formata poslovnice ima prednost nad drugim cenovnikom istog lanca za isti proizvod. Ako nema takve ponude, koristi se raspoloživa cena lanca i prikazuje oznaka „Cena lanca”. Najbliža odgovarajuća poslovnica bira se među poslovnicama sa poznatim koordinatama. Za ručni izbor malog mesta bez GPS-a prikazuje se poslovnica u tom mestu.

`web/store-coordinates.json` sadrži potvrđena povezivanja sa javnim lokatorima trgovaca, sa izvornim URL-ovima. Aktuelni snimak sadrži 1.446 poslovnica iz sedam lanaca. Poslovnice bez koordinata mogu učestvovati u pretrazi celog malog mesta kada je naselje nedvosmisleno poznato. Prazan rezultat može značiti da još nemamo potrebne podatke o lokacijama. Aplikacija prikazuje tu informaciju.

Generator lokacijskih podataka je u backend repozitorijumu (`python -m collector.build_location_data --help`). Posle generisanja prekopiraj njegov `web/settlements.json` u `app/src/main/assets/settlements.json` ovog repozitorijuma i proveri izmene. Pored projektnih zavisnosti, generator koristi `beautifulsoup4` (provereno sa 4.15.0). Potrebni su zvanični RZS Excel, JSON izvoz aktuelnih poslovnica i sačuvani zvanični lokatori trgovaca. Nakon zamene serverskih JSON fajlova ponovo pokreni web servis, a nakon izmene naselja izgradi novu aplikaciju.

## Skener proizvoda (0.3.1)

Tab „Skeniraj” odmah otvara kameru u portrait režimu, bez popup-a i ručnog unosa. ZXing Android Embedded
4.3.0 je ugrađen u APK i otvara kameru uz runtime dozvolu. Za skeniranje nisu
potrebni Google Play services ili preuzimanje modula. Za skeniranje je potrebna dozvola za kameru. Dokumentacija biblioteke:
https://github.com/journeyapps/zxing-android-embedded

Pretraga koristi postojeći HTTPS `/api/compare/<barcode>` i zahteva internet.
Prikazuje najnižu objavljenu cenu po oznaci trgovca/prodajnog formata, sortirano
po ceni. To nije provera zaliha ili pretraga u okolini. Proverava se kontrolna
cifra i odbijaju interni kodovi prodavnica. QR se prihvata samo ako sadrži čist
validan broj proizvoda; URL-ovi se ne otvaraju. Skraćeni UPC-E se proširuje na UPC-A pre pretrage.
EAN/UPC varijante sa vodećim nulama se pretražuju zajedno.

Provera:
```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  -PkorpaDebugApiUrl=https://korpa.intelisystems.dev
```
Na telefonu proveriti kameru, odustajanje od skeniranja, odbijenu dozvolu, nepoznat kod,
isključen internet i rezultat za poznat proizvod. Test APK koristi postojeći
potpis i package pa se instalira kao ažuriranje uz očuvanje liste.

## Ispravke povratnih informacija testera (0.4.0)

- Tačan naziv naselja ima prednost nad poklapanjem naziva opštine (Pančevo).
- Ulica i kućni broj su odvojena polja. Sufiksi poput 73a su dozvoljeni;
  broj stana /4 se odbija jasnom porukom. Geocoder rezultat mora da odgovara
  mestu, ulici i broju. Nepotvrđena adresa nije proglašena nepostojećom.
- Potvrđena oblast, adresa i radijus čuvaju se lokalno. Ponovno otvaranje jasno
  označava sačuvanu lokaciju; „Moja lokacija” traži novu GPS/mrežnu lokaciju.
- Prikaz poslovnice uključuje lanac, naziv/oznaku poslovnice, adresu i udaljenost.
- Grupe nude potkategorije. Za kombinovanu kategoriju pića, kafe i čaja koriste
  se kategorija izvora i nazivi proizvoda; to je heuristika, nije potpuna
  klasifikacija svih proizvoda. Likeri su isključeni iz kafe i čaja.

Ručna provera na telefonu: Pančevo latinicom/ćirilicom, Cara Dušana + 73,
73a i 73/4, odsutan geocoder/internet, ponovno otvaranje izabrane oblasti,
dve poslovnice različitih lanaca na istoj adresi, kafa/čaj naspram likera.

## Raspon cena u pretrazi (0.4.1)

Rezultati kategorijske pretrage prikazuju minimum–maksimum za isti pouzdan
GTIN proizvod u celom lokalnom katalogu. Ispod ostaje cena izabrane ponude
koja ulazi u spisak. Jedna ili više jednakih cena prikazuju se kao jedan iznos.
Ponude bez pouzdanog GTIN ključa ne spajaju se po nazivu. Raspon nije ograničen
lokacijom niti filterom trgovca i predstavlja poslednje sinhronizovane cene.

SQLite katalog prelazi na verziju 2 sa indeksom `(product_key, price)`.
Nadogradnja obnavlja samo katalog iz novog ugrađenog snimka, uz očuvanje tabele
`shopping`, pa proverava novije promene servera. Seed mora biti generisan novim
`build_catalog_seed.py` pre pravljenja APK-a.
