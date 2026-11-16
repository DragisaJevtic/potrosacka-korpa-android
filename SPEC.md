# Specifikacija Android aplikacije

Postojeća nativna Kotlin aplikacija za Android 7.0+; compile/target SDK 36. Verzija 0.4.3 (code 8), identifikator `dev.intelisystems.potrosackakorpa`.

## Postojeće funkcije

- Lokalni SQLite katalog, opcioni ugrađeni početni snimak i verzionisana HTTP sinhronizacija.
- Pretraga, kategorije/potkategorije i raspon cena pouzdano prepoznatog proizvoda.
- Spisak kupovine sa količinama i cenom izabrane ponude.
- ZXing skeniranje proizvoda i poređenje preko API-ja.
- GPS/ručni izbor mesta, adrese i radijusa; čuvanje potvrđene oblasti.
- Najjeftinija kupovina u oblasti uz jasno ograničenje na objavljene cene.

## Kriterijumi podele

Gradle build i Python testovi rade iz korena ovog repozitorijuma, bez backend izvornog koda. API URL je konfigurabilan postojećim Gradle svojstvima. Paket, verzija, lokalna šema i ponašanje aplikacije nisu promenjeni podelom. Početni katalog nije u Gitu; generator ga pravi preko API-ja, a aplikacija podržava njegovo odsustvo.

Nove funkcije, Play objava i monetizacija nisu deo podele.

## Backend migracija 9 (0.5.0)

- Mobilni katalog zadržava delta protokol; nova generacija i povratak revizije zahtevaju reset. Sve strane moraju imati istu verziju/reset zastavicu i napredujući kursor pre transakcionog upisa.
- Istorija je zaseban online zahtev za tačan barkod/izvor/format. Ne čuva se kao dnevni snapshot na telefonu.
- `rows` predstavljaju prvo viđenje, promene uslova, povlačenje i povratak. `points` predstavljaju potvrđene periode; NULL prekida grafikon. `scheduled` ne menja vreme potvrde izvora.
- Poređenje iz pretrage dostupno je za pouzdane GTIN kodove. Interni kodovi se ne porede između trgovaca.
