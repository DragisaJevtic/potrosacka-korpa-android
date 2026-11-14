# Potrošačka korpa — Android

Nativna Kotlin aplikacija za pretragu cena, spisak kupovine, skeniranje barkoda i najjeftiniju kupovinu u okolini. Katalog i spisak čuvaju se lokalno u SQLite-u; podaci se sinhronizuju preko backend API-ja.

Aktuelna verzija koda: **0.4.3**, `versionCode` **8**. Paket: `dev.intelisystems.potrosackakorpa`. Minimum Android 7.0 (API 24), compile/target SDK 36.

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
