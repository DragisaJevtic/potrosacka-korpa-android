# Razvoj i provera

Potreban je JDK kompatibilan sa uključenim Gradle wrapper-om (projekat ima daemon JVM podešavanje), Android SDK 36 i mreža za Gradle zavisnosti. Java izvorni nivo je 17. Postavi `ANDROID_HOME` ili lokalni `local.properties` sa `sdk.dir`; taj fajl ne ide u Git.

```sh
python3 -m unittest discover -s tests -v
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug URL je `http://192.168.50.65:8088`. Za drugi backend:

```sh
./gradlew :app:assembleDebug -PkorpaDebugApiUrl=https://korpa.intelisystems.dev
```

Početni katalog se generiše pre izdanja; potreban je dostupan, popunjen backend:

```sh
python3 tools/build_catalog_seed.py --api-url http://192.168.50.65:8088
```

Asset `app/src/main/assets/catalog.db.gzip` je namerno izuzet iz Gita. Kopija postojećeg asseta sačuvana je lokalno pri podeli, ali je nema u novom checkout-u. Bez njega aplikacija preuzima katalog preko API-ja.

## Izdanje

APK: `app/build/outputs/apk/debug/app-debug.apk`. Distribucija kroz Firebase opisana je u `distribution/README.md`; upload skripta šalje stvarno izdanje, nije test komanda. Debug potpis je izvan repozitorijuma: na drugom računaru ne pretpostavljati da novi debug ključ može ažurirati stare instalacije.

Za release koristi HTTPS `korpaApiUrl`, stabilan package i odgovarajući privatni signing ključ van Gita. Podela repozitorijuma ne povećava automatski verziju niti objavljuje novo izdanje.

Ručna provera: pokretanje sa i bez mreže, sinhronizacija, očuvanje spiska, kamera/dozvole, poznat i nepoznat barkod, izbor Pančeva, adresa sa kućnim brojem, pamćenje oblasti i raspon cena.

## Veza sa backendom

API ugovor je u [backend dokumentaciji](https://github.com/DragisaJevtic/potrosacka-korpa-backend/blob/main/docs/API.md). Za novu verziju naselja prenesi backend `web/settlements.json` u `app/src/main/assets/settlements.json`, pregledaj diff i commituj ga uz izdanje.
