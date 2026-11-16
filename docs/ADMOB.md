# AdMob u pretrazi kategorija

Android koristi Google Mobile Ads 24.7.0 i UMP 3.2.0. Fiksni banner 300 × 250 (AdSize.MEDIUM_RECTANGLE) nalazi se posle svakih pet proizvoda, uključujući „Prikaži još“, u pojedinačnoj
kategoriji i u globalnoj pretrazi sa ekrana Kategorije. U kategorijama nema oglasa za manje
od pet rezultata. U poređenju cena prikazuje se veliki oglas posle svakih pet
ponuda ili jedan na kraju ako ima od jedne do četiri ponude. To važi i za
rezultate skeniranja; u samoj kameri nema oglasa.

Spisak za kupovinu koristi uzani banner 320 × 50 (AdSize.BANNER) posle svaka
tri proizvoda. Oglasi nisu deo spiska, količina ni procenjenog iznosa. Prazan
spisak ili manje od tri stavke nema oglase. Svaki format se preskače ako nema
dovoljno raspoložive širine. Zatvaranje poređenja oslobađa njegove oglase. Straničenje i brojači
računaju samo proizvode. Oglas je označen tekstom „Oglas“ i odvojen razmacima.
Neučitan/neuspešan oglas ne ostavlja praznu karticu. Novi upit i izlazak sa
ekrana oslobađaju AdView; zakašnjeli odgovori ne popunjavaju stari prikaz.

## Razvoj i produkcija

Debug uvek koristi Google test App ID i banner ID, čak i kada su zadati pravi
ID-jevi. Za release zadati oba Gradle property-ja (npr. u korisničkom
`~/.gradle/gradle.properties` ili preko `-P`):

```
korpaAdMobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
korpaAdMobBannerId=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
```

To su javni identifikatori, ne lozinke. Oglasna jedinica mora biti tipa Banner
za ovu Android aplikaciju. Release bez oba ID-ja ne šalje zahteve za oglase.
Za sada pravi ID-jevi nisu uneti. Promene zahtevaju novu Android verziju;
backend se ne menja. APK nije pripremljen niti distribuiran u ovom koraku.

## Saglasnost i privatnost

Pre inicijalizacije oglasa UMP osvežava status saglasnosti i prikazuje obrazac
kada je potreban. Oglasi se traže samo ako `canRequestAds()` dozvoljava.
Greška bez prethodne važeće saglasnosti ostavlja pretragu bez oglasa.
„Privatnost oglasa“ na ekranu Kategorije otvara UMP privacy options kada su
potrebne. Promena saglasnosti uklanja postojeći oglas; sledeća pretraga ponovo
proverava dozvolu. Nema prosleđivanja teksta pretrage, proizvoda ili lokacije
korisnika kao parametara zahteva za oglas.

Pre javnog izdanja u AdMob konzoli konfigurisati Privacy & messaging i poruke
saglasnosti za ciljna tržišta, kao i politiku privatnosti aplikacije i Play
Console deklaracije o oglasima i podacima prema uključenim Google SDK-ovima.

## Ručna provera u Android Studiju

- Pretraga sa najmanje pet ponuda: označen test banner posle svakih pet proizvoda.
- Brojač i „Prikaži još“ ne uključuju oglas; šest pozicija na 30 proizvoda, još šest na narednih 30.
- Brza promena upita, prelazak na Spisak, povratak i zatvaranje aplikacije.
- Nedostupna mreža/no-fill: pretraga i dodavanje proizvoda rade bez praznog oglasa.
- UMP obrazac i privacy options testirati uz test uređaj/geografiju u razvojnom
  okruženju; ne dodavati prinudnu geografiju niti reset saglasnosti u release.
- Oglas 300 × 250 je centriran; ako lista ima manje od 300 dp širine, slot se uklanja. Proveriti telefon/tablet i pozadinu/povratak u aplikaciju.

## Početni izbor lokacije

Kada se na ekranu Najjeftinija kupovina učita sačuvana lokacija, prikazuje se
jedan uzani oglas 320 × 50 ispod poruke „Sačuvana lokacija prethodne pretrage“,
iznad donjeg dela sa dugmadima za pretragu. Oglas je u skrolujućem sadržaju;
dugmad ostaju fiksirana na dnu. Nema tog oglasa bez sačuvane lokacije, u
rezultatima pretrage ili u dijaloškom biraču. Promena mesta, pokretanje novog
lociranja ili zatvaranje birača uklanjaju oglas.

## Rezultati najjeftinije kupovine

Za dve ili više grupa prodavnica prikazuje se po jedan oglas 300 × 250 između
susednih grupa (N grupa → N−1 oglasnih pozicija). Za tačno jednu grupu prikazuje
se jedan oglas 300 × 250 na dnu rezultata, iza eventualnih poruka o artiklima
bez ponude. Bez grupa, tokom učitavanja i kod greške nema oglasa rezultata.
Nova pretraga i povratak na izbor lokacije čiste stare oglase; pauziranje,
povratak i zatvaranje ekrana prosleđuju se svim oglasima.

## Interstitial pre navigacije

Klik „Navigacija“ u rezultatima najjeftinije kupovine prikazuje unapred učitan
interstitial, pa nakon njegovog zatvaranja otvara mapu. Bez spremnog oglasa,
uz zabranu zahteva kroz UMP ili pri grešci prikaza mapa se otvara odmah.
Klik ne čeka mrežno učitavanje. Više klikova tokom oglasa ne menja odredište.
Najviše jedan prikazan oglas u pet minuta, zajednički za sve prodavnice;
vreme poslednjeg prikaza je sačuvano lokalno i preživljava restart aplikacije.
Učitani oglasi stariji od 55 minuta se ne prikazuju. Zatvaranje Activity-ja
poništava nastavak navigacije, tako da zakašnjeli callback ne otvara mapu.

Debug koristi Google interstitial test jedinicu. Za release, pored postojećih
App ID i Banner ID podešavanja, zadati `korpaAdMobInterstitialId` sa ID-jem
posebne Interstitial jedinice. Bez tog property-ja navigacija ostaje bez oglasa.

Na telefonu proveriti: zatvaranje test oglasa otvara izabranu prodavnicu;
ponovni klik u narednih pet minuta ide odmah na mapu; offline/no-fill ide
odmah na mapu; neuspeh prikaza i uzastopni klikovi ne otvaraju mapu dva puta;
povratak iz Maps i zatvaranje aplikacije ne ostavljaju stari callback.
