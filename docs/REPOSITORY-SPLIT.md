# Podela projekta — 14.11.2026.

Ovaj repozitorijum izdvojen je iz lokalnog zajedničkog projekta „Potrosacka korpa”. Izvorni Git nije imao commitove; nema prethodne istorije za prenos. Izvorni folder je sačuvan, a aktivni NUC/VPS servisi nisu menjani.

- Backend poseduje `collector/`, Python `web/` API, SQL migracije, registre izvora i serverske testove.
- Android poseduje Gradle aplikaciju, generator početnog kataloga i Python/Kotlin testove mobilnog klijenta.
- Web poseduje HTML/CSS/JavaScript i HTTP server za statičke fajlove i `/api/*` proxy.

Veza između repozitorijuma je HTTP API; nema Git submodula niti zajedničkog direktorijuma potrebnog za build. Jedini koordinisani generisani asset je skup naselja: backend je njegov izvor, Android verzija se ažurira eksplicitno pri izdanju.

Postojeći API na NUC-u: `http://192.168.50.65:8088`. Postojeći produkcioni URL u konfiguraciji: `https://korpa.intelisystems.dev`; produkciono stanje nije potvrđeno ovom podelom.

Dokumentacija opisuje postojeće ponašanje. Backlog je predlog narednih koraka, a ne odobren proizvodni plan. Licenca nije izabrana; ne pripisivati projektu novu licencu bez odluke vlasnika.
