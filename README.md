# Potrošačka korpa — Android

Nativna Kotlin aplikacija za pretragu cena, spisak kupovine, skeniranje barkoda i najjeftiniju kupovinu u okolini. Katalog i spisak čuvaju se lokalno u SQLite-u; podaci se sinhronizuju preko backend API-ja.

Aktuelna verzija koda: **0.5.4**, `versionCode` **13**. Paket: `dev.intelisystems.potrosackakorpa`. Minimum Android 7.0 (API 24), compile/target SDK 36.

## Repozitorijumi

- [Backend](https://github.com/DragisaJevtic/potrosacka-korpa-backend)
- [Android](https://github.com/DragisaJevtic/potrosacka-korpa-android)
- [Web](https://github.com/DragisaJevtic/potrosacka-korpa-web)

## Pokretanje

Otvori koren ovog repozitorijuma u Android Studiju. Postavi Android SDK 36 i JDK koji zahteva uključeni Gradle wrapper; daemon kriterijumi su u `gradle/gradle-daemon-jvm.properties`. `ANDROID_HOME` ili lokalni `local.properties` određuje SDK putanju.

```sh
./gradlew :app:assembleDebug
```

Debug backend je NUC `http://192.168.50.65:8088`; telefon/emulator mora imati mrežni pristup. Za drugi backend:

```sh
./gradlew :app:assembleDebug -PkorpaDebugApiUrl=https://korpa.intelisystems.dev
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Backend izvorni kod nije potreban za build.

## Početni katalog

```sh
python3 tools/build_catalog_seed.py --api-url http://192.168.50.65:8088
```

Generator proverava verziju kroz sve strane i integritet SQLite baze. Asset `app/src/main/assets/catalog.db.gzip` je izuzet iz Gita i treba ga generisati pre izdanja koje treba da sadrži offline katalog. Bez asseta aplikacija preuzima katalog preko API-ja. Lokalna kopija postojećeg asseta sačuvana je pri podeli projekta.

## Testovi

```sh
python3 -m unittest discover -s tests -v
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Python testovi proveravaju generator i lokalni SQL. Kotlin testovi proveravaju barkod i unos lokacije. Telefon/emulator je potreban za ručnu proveru kamere, dozvola, GPS-a i nadogradnje uz očuvanje spiska.

## Dokumentacija i izdavanje

- [Specifikacija](SPEC.md) i [backlog](BACKLOG.md)
- [Razvoj, API i izdavanje](docs/DEVELOPMENT.md)
- [Detalji funkcija iz prethodnog projekta](docs/FEATURES.md)
- [Firebase distribucija](distribution/README.md)
- [Podela repozitorijuma](docs/REPOSITORY-SPLIT.md)

Privatni signing ključevi, lokalne putanje, APK-ovi i generisani katalog nisu deo Gita. Podela ne menja verziju niti objavljuje novo izdanje. Beleške distribucije trenutno završavaju na 0.4.1; potrebna je potvrda promena za kasnija izdanja.

Rezultati provere podele: [VALIDATION.md](docs/VALIDATION.md).

## Verzija 0.5.0 — backend sa istorijom promena

U rezultatima pretrage proizvoda sa pouzdanim barkodom izaberi „Uporedi cene i istoriju”. Poređenje radi i posle skeniranja. Dugme sa cenom otvara istoriju konkretnog izvora/prodajnog formata: događaji, poslednja potvrda i stepenasti grafikon sa prekidima. Zakazana aktivacija nije prikazana kao nova provera izvora. Prikaz do 500 događaja označava skraćenje. Poređenje i istorija zahtevaju mrežu; katalog i spisak ostaju lokalni.

Sinhronizacija proverava doslednost generacije, reset zastavice i kursora pre upisa. Nepotpuno preuzimanje ne zamenjuje lokalni katalog; reset ne briše spisak. Novi APK koristi produkcijski HTTPS API kada se gradi sa `-PkorpaDebugApiUrl=https://korpa.intelisystems.dev`.

## Verzija 0.5.1 — najjeftinija kupovina na celom ekranu

Izbor mesta i rezultati otvaraju zaseban ekran sa povratkom na spisak. Za mesta sa manje od 5.000 stanovnika podrazumevana pretraga uključuje celo mesto i okolne poslovnice do 10 km od potvrđene lokacije/centra; radijus se bira među 1, 2, 5, 10 i 20 km. Moguća je i eksplicitna pretraga samo mesta. Ako centar ne može da se potvrdi, koristi lokaciju telefona ili samo izabrano mesto. Spisak i potvrđena oblast se čuvaju.

Okolina se određuje vazdušnom udaljenošću od potvrđene tačke, ne granicama susednih naselja ni dužinom puta. Poslovnice u okolini bez poznatih koordinata nisu uključene. Zahtevan je backend sa režimom `town_nearby` (postavljen 15.11.2026.); stariji server se ne predstavlja kao da je pretražio okolinu.

### Rezervna pretraga izvan oblasti

Za artikle bez ponude u izabranoj oblasti aplikacija traži najbližu poznatu
poslovnicu sa objavljenom cenom, bez ograničenja cene ili udaljenosti.
Takvi rezultati su označeni „Najbliža poznata ponuda van izabrane oblasti“;
ukupan iznos uključuje i njih. Udaljenost je vazdušna, a cenovnik nije potvrda zaliha.
Bez potvrđenih koordinata aplikacija traži potvrdu lokacije za rezervnu pretragu.
Potreban je API sa podrškom za `nearest_fallback` (NUC: `nearest-20261115`).

Provera 15.11.2026: `:app:testDebugUnitTest` (16 testova) i `:app:lintDebug`
prošli. APK nije pravljen; pokretanje i testiranje iz Android Studija.
