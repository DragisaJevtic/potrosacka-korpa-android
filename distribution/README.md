# Firebase App Distribution

- Projekat: `intelisystems-potrosacka-korpa`
- Android app: `1:466611839381:android:7cac04bc6746340de1969c`
- Package: `dev.intelisystems.potrosackakorpa`
- API: `https://korpa.intelisystems.dev`
- Konzola: https://console.firebase.google.com/project/intelisystems-potrosacka-korpa/appdistribution

Testeri su registrovani u projektu. Otprema bez `--testers` ili `--groups`
ne šalje pozivnice. U App Distribution odabrati Android aplikaciju, dodati
mejl adrese testera i dodeliti im izdanje. Link ka konzoli nije javni APK link.

Za sledeće izdanje povećati `versionCode` i promeniti `versionName` u
`app/build.gradle.kts`, ažurirati `release-notes.txt`, pa pokrenuti:

```sh
bash distribution/upload.sh
```

CLI mora biti prijavljen Firebase nalogom sa pristupom projektu (`firebase login`).
Tokeni i privatni ključevi se ne upisuju u projekat. Za samu APK distribuciju
nisu potrebni Firebase SDK, Analytics ili `google-services.json`.

Ove test verzije koriste postojeći debug potpis radi kompatibilnosti sa ranije
ručno instaliranim APK-ovima. Ne brisati/promenjivati potpis između test izdanja.
Pre Google Play objave podesiti zasebno release potpisivanje i plan prelaska
sa debug potpisa; ovo nije Play release build.

Za slanje postojećim testerima koristiti `firebase appdistribution:testers:list`
i proslediti proverenu listu kroz `--testers-file` pri distribuciji. Liste mejlova
čuvati van repozitorijuma. Izdanje 0.5.4 (13) namenjeno je svim postojećim testerima.
