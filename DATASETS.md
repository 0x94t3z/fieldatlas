# Field Atlas datasets

## Collections the app offers

Field Atlas 1.4.0 lists these collections in setup and Library (`app/src/main/assets/knowledge/catalog.json`).
Each is an optional `.fapack` download, checked against the size and SHA-256 below before it is installed.
The 1.4.0 release measurements ([sets 4 to 6](docs/evaluation.md#140-release-build-on-fresh-questions-october-13)) ran with exactly these files, except the rerun of set 4 on the 7 October build, which used that build's own collections; earlier sections of the evaluation used the collections of their time.

| Collection (id) | Version | Bytes | SHA-256 | Source and snapshot | Licence | Download |
| --- | --- | ---: | --- | --- | --- | --- |
| Encyclopedia (`wikipedia-encyclopedia`) | 2026.10.12-native-compressed-preview | 10,643,533,492 | `7d41d7fbe0419d703b03d3788a55109217ef57e6fac7754165cc8ccc37654d59` | Simple English Wikipedia CirrusSearch dump, 29 December 2025; English Wikipedia vital articles by section, read 6 October 2026; English Wikipedia CirrusSearch content index, 4 October 2026 | CC BY-SA 4.0 | [Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-encyclopedia/tree/4a31af613133782cb6ba62d2a660a920e5a64cd6) |
| Travel guides (`wikivoyage-guides`) | 2026.09.01 | 419,107,865 | `1de13456098ddbf307665257e88a6f5b98f646f7aa078b18d8a1a2a8e8032c83` | English Wikivoyage pages-articles dump, 1 September 2026, one passage per section | CC BY-SA 4.0 | [Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/5b72377fe68508ae00ea3856bdfdff40a19fb3f4/wikivoyage-guides/2026.09.01) |
| Travel places (`wikivoyage-places`) | 2026.09.1 | 351,581,170 | `1aa075cb2f5dd57415589b3a8e083d701e63f9bb87498f3da97ccbda1c3567ac` | English Wikivoyage pages-articles dump, 1 September 2026 | CC BY-SA 4.0 | [Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/1c547f35c613e4984e5932a3173845b1cef3e76f/wikivoyage-places/2026.09.1) |
| Vegan places (`osm-vegan-places`) | 2026.10.05 | 40,068,046 | `4c08ac74b221890cc0d7559c717fff8762252d9d61b04a9ffdf0d3d8daf660fd` | OpenStreetMap via Overpass, 2 October 2026 | ODbL 1.0 | [Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/86064123736fc5c8ff966b44bfcbe6e07de30441/osm-vegan-places/2026.10.05) |
| Essentials (`osm-essentials`) | 2026.10.05 | 1,781,015,519 | `a8f85f91e9716d05fa08880cea5d5e512723b3a8d32870cc910906fe75eaf6d9` | OpenStreetMap via Overpass, 2 October 2026 | ODbL 1.0 | [Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/e320ff8a3fe4dbcaa68c725c54aaffe93793f35c/osm-essentials/2026.10.05) |
| Cryptography & Ethereum (`crypto-ethereum`) | 2026.10.05 | 77,678,612 | `b34a374145eba8fd19e118a274b045a09361af596b1ffae2aba808481b62dcd7` | EIPs, ERCs and consensus specifications at pinned commits; NIST FIPS 203/204/205, SP 800-208, IR 8547; 123 Wikipedia articles at pinned revisions | CC0 1.0, CC BY-SA 4.0, US government work, MIT (encoder) | [Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/6be16bab707c3ee9f6cc2b7b5694182492a43516/crypto-ethereum/2026.10.05) |
| Biology & longevity (`world-knowledge-biology`) | 2026.10.13 | 8,183,535 | `06e49130ad80d75a83275b7c0c09ac1ba47fb6ea2e3f6c5786b0426caed2b376` | OpenStax Biology 2e at a pinned revision; MedlinePlus health topic summaries, 10 October 2026; Human Ageing Genomic Resources, 13 October 2026; six PubMed Central reviews | CC BY 4.0, CC BY 3.0, US government work | [Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/ba93877fc414e271b6299f09aec26d916a2e8707/world-knowledge-biology/2026.10.13) |

**Where to download.** Every collection is hosted on Hugging Face, in
[Field Atlas Knowledge Packs](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge) and
[Field Atlas Encyclopedia](https://huggingface.co/datasets/0x94t3z/fieldatlas-encyclopedia); the app
downloads from the pinned revisions in the table and checks each file before installing it. The
knowledge releases on GitHub are kept, unchanged, only for Field Atlas 1.3.0 and older, whose
built-in catalogs download from them. Use Hugging Face for anything new.

## Encyclopedia (October 12)

One download replaces the separate Encyclopedia and Encyclopedia in depth collections of earlier
1.4.0 builds, so a phone carries one encyclopedia instead of a growing list of shards. It combines,
without rewriting a passage:

- **Simple English Wikipedia**: lead sections of 211,159 articles from the CirrusSearch dump of
  29 December 2025 (the earlier Encyclopedia collection, below).
- **English Wikipedia vital articles by section**: 49,822 articles read on 6 October 2026, each at a
  pinned revision (the earlier Encyclopedia in depth collection, below).
- **English Wikipedia**: articles from the CirrusSearch content index dump of 4 October 2026, built in
  45 modules and merged.

8,386,572 passages in all; the merge checked that no passage was dropped or duplicated
(`origins.json`, inside the pack, records each input's size and SHA-256). Every passage keeps its
article and revision link. The database carries the app's keyword index and a Leviathan 0.2.0 index
over the same text, so the phone searches it natively. The `.fapack` (`wikipedia-encyclopedia`,
version `2026.10.12-native-compressed-preview`, schema 2) is 10,643,533,492 bytes and installs to
24,346,003,429 bytes; SHA-256 `7d41d7fbe0419d703b03d3788a55109217ef57e6fac7754165cc8ccc37654d59`;
licence CC BY-SA 4.0. It is hosted on Hugging Face at a pinned revision, and that public copy was
streamed and hashed in full before the catalog pointed to it. It is a dated snapshot, not live
information. Installing it needs room for the download and the installed database at once; the
app checks free space before it starts.

## English Wikipedia vital articles by section (October 6)

`tools/packs/build_wikipedia_sections_pack.py` builds the vital-articles part of the Encyclopedia (offered on its own as Encyclopedia in depth in earlier 1.4.0 builds): English Wikipedia's
vital articles (the 49,831 titles of `tools/data/wiki_vital_titles.json`, the Level 5 list), one
passage per section, each linked to the exact revision read. It exists because Simple English
Wikipedia defines a subject but rarely explains its causes or mechanisms.

- **Fetch:** the MediaWiki API's plain-text extracts (`prop=extracts`, with section headings),
  revision id, redirects and the disambiguation flag, one request per title, at most 3 requests a
  second across all workers (Wikimedia allows unauthenticated clients 200 a minute), with
  `maxlag=5`, `Retry-After` honoured by every worker, and a resumable JSON-lines spool. The full
  English dump is 43 GB; this reads only the listed articles.
- **Build:** offline and byte-reproducible from the spool. Each article becomes "<Article> —
  Overview" for its lead (with an "Also known as:" line of up to six useful redirect titles) and
  "<Article> — <Section> > <Subsection>" for the rest. Reference, link and bibliography sections
  are left out; past 16,000 characters an article keeps its explanatory sections before history,
  naming and reception. Formulas are written as their LaTeX source. An article without a lead is
  left out, since the app reaches an article through its lead.
- **Use in the app:** the collection is never searched. When an explanation's subject has been
  found in the Encyclopedia ("Heat pump"), the app reads that article's sections here and adds the
  ones that fit the question ("Operation" for how, "Causes" for why), within a reading budget sized
  to the phone's speed (about 20 s of reading).
- **Licence:** CC BY-SA 4.0. The build writes `ATTRIBUTION.txt` beside the pack: the credit, the
  licence, the changes made, and every article's revision link.
- **Limits:** the extracts leave out tables, images and references, so a section that was mostly
  a table is short or missing. A snapshot of 6 October 2026, not live.

**Build of 6 October 2026.** 49,822 articles, 469,919 section passages, from a spool of
1,333,975,068 bytes (SHA-256 `505c820cd21824d38aac9ab05d6b083e1e269ace739cffdd8bd4e2ea94fe1386`). The `.fapack` (`wikipedia-en-vital`, version
`2026.10.06`) is 1,389,700,139 bytes, SHA-256 `7f824902de3769c26519d050dab4a3f0c0dd90fe62c3eb54316818f0e2655271`. Nine of the 49,831 titles were disambiguation pages or had no lead and were left out. Graded blind side by side with the same build without it, reading these sections raised the fork's 31 lookup questions from 45% to 55% of the reference and our 56 development questions from 57% to 59%, so the collection was offered as recommended in earlier 1.4.0 builds; it is now part of the Encyclopedia. It is released as [knowledge-wikipedia-en-vital-2026.10.06](https://github.com/0x94t3z/fieldatlas/releases/tag/knowledge-wikipedia-en-vital-2026.10.06).

## Emergency guides (bundled)

Bundled in the APK as `app/src/main/assets/emergency/guides.json`, so Research can answer emergency questions with no download, model or network. Built by `tools/packs/build_emergency_data.py` from the pinned list in `tools/data/emergency_sources.json`:

```sh
python3 -m pip install -r tools/packs/requirements.txt   # BeautifulSoup for HTML selection
python3 tools/packs/build_emergency_data.py fetch     # one polite request per source, raw bytes cached with SHA-256
python3 tools/packs/build_emergency_data.py build     # offline; re-checks every cached hash, byte-identical output
python3 -m unittest tools/tests/test_emergency_data.py
```

- **guides.json** (155244 bytes, SHA-256 `a48a286dd5789a28c9ebc3ad0a46fc55da7e0adccb0adf9f6a72b646acbb8ffc`, version 2026.10.05): 42 guides; carbon monoxide poisoning (CDC/NIOSH) joined the 41 of 1.3.0, whose text is unchanged. Text is *selected* from each source (headings, ranges, dropped blocks) and never edited; a test re-extracts every cached page and checks each string is an exact source block. US federal pages (CDC, NIOSH, NIH/NLM MedlinePlus health-topic summaries, NIH/NIAMS, NWS/NOAA, NPS, FEMA Ready.gov, U.S. Fire Administration) are public domain; MedlinePlus A.D.A.M. encyclopedia pages, which are copyrighted, are not used. Four guides are English Wikipedia articles at pinned revisions (CC BY-SA 4.0). Guides that assume US services or US species carry `"region": "US"`.
- **numbers.json** (112428 bytes, SHA-256 `4bbec9d2df4ec619a5732cb3551d8620c1e5dd447899db8147e921475b877894`, bundled as `app/src/main/assets/emergency/numbers.json`): police, ambulance, fire and general numbers for 242 countries and territories from Wikipedia's “List of emergency telephone numbers” (revision 1377218654, CC BY-SA 4.0). Research answers “what emergency number do I call in Japan?” from this table, cited, without the model.
- **Review before relying on it:** see the limits in [docs/emergency.md](docs/emergency.md#limits-you-should-know). The severe-bleeding gap (no bundled tourniquet guidance) and the Wikipedia-sourced choking and drowning guides need review by a clinician.

## Encyclopedia and travel guides from Wikimedia dumps (keyword-only builder)

`tools/packs/build_wikipedia_pack.py` builds keyword-searchable packs from Wikimedia CirrusSearch
"content" dumps, which carry each page's plain text, lead section, redirects, incoming-link
count and revision id. The same tool builds the Simple English encyclopedia and the
Wikivoyage travel guides.

- **Two steps:** `spool` streams the dump without saving it, records its SHA-256 and size,
  and keeps each article's lead, up to eight redirects as aliases and, for articles with at
  least 300 incoming links, up to 6,000 characters of body text. Disambiguation pages and
  leads under 80 characters are skipped. `build` is offline and byte-reproducible from the
  spool: it takes articles by incoming links, most linked first, until a text budget, and
  writes an "Article — Overview" passage (the lead, then aliases in a separate paragraph)
  and an "Article — Details" passage (the body excerpt). Each source links to the exact
  dumped revision (`/w/index.php?oldid=…`).
- **Template errors:** some dump text contains rendered Lua template failures. A failed
  Japanese-name template at the start of a lead is replaced by the article title ("Sapporo is
  the capital city of Hokkaidō"); other affected sentences are dropped. 4,628 Simple English
  passages contained one before this repair.
- **Licence:** CC BY-SA 4.0, with the revision link kept on every passage.
- **Limits:** a snapshot, not live. Simple English articles are shorter than English
  Wikipedia's. The full English Wikipedia dump (about 43 GB compressed) uses the same tool
  but was not built here; the connection used for this work streamed Wikimedia dumps at
  about 330 KB/s.

**Simple English Wikipedia, local build, 2 October 2026.** Dump
`simplewiki-20251229-cirrussearch-content.json.gz` (636,486,826 bytes, SHA-256
`b0fbbcc3d5055c025b51ae86c91fe7deb9e93629794e48ce49c218e49136fe27`): 278,283 pages, 211,159
articles kept, 245,100 passages. The `.fapack` (`simplewiki`, version `2025.12.29`) is
316,396,538 bytes, SHA-256
`add1984a60ff3cfd2298668e7e40b061a8581aeb24023edf3bf5370eef39a82a`. On desktop, with this pack installed alongside
Biology, Vegan places, Travel places and Travel guides, the first keyword pass took 30–300 ms
per question after the packs were open, and "What is photosynthesis?", "Tell me about Japan's
history" and "Explain climate change" each led with the named article's overview.

**Wikivoyage travel guides, local build, 2 October 2026.** Dump
`enwikivoyage-20251229-cirrussearch-content.json.gz` (234,324,021 bytes, SHA-256
`6c854abe7226ab1efb748ac672db91fb26f45d94b17e1e53f6a7e932fc6fb10a`): 33,845 pages, 25,330
guides kept, 50,495 passages, built with `--copyright-page Wikivoyage:Copyleft`. The
`.fapack` (`wikivoyage-guides`, version `2025.12.29`) is 374,600,710 bytes, SHA-256
`2684b5b664aedb15aafb905a2dffccdd059dc589d10a382fdddec17c8bef60b2`. It complements the
existing Travel places pack (one listing per venue) with each destination's prose guide.

**Wikivoyage travel guides by section, local build, 5 October 2026.** The CirrusSearch build
above flattens each guide and keeps only its opening, so a question about cash in Japan or
crossing the road in Hanoi had nothing to find. `tools/packs/build_wikivoyage_guides_pack.py` reads the
same pinned pages-articles dump as the places packs
(`enwikivoyage-20260901-pages-articles.xml.bz2`, SHA-256
`c6cebf6b109c31698e736858fd1d8dec1c41d87437aa4c4cafd7b0df88777773`) and writes one passage per
section: "Japan — Buy > Cash", "Hanoi — Stay safe > Traffic". Links keep their label; warning
and caution boxes keep their text and headline; prices ({{EUR|25}} as "EUR 25"), phone numbers,
conversions and station names are written out; map data, image captions and comments are
dropped. Transport and practical sections keep their listings as one line each; See, Do, Eat,
Drink, Sleep and Buy listings stay in the places pack. 31,672 guides, 291,092 section passages.
The `.fapack` (`wikivoyage-guides`, version `2026.09.01`) is 419,107,865 bytes, SHA-256
`1de13456098ddbf307665257e88a6f5b98f646f7aa078b18d8a1a2a8e8032c83`.
It replaces the 2025.12.29 guides pack under the same id. It is hosted on
[Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/main/wikivoyage-guides/2026.09.01); the
earlier GitHub release `knowledge-wikivoyage-guides-2026.09.01`, with its `SOURCE.json` and `SHA256SUMS`, is kept for older app versions. Field Atlas 1.4.0 offers
this version; installing it switches the 2025.12.29 pack off.

Both CirrusSearch packs were rebuilt a second time from their spools and were byte-identical.
They were published on 2 October as releases `knowledge-simplewiki-2025.12.29` and
`knowledge-wikivoyage-guides-2025.12.29`, each carrying the `.fapack`, `SOURCE.json`, the
spool and its `.meta.json` (Wikimedia keeps CirrusSearch dumps for only a few weeks), and
`SHA256SUMS`. The flat guides release was superseded by the guides-by-section collection
(`knowledge-wikivoyage-guides-2026.09.01`); it stays on GitHub because Field Atlas 1.3.0 downloads from it.

## Broader travel places (keyword-only builder)

`tools/packs/build_wikivoyage_places_pack.py` uses the same pinned English Wikivoyage dump and extracts six listing types: See, Do, Eat, Drink, Sleep, and Buy. Each place keeps its destination, category, address or directions, description, available hours and price, coordinates when supplied, listing check date when supplied, page revision date, and source permalink. The resulting `.fapack` uses FTS5 keyword search—**no embedding or vector build is required**. The app has a fast, source-only path for simple restaurant, café, museum, hotel, bar, and shop lookups; complex questions still use the normal research path.

The [Travel places download](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/main/wikivoyage-places/2026.09.1) contains 401,826 listings and 405,849 searchable chunks in a 351,581,170-byte `.fapack` (SHA-256 `1aa075cb2f5dd57415589b3a8e083d701e63f9bb87498f3da97ccbda1c3567ac`). Setup and Library offer it as Travel places. Pack verification passed, and earlier offline Infinix checks returned cited café listings for Chiang Mai and museum listings for Berlin. These checks do not establish broad answer quality. The [build command](tools/README.md#typical-flows) and source dump hash above make it reproducible. Listings cannot establish current opening, quality, or a present-day “best” ranking. The collection includes restaurant listings, so the overlapping Eat-only pack is unnecessary for most users.

## Vegan places from OpenStreetMap (keyword-only builder)

`tools/packs/build_osm_vegan_pack.py` builds one searchable listing per OpenStreetMap place tagged
`diet:vegan=only` (fully vegan), `diet:vegan=yes` (vegan options), `diet:vegan=limited`, or
`cuisine=vegan`. It exists because Wikivoyage covers few vegan venues (four Berlin listings,
one in Tokyo), while OpenStreetMap records about 2,000 vegan-tagged places in the Berlin area alone.

- **Source and licence:** OpenStreetMap data via the Overpass API, © OpenStreetMap
  contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright). Every listing and the
  app's answer carry this attribution. The pack is a derived database under the same licence.
- **What a listing contains:** destination, category (Eat, Drink, Buy), name (English name
  when tagged, with the local name), vegan label, cuisine, address, hours as tagged,
  coordinates, the mapper's `check_date` when present, the data snapshot date, and a link to
  the OpenStreetMap element.
- **Destination:** the tagged address city when it is itself a city of at least 200,000
  people within 30 km (from the 5 October builds); else the most populous city of at least
  100,000 people within 30 km, else the nearest city or town within 20 km, else the tagged
  address city. Settlements come from OpenStreetMap `place=city`/`place=town` nodes fetched in
  the same run. Places with no destination are kept for keyword search but never answer
  "in <city>" questions.
- **Reproducibility:** `fetch` runs six exact-tag Overpass queries and stores every raw
  response with its SHA-256, query and Overpass `timestamp_osm_base` in `lock.json`. `build`
  is offline and byte-reproducible from that cache, and copies the lock into the pack output as
  `OVERPASS-LOCK.json`. Each response also records the Overpass endpoint it came from. Publishing
  the cache with the pack lets anyone rebuild it exactly;
  re-running `fetch` later gives newer data. `--date` requests date-pinned (attic) queries, but
  on the public server a worldwide attic query timed out after 900 s, against 81 s for current
  data.
- **Near me:** the pack adds a `place_points` table (latitude/longitude per search row). For
  "near me", "nearby" or "the city I'm in" questions, the app reads the phone's location from
  Android's location service, asking its fused, GPS and network providers at once and taking the
  first fix (GPS works offline and without Google Play Services), and keeps it on the phone. It lists the
  nearest matching places with straight-line distances and compass directions, widening from
  2 km to 15 km when little is close. A request for the *best* places ("the best vegan
  restaurants in the city I'm in") ranks the city the phone is in instead, named in the answer:
  fully vegan places first, then places the city's Wikivoyage guide also recommends, sit-down
  restaurants before cafés and fast food, and independent places before chains. When no fresh fix
  comes within 45 seconds, a last known position up to two hours old is used and the answer says how
  old it is. Location permission is requested only when such a
  question is asked; without it, the app says how to enable location or name a city instead of
  guessing.
- **`cuisine=vegan`:** queried for completeness but returned no elements; OpenStreetMap tags
  vegan venues with `diet:vegan=only`.
- **Limits:** tags record what mappers entered and when; they cannot establish current opening,
  menus, prices, or quality. There are no ratings, so a "best" answer says it is ordered by what
  the map and the travel guide record, and each listing gives its check date or says none was
  recorded.

**Local build, 2 October 2026.** 52,064 places: 3,715 fully vegan, 47,056 with vegan
options, 1,293 limited; 48,270 Eat, 1,369 Drink, 2,425 Buy. 350 places have no destination.
Examples: Berlin 2,058, London 1,486, Paris 762, New York 690, Tokyo 183, Bangkok 123,
Singapore 102, Chiang Mai 85. The `.fapack` is 40,088,517 bytes, SHA-256
`6cc944185c18de577060833645ee91a4da511c2c32df77ffc3b4dbfaf2dd8f7c`, version `2026.10.02`;
two builds from the same cache were byte-identical. The cache is 88 MB of raw Overpass JSON.
Data timestamps run from `2026-10-02T00:35:39Z` (vegan queries, `overpass-api.de`) to
`2026-10-02T03:39:03Z` (`place=town`, fetched from the `maps.mail.ru` Overpass mirror after
the main server refused further connections). It was published as release `knowledge-vegan-places-2026.10.02` (superseded by `knowledge-vegan-places-2026.10.05`, which carries the same cache; kept on GitHub because Field Atlas 1.3.0 downloads from it), carrying the `.fapack`, `OVERPASS-LOCK.json`, the raw-response cache (`osm-vegan-overpass-cache-2026.10.02.tar.gz`, 18.5 MB) and `SHA256SUMS`, and the in-app download catalog lists it.

**Rebuild, 5 October 2026.** Same Overpass cache, with the address-city rule above: a
restaurant whose address says Malmö was filed under Copenhagen, 28 km away in another country.
952 places changed destination, among them 107 from Cologne to Bonn, 80 from Wiesbaden to
Mainz, 54 from Amsterdam to Almere, 38 from Copenhagen to Malmö and 22 from Tijuana to San
Diego; the count of places, and of places without a destination, is unchanged. The `.fapack`
is 40,068,046 bytes, SHA-256
`4c08ac74b221890cc0d7559c717fff8762252d9d61b04a9ffdf0d3d8daf660fd`, version `2026.10.05`.
It is hosted on Hugging Face; the GitHub release `knowledge-vegan-places-2026.10.05`, with its `OVERPASS-LOCK.json` and `SHA256SUMS`, is kept for older app versions, and the raw cache is the one published with `2026.10.02`. Field Atlas 1.4.0 offers this version.

An earlier build of the same day fetched settlements with `out tags;`, which omits node
coordinates, so every place fell back to its address city and Tokyo had no listings. The
settlement queries now use `out;`, a changed query invalidates its cached response, and the
build stops if settlements arrive without coordinates.

On the Infinix X6840 (offline), "Tell me the best vegan restaurants in Berlin" and "…in
Tokyo" each returned six cited, fully vegan OpenStreetMap places in 15–17 s without running
the model. "Best vegan restaurants near me" obtained a location fix and reported that no
mapped place lies within 15 km of the test location.

## Essentials from OpenStreetMap (keyword-only builder)

`tools/packs/build_osm_essentials_pack.py` builds one listing per OpenStreetMap place a traveller
without a connection most often needs: pharmacies, hospitals and clinics; police, embassies and
consulates; ATMs and currency exchange; train, bus and ferry stations; toilets and drinking
water; supermarkets and hostels. It reuses the vegan-places pipeline above: the same exact-tag
Overpass queries archived with SHA-256 and data timestamps, the same tiling and retries, and the
same destination rule, with settlements read from the vegan-places cache (`--settlements-cache`).

- **What a listing contains:** destination, category (Health, Safety, Money, Transport,
  Toilets, Water, Buy, Sleep), type, a name (or, when untagged, the brand, operator, network or
  represented country), address, hours, and details such as emergency department, fee,
  wheelchair access and phone where mapped, with coordinates and a link to the element.
- **Near me and nearest-in-city:** `place_points` stores coordinates and the category per row,
  so "nearest pharmacy" filters by category from the index instead of reading every listing in
  a dense city. App 1.2.0 recognises pharmacies, hospitals, clinics, ATMs, exchange, toilets,
  drinking water, police, embassies, stations and supermarkets in questions. Hostels are in the
  pack but reach answers only through ordinary keyword search in 1.2.0.
- **Duplicates:** an element matching two kinds is kept once, and a place mapped twice (point
  and outline with the same name and centre) is listed once.
- **Limits:** tags can be stale. A listing cannot confirm that a pharmacy is open, a toilet is
  free, or an ATM works today.

**Local build, 3 October 2026.** 2,981,021 places from 15 Overpass kinds: 522,588 toilets,
495,089 supermarkets, 442,099 pharmacies, 368,735 drinking-water points, 243,661 ATMs,
220,752 clinics, 217,476 hospitals, 162,887 police, 101,968 train stations, 67,788 bus
stations, 62,681 hostels, 37,409 ferry terminals, 21,115 currency exchanges, 11,361 embassies
and 5,412 consulates. 127,642 have no destination. Examples: Tokyo 22,974, Paris 13,532,
London 9,423, New York 8,144, Berlin 6,644, Jakarta 5,491, Singapore 4,767, Bangkok 4,375.
The `.fapack` is 1,781,105,631 bytes, SHA-256
`e04fb8b89f627ef1cd8d0fb37f961d5d6b51acc7bc64d933d2b932db76bd6d62`, version `2026.10.02`;
two builds from the same cache were byte-identical (each took 13–17 minutes and peaked at
about 2 GB of memory on an Apple Silicon Mac). All 31 Overpass responses came from the
`maps.mail.ru` mirror, with data timestamps from `2026-10-02T08:04:36Z` to
`2026-10-02T16:29:06Z`; the raw cache is 1.0 GB, or 180 MB compressed. It was published on 3 October as release
`knowledge-essentials-2026.10.02`, carrying the `.fapack`, `OVERPASS-LOCK.json`, the cache
(`osm-essentials-overpass-cache-2026.10.02.tar.gz`) and `SHA256SUMS`; superseded by
`knowledge-essentials-2026.10.05`, which carries the same cache; kept on GitHub because Field
Atlas 1.3.0 downloads from it.

**Rebuild, 5 October 2026.** Same cache, with the vegan-places address-city rule. 12,823
places changed destination, among them 306 from Quezon City to Manila, 247 from El Alto to La
Paz, 216 from Cologne to Bonn and 170 from Kuala Lumpur to Petaling Jaya. Two more places are
listed (2,981,023): a place mapped twice is merged when both copies have the same title and
centre, and in two such pairs only one copy's address names a city of its own (Ernakulam,
beside Kochi; Novo Hamburgo), so the copies now carry different destinations. The `.fapack` is 1,781,015,519 bytes,
SHA-256 `a8f85f91e9716d05fa08880cea5d5e512723b3a8d32870cc910906fe75eaf6d9`, version
`2026.10.05`. It is hosted on Hugging Face; the GitHub release `knowledge-essentials-2026.10.05`, with its
`OVERPASS-LOCK.json` and `SHA256SUMS` (the raw cache is the `2026.10.02` one), is kept for older app versions, and Field Atlas 1.4.0
offers this version.

The first full build failed: the coverage summary listed every type label, which exceeded the
format's 160-character limit once all kinds were fetched. The summary now uses short names, and
a test checks the limit with every kind present.

## Cryptography & Ethereum (vector builder, October 5)

`tools/packs/build_crypto_pack.py` builds a focused collection from primary sources, each pinned when
fetched and checked against the lock on every build:

- Ethereum Improvement Proposals and ERCs at `ethereum/EIPs`
  `bcdb27e330c3ba01a20c3fd1e2e592e0d04d2d47` and `ethereum/ERCs`
  `365b4c02879f3e882b91281d42b4f57b406205e9`, one passage per section, cited to the file at that
  commit. Proposals marked "Moved" are skipped in favour of the ERC repository's copy. Each
  proposal's own Copyright section is checked: two that are CC BY-NC-SA (EIP-1355, EIP-1485) are
  left out, and the rest are CC0 by their own notice or the repository's `LICENSE.md`.
- The consensus specifications at `ethereum/consensus-specs`
  `889a389f9f95d2aba52aedf233217f370772dd3d` (CC0).
- NIST FIPS 203, 204 and 205, SP 800-208 and IR 8547 (initial public draft), as published PDFs
  with recorded SHA-256; works of the US federal government, public domain in the United States.
- 123 English Wikipedia articles from `tools/data/crypto_reference_topics.json` at pinned revisions
  (CC BY-SA 4.0), through the general reference builder.

18,204 documents and 22,805 passages (7,088 EIP, 8,596 ERC, 976 specification, 92 NIST and
1,452 Wikipedia documents). `tools/packs/embed_pack.py` encodes every passage with the same BAAI
bge-small-en-v1.5 encoder the phone uses for questions (MIT), and `tools/packs/build_vector_pack.py`
adds the vectors. The `.fapack` (`crypto-ethereum`, version `2026.10.05`) is 77,678,612 bytes,
SHA-256 `b34a374145eba8fd19e118a274b045a09361af596b1ffae2aba808481b62dcd7`; its licence field is
`CC0-1.0 AND CC-BY-SA-4.0 AND LicenseRef-US-Government-Work AND MIT`. The build writes
`ATTRIBUTION.txt` (the Wikipedia revision list and change notice) and `sources.lock.json` beside
the pack. The pack and both files are hosted on
[Hugging Face](https://huggingface.co/datasets/0x94t3z/fieldatlas-knowledge/tree/main/crypto-ethereum/2026.10.05); the GitHub release
`knowledge-crypto-ethereum-2026.10.05` is kept for older app versions. Field Atlas 1.4.0 offers it as Cryptography & Ethereum.

## Biology & longevity (October 13)

A focused biology and health collection, replacing the fork's 1.2.0 pack in the download list.
It holds 9,260 passages from 6,738 documents and uses the same keyword and native (Leviathan)
search as the Encyclopedia; it has no vectors.

- **OpenStax Biology 2e** (CC BY 4.0), read at a pinned revision. Each textbook section is kept
  with its own headings; figures, captions, exercises, equations and inline footnotes are left out.
  The text is not paraphrased.
- **MedlinePlus health topic summaries** (US government work, public domain in the United States),
  from the 10 October 2026 bulk file. Summaries only, not linked articles or images.
- **Human Ageing Genomic Resources** (CC BY 3.0): GenAge human and model-organism genes,
  LongevityMap and CellAge, retrieved 13 October 2026. Each record keeps its original fields as
  labelled lines. A gene listed there is not evidence that changing it extends human life; that
  caution is stored as metadata, not presented as source text.
- **Six open-access ageing reviews from PubMed Central**, each CC BY 4.0 (PMC11647017,
  PMC10702235, PMC10090449, PMC10824251, PMC11830604, PMC12322153). PubMed showed no linked
  correction or retraction when they were selected. Figures and tables are excluded, and in-text
  pointers to them are removed while the citations beside them are kept.

The `.fapack` (`world-knowledge-biology`, version `2026.10.13`, schema 2) is 8,183,535 bytes and
installs to 27,291,455 bytes; SHA-256 `06e49130ad80d75a83275b7c0c09ac1ba47fb6ea2e3f6c5786b0426caed2b376`;
licence field `CC-BY-4.0 AND CC-BY-3.0 AND LicenseRef-US-Government-Work`. `ATTRIBUTION.txt`, inside
the pack and beside it on Hugging Face, carries the OpenStax contributor credits and licence, the
MedlinePlus and HAGR terms and each article's permissions. Collection details in Library show it
offline. Nothing in this collection is clinical guidance.

On 30 held-out biology questions, run through the app's answer pipeline with the 2B model and
graded blind by two reviewers, this collection had about twice as many correct answers as the
1.2.0 pack it replaces, with no answers flagged as harmful. One answer overstated what caloric
restriction is shown to do in humans, and questions about whether animal results prove an effect
in people remained unanswered; see [docs/evaluation.md](docs/evaluation.md) for how answers are measured.

Phones that already have 1.2.0 keep it; Library offers 2026.10.13 as the newer edition and never
replaces an installed collection by itself. The 1.2.0 pack was built by the
[vbuterin/fieldatlas fork](https://github.com/vbuterin/fieldatlas/tree/vector-search/releases/packs)
from PubMed abstracts, the Fight Aging! archive and longevity databases; Field Atlas did not
re-check the licences of those sources.

## Adding future collections

The development app accepts independently produced knowledge packs using the supported fapack manifest and SQLite schema. A topic need not have a hardcoded app entry. The canonical download catalog is `app/src/main/assets/knowledge/catalog.json`; publishing compatible entries to that file on the project repository's main branch lets users discover them through explicit **Refresh collections**. Refresh is optional provisioning, not background research networking. Installed packs work independently of catalog availability.

Keyword search is the compatibility fallback. The current vector reader supports normalized `int8-symmetric-per-vector` blobs in `chunk_vectors`. Packs using another layout or an encoder incompatible with the active encoder remain keyword-only. Encoder identity is checked again when encoding to avoid mixing vector spaces if enabled packs change mid-query. This is not support for arbitrary model/embedding formats. Pack content does not override citation or answer policies.

## Development builds and experiments

These builds were used to develop and test retrieval. None is offered in the app; they are kept so the
earlier measurements can be reproduced.

### Everyday reference preview (October 1)

#### Local semantic-search experiment — 0.3.0

The local 0.3.0 preview adds a search index to the existing 0.2.0 content;
all 2,633 source passages, titles and revision URLs were compared and are unchanged.
It uses the same hash-verified BGE-small-en-v1.5 query encoder as the installed
biology pack. This is a development artifact, not a published catalog update.

- Archive: `build/everyday-reference/final-preview/pack/pack-0.3.0.fapack`
- Size: 42,108,940 bytes; SHA-256:
  `c0142831da0017d6755b6f52c1648212c3c66b2925150abc1204e53d4a598a4a`.
- Encoder SHA-256:
  `f046db1dc724cf4f6f0a0c5917e922823b73eb1d27b8f9a9c2797f7866974804`.
- Encoding: CLS pooling, L2 normalization, 384 dimensions, int8 storage;
  query instruction applied only to queries. Three passage embedding inputs
  exceeded 512 tokens and were capped, keeping their special tokens. The actual
  source text was not shortened. Corpus text retains its original attribution;
  the [BGE encoder](https://huggingface.co/BAAI/bge-small-en-v1.5) is MIT-licensed.

The frozen probe, vectors, original database identity and query vectors are retained
under `build/reasoning-diagnosis/`. `tools/packs/build_vector_pack.py` packaged the index
and ran its quantization self-match guard. A separate run used the actual Kotlin
vector search, fusion, relevance filtering and prompt packing. The buoyancy
passage became the leading result, but generated explanations still contained
errors. Retrieval similarity is not a factual-support score or a quality pass.

#### Original keyword-only preview — 0.2.0

A separate, independently selected 60-article collection now covers physical
science, Earth/space, biology, evidence/statistics, computing and history/society.
It is fetched from original Wikimedia revisions, not another submission's corpus.
The 26 earlier pinned source records are retained unchanged. This preview is not
a complete encyclopedia or a claim that generated answers meet the bounty bar.

```sh
python3 tools/packs/build_general_reference.py fetch \
  --topics tools/data/everyday_reference_topics.json \
  --cache build/everyday-reference/practical-snapshot
python3 tools/packs/build_general_reference.py build \
  --topics tools/data/everyday_reference_topics.json \
  --cache build/everyday-reference/practical-snapshot \
  --output build/everyday-reference/final-preview \
  --version 0.2.0 --preserve-paragraphs \
  --pack-id everyday-reference --title 'Everyday reference · Preview'
```

Use an existing hash-verified snapshot to reproduce the exact tested dataset;
a fresh fetch selects newer revisions. Build refuses to overwrite an existing
output. The local artifact is
`build/everyday-reference/final-preview/pack/everyday-reference-0.2.0.fapack`:
4,337,718 bytes, 1,484 sections and 2,633 chunks. Complete source paragraphs are
preserved; tables, images and math-containing paragraphs are still omitted.
There are no embeddings. Attribution, source revisions, original topic selection
and extraction counts accompany the build. The manifest carries license and
revision URLs for the imported pack.

`tools/data/everyday_reference_checks.json` contains eight frozen development questions.
Paired baseline/preview tests retain original retrieved passages and generated
answers; source coverage and answer correctness are reviewed separately. See the
[architecture review](docs/evidence/candidate-architecture-review-2026-10-01.md).
This pack is not automatically recommended or published in the download catalog.

### General Reference pilot (development only)

`tools/packs/build_general_reference.py` builds a separate, independently selected
24-article English Wikipedia pilot across six subject areas. It does not use
another submission's corpus. This is a bounded pipeline experiment, **not** a
general encyclopedia, published collection, or answer-quality certification.

```sh
python3 -m pip install -r tools/packs/requirements.txt
python3 tools/packs/build_general_reference.py fetch
python3 tools/packs/build_general_reference.py build
python3 -m unittest discover -s tools/tests -p 'test_general_reference.py'
```

Fetch uses Wikimedia's API sequentially with an identifying User-Agent, pins
each article revision and timestamp, and records response SHA-256 values in
`build/general-reference/snapshot/sources.lock.json`. Interrupted fetches reuse
hash-checked completed articles. A new topic selection requires a new cache.
Keep the entire snapshot cache: it is the reproducible input, not just the topic
list. A fresh fetch without that cache selects newer revisions and is a different
dataset. Rendered templates can also change independently of an article revision.

Build is offline, rejects incomplete/corrupt input, and refuses to overwrite an
existing output directory. It produces the app-compatible `.fapack` under
`build/general-reference/pilot/pack/`, a document spool, attribution file, source
lock, and extraction report. Text remains CC BY-SA 4.0, credited to Wikipedia
contributors through the original revision URLs; the report records transformation
and extraction limits. See [Wikimedia reuse terms](https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use#7._Licensing_of_Content).

Article sections become separate documents with the article and heading path in
their titles. Navigation, reference lists, media, tables and math-containing
paragraphs are omitted. Nested lists are flattened; long paragraphs still use the
existing bounded pack chunker. Consequently this is **not** full mathematical,
table or procedure preservation. The corpus has no embeddings. Packaging tests
do not validate Android retrieval or generated answers.

`tools/data/general_reference_checks.json` freezes four development questions and
manual review criteria before the pilot's first inference comparison. Compare
model-only against the same model with the pilot, using the existing desktop
runner; do not substitute these known cases for the unchanged bounty benchmark
or call completion an accuracy pass. Phone testing and source/license review are
required before catalog publication. The website and download catalog intentionally
do not advertise this pilot.

Initial development comparison (30 September 2026): the 24-article snapshot built
690 section documents and 1,484 searchable chunks, approximately 2.46 MB packaged.
Four identical prompts were run with and without the pack through the actual Kotlin
desktop pipeline and the saved Qwen3.5 2B model. Reports are under
`build/general-reference/evaluation/{with-pack,model-only}/report.json`.
**The pilot did not pass qualitative review.** In the agriculture answer, irrelevant
writing-history evidence contaminated the explanation. The first three pack-backed
responses had no accepted citations. The current-information question refused to
invent an earthquake but needlessly cited unrelated passages. Model-only answers
also contained errors; neither configuration is an accuracy baseline.

The next gate is retrieval precision and evidence use, not more downloads. The
lexical filter can accept incidental overlap among generic instruction words;
its short-definition and two-single-word-comparison guards do not cover these
longer questions. Prompt instructions alone did not prevent subject contamination.
This is an English, keyword-only desktop diagnosis, not a measured phone result.

#### Targeted retrieval follow-up

The next run, retained at
`build/general-reference/evaluation/retrieval-fix/report.json`, uses the same
snapshot, model and four questions. Search no longer treats `which`, `only`,
`any`, `each`, `every` or `using` as content terms. For sectioned reference
articles, explicitly named parent subjects constrain which other passages can
join the evidence; multiple named subjects and cross-article mentions remain
eligible. This is a lexical precision guard, not semantic verification. It does
not solve indirect references, ambiguous names, synonyms or passage-level support.

Observed: the writing-history claim disappeared from the agriculture answer,
and the current-events question returned an evidence-gap message without unrelated
citations. The agriculture response still digressed and the networking answer
still needs factual review. The first three answers remained uncited model
explanations. The pilot is still not approved for publication; a targeted retrieval
regression fix is not a general research-quality pass.

Verification for this follow-up: the ordinary JVM suite and 11 connected Android
retrieval tests passed, including a real SQLite subject-contamination fixture.
The optimized release build passed. The existing Biology/Travel desktop regression
retained Berlin's four cited listings and the fictional-city evidence gap; the
biology and seasons model explanations still contain limitations and must not be
reported as quality passes. These checks do not constitute on-phone generated-answer
validation of the new pilot, which remains uninstalled and unpublished.

#### Expanded coverage candidate (October 1)

`tools/data/general_reference_expanded_topics.json` adds Spreadsheet and P-value to the
24-article development pilot. `fetch --seed-cache` copies and validates the original
pinned snapshots into a new cache, then fetches only additions. The original topic
list and cache are unchanged. Builds remain offline and include revision attribution.

The local 0.3.0 candidate has 26 articles and 741 sections; its pack SHA-256 is
`dd4acf112ed6d99d776b9b1f26047cea9ce9e760a54a61cae3806c2d323779ba`.
It is not published or added to the download catalog. All 24 original source records
are identical. Added revisions: P-value 1364624459 and Spreadsheet 1376895772.

Eleven known development questions were rerun with the unchanged 2B model. Added
coverage brings p-value limitations into the selected passages, but coverage alone
does not fix generated answers. For explicit two-subject comparisons, title nomination
now prefers each exact subject's Overview over shorter History/Types sections. This
restores both Database and Spreadsheet introductions; the other ten answers stayed
byte-identical in that selection-only comparison. The database answer still treats
relational properties as universal, partly reflecting overbroad source wording.
The algorithm answer still contradicts its source. Neither is a quality pass.

Local reports: `build/general-reference/evaluation/expanded-coverage/report.json`
and `build/general-reference/evaluation/comparison-coverage/report.json`.

#### Difference-question relevance follow-up

The October 1 local follow-up extends the existing single-word comparison gate
to questions such as "How does a battery differ from a capacitor?" and "How is a
battery different from a capacitor?" It also recognizes definitions preceded by
a short `In …,` qualifier without removing that qualifier from the actual source.
Ambiguous multiword comparisons and study-specific trailing clauses retain the
ordinary relevance path. This is an English lexical heuristic, not semantic
verification, and it does not alter model selection or answer instructions.

With the same 2B model and paragraph-pack database, the database/spreadsheet
question now selects **Database — Overview** instead of a document-oriented
database definition and a desktop-software history passage. The other six known
development cases retain byte-identical requests and visible answers. Results:
`build/general-reference/evaluation/qualified-difference-subjects/report.json`.
The earlier syntax-only attempt is preserved at `difference-subjects/report.json`;
it excluded the qualified definition and fell back to model knowledge, so it is
not the retained implementation by itself.

This fixes evidence selection for this question, **not its overall answer**.
The resulting model explanation still overgeneralizes database support for
unstructured content and spreadsheet updates. The pilot has no dedicated
spreadsheet article, so comparison coverage is incomplete. All generated
comparison prose remains labelled unverified. Do not publish the candidate pack
or claim a quality pass from this follow-up. Biology/Travel regression results are
at `build/desktop-evaluation/difference-subjects-regression/report.json`; Berlin's
dated listings and the fictional-fact evidence gap remain intact, while the
known biology explanation problems remain unresolved.

Verification: the full JVM suite reported 371 tests, zero failures/errors and two
skips; `:app:assembleRelease` completed successfully offline. Phone testing and
installation remain deferred; this is not a published release.

#### Rejected ranking and answer-composition experiments

Further local trials on 30 September 2026 tested section-title ranking, section
diversity, shorter answer instructions, a four-passage reference limit, and an
explicit question-coverage instruction. None justified replacing the preceding
retrieval fix. Depending on the variant, answers incorrectly denied heat transfer
through a vacuum, omitted convection, reversed the network-outage scenario, or
added unsupported historical details. All of these experimental production changes
were reverted; the targeted retrieval follow-up above remains intact.

Local reports are under `build/general-reference/evaluation/` in
`section-ranking`, `section-ranking-refined`, `concise-composition`,
`bounded-reference-retry`, and `coverage-candidate`. The earlier
`bounded-reference` run had a Gradle result-writing collision with a concurrent
test run and is not a successful execution; its retry ran sequentially. These
are known development questions, not an independent benchmark score.

After restoring the prior implementation, three fresh questions about the water
cycle, databases versus spreadsheets, and genes versus environment completed in
`fresh-checks-stable/report.json`. All remained labelled unverified model
explanations. Review still found overly absolute statements about evaporation and
when databases are required. Completion does not establish accuracy or citation
support. The ordinary JVM suite passed after restoration.

Phone validation and installation were deferred at the user's request; these
local experiments do not establish Android answer quality.

#### Controlled manual-evidence comparison

Two of the same development questions (heat transfer and networking) were run
with manually selected, unmodified pilot passages and again with automatic
retrieval. The reports are `build/general-reference/evaluation/manual-evidence/report.json`
and `build/general-reference/evaluation/automatic-evidence-control/report.json`;
the selection is `build/general-reference/evaluation/manual-selection.json`.
Model/database hashes, app-source hash and server command match between runs.
The relevance gate and prompt packing were not bypassed. All five selected heat
passages and all three selected network passages reached the recorded prompts
as excerpts. No production app code changed for this comparison.

The manual heat answer incorrectly said all three mechanisms can transfer heat
through a vacuum, despite receiving the explicit radiation/vacuum passage and
the conduction/convection material requirements. The automatic answer correctly
distinguished vacuum transfer but described photons as opposed to particles.
This is evidence of a downstream generation problem, not only missing retrieval.
It does not isolate model capability from prompt design or prove a universal
failure rate from two questions and one fixed seed.

For networking, manual selection removed distracting overlay/protocol passages
and produced a clearer local/global distinction, but the answer overgeneralized
offline video conferencing and added a confusing final connectivity qualification.
Automatic retrieval included peripheral overlay/VPN/ATM passages; its answer
introduced an unnecessary overlay explanation and framed local networking as a
fallback. Neither answer is a verified source-backed quality pass. All four
responses remained labelled unverified model explanations, consistent with the
current mixed-answer policy, which permits background knowledge and makes exact
source quotations optional. Lack of citations alone therefore does not identify
a retrieval failure.

The following investigation compares answer policies using fixed evidence,
before expanding the corpus or changing the default model. The
[diagnostic instructions](docs/evaluation.md#manual-evidence-diagnostic) explain
how to reproduce this test without changing the Android app.

#### Fixed-evidence answer-policy comparison

The same two questions and selected passages were tested with the existing strict
source-only instructions and one test-only partial-answer candidate. Reports are
`build/general-reference/evaluation/source-only-policy/report.json` and
`build/general-reference/evaluation/source-partial-policy/report.json`. Against
the preceding `manual-evidence` control, model/database identities, production
source hash, packed evidence/question suffix, system message, and other inference
request settings match. Only the user-message instruction prefix changed.

The existing source-only policy corrected the heat/vacuum distinction, but refused
the entire networking question despite the supplied network and internet
definitions. The partial-answer candidate asked for supported parts plus specific
gaps. It answered both questions and preserved the vacuum distinction, but its
network answer then contradicted its own explanation with an overly broad gap
statement. Heat examples introduced details absent from their cited excerpts.
Both policies also retained an overly narrow solids-only conduction description
from the selected context. A source-number marker is not evidence of entailment.

These observations are from two known development questions and one fixed seed,
not a pass rate. The partial-answer candidate was designed after inspecting the
strict run and therefore is not held-out validation. Neither policy is approved
as a production replacement. More data alone is not demonstrated to solve these
generation, evidence-use, and source-context limitations.

The override changes inference instructions only. Existing mixed-answer display
processing strips paraphrase citations and retains its unverified-model label;
the raw answers and displayed answers were reviewed separately. Shipping a future
policy requires consistent attribution behavior as well as independently reviewed
claim support and fresh-question checks. Test tooling checks and the ordinary JVM
suite pass; no app-default, corpus, model, or phone-installation change was made.

#### Paragraph-context implementation candidate

The local prompt builder now prefers an intact paragraph of at most 1,200
characters over its previous three-sentence window for passages longer than 700
characters. It chooses by query-term coverage and retains adjoining English
qualification/anaphora paragraphs (for example, "However" or "These"). A connected
unit that cannot fit is omitted rather than clipped. Longer unstructured
paragraphs retain the prior sentence-window fallback. Original source text and
citation mapping remain unchanged; this cannot recover text already cut by the
pack chunker and is not a semantic-support check.

Four new unit tests cover later definitions, cross-paragraph qualifications,
dependent paragraphs, and omission of oversized connected units. The eight
claim-support cases in `build/claim-support/paragraph-context/report.json` produced
raw and visible answers identical to `app-baseline`; their short passages are
unaffected. The four real-source development questions were also rerun in
`build/general-reference/evaluation/paragraph-context/report.json`.

The heat answer retained all three mechanisms and the correct vacuum distinction.
The network answer still overstates proximity/built-in capabilities as sufficient
for local communication. Agriculture still digresses into a food-loss statistic;
the current-events question still returns an evidence gap. Consequently the
context-preservation change is a **local implementation candidate, not an overall
answer-quality pass or approved release**. It does not change ranking, policies,
models, downloads or catalog publication. Phone testing and installation remain
deferred. Further quality work must address these failures before promotion.

#### Whole-paragraph pack candidate (not published)

The reference builder has an opt-in `--preserve-paragraphs` mode. It groups whole
paragraphs toward a soft 1,200-character target without character overlap; English
dependent/qualification paragraphs stay with the preceding paragraph even when
the unit exceeds the target. It cannot guarantee cross-section or semantic
completeness. The legacy default and Biology/Travel builds are unchanged.

```sh
python3 tools/packs/build_general_reference.py build --preserve-paragraphs \
  --version 0.2.0 --output build/general-reference/paragraph-pilot
```

The existing snapshot yields 1,307 chunks (longest 2,350 characters), versus 1,484
legacy chunks. Full rebuilds produced identical candidate SHA-256 values:
`13770f1ae8d2f1ea68741b8af0c4a1961902bca34662294ac5790371da9b76e5`.
A legacy rebuild still matches the original pack SHA-256
`7866c3a563c0cdbe300325a3d09368d3dc2a3d9770ceb62881b23f1de14583e9`.
Tests check lossless paragraph grouping, qualifications, deterministic packaging,
and unchanged default behavior. No competitor dataset is used.

Seven development questions were run in
`build/general-reference/evaluation/whole-paragraph-pack/report.json`. Removing
artificial chunk boundaries did not resolve generation errors: the heat answer
still uses an inaccurate photons-versus-particles contrast and omits a requested
radiation example; networking still overstates proximity as sufficient; the genes
answer introduces unrelated correlation material; and the database answer
overgeneralizes one database category. The current-information request still
returns an evidence gap. These failures block catalog publication. A structurally
better pack is not necessarily a better answer source under the current model
and retrieval pipeline.

### Historical Reference fixture

`fixtures/reference/documents.jsonl` contains twelve compact project-authored CC0 reference notes covering seasons, water, solar storage, evidence comparison, Formula One, cars, batteries, climate, computing, networks, health information, and historical evidence. This remains a reproducibility fixture for the historical v2 benchmark; it is no longer bundled into the Android app. It is focused coverage, not a comprehensive encyclopedia.

Build it with:

```sh
./scripts/build_reference_pack.sh
```

The build is deterministic and preserves document IDs, titles, provenance, license, and source passages for local citations.

### Wikivoyage places to eat (optional build)

`tools/packs/build_wikivoyage_eat_pack.py` converts the pinned [1 September 2026 English Wikivoyage pages dump](https://dumps.wikimedia.org/enwikivoyage/20260901/enwikivoyage-20260901-pages-articles.xml.bz2) into one local document per `Eat` listing. Each document retains its destination, venue name, description, listing update date when present, page revision date, and a permalink to that revision. The text license is [CC BY-SA 4.0](https://en.wikivoyage.org/wiki/Wikivoyage:Copyleft). The dump SHA-256 is `c6cebf6b109c31698e736858fd1d8dec1c41d87437aa4c4cafd7b0df88777773`.

The verified local build contains 79,566 listings in a 55,047,040-byte `.fapack` (SHA-256 `adf90e6b4dfbf1da0e538862400130071c49dc56cf5d81ea005e0f5a7a141e73`). This pack is an optional build artifact, not bundled with the APK or published as a release asset. Listings can be incomplete or stale; a dated mention of a vegan option does not establish current opening, menu, or quality. The exact build command is in [tools/README.md](tools/README.md).

### Wikipedia mini (keyword-only builder)

`tools/packs/wiki_mini_build.py` selects a bounded set of articles from a Wikipedia dump and creates a general-background `.fapack` with FTS5 search. It does not require generating embeddings. See the [tool instructions](tools/README.md#typical-flows). It is a builder, not a bundled or published pack; its actual size, content, phone performance, and answer quality must be checked on the resulting artifact before making coverage claims. The source text is CC BY-SA 4.0, with attribution retained per document.

### Starter evidence pack

`fixtures/starter/documents.jsonl` contains four small, project-authored CC0 documents. It exists to prove deterministic pack construction, Android import, FTS5 retrieval, citation display, the required seasons smoke query, and the offline workflow. It is not broad coverage. Questions outside this pack can still be answered by the offline model, but those answers are uncited and are not treated as source-backed research.

Its manifest declares `demo` coverage, a short scope summary, and three example questions that are answerable from those documents. Field Atlas uses this metadata only for disclosure and query suggestions; it never treats it as model instructions.

The Starter pack is a reproducibility fixture rather than a current release asset. Build it locally with the command below; the deterministic result is 25,500 bytes with SHA-256 `51769d845dc163aa4a56a15d9ad68eb3d65f12f1649d56b2105a906c9e0c453b`.

Every JSONL row retains its document ID, title, source attribution, license, and original text. The builder applies Unicode NFKC normalization, orders documents by UTF-8 document ID, and creates paragraph-first chunks of at most 1,200 characters with 150 characters of overlap. Chunk IDs have the form `<document_id>:<zero-padded-index>`.

Build it with:

```sh
./scripts/build_starter_pack.sh
```

The command creates `build/packs/starter/content.sqlite`, canonical `manifest.json`, `SHA256SUMS`, and `fieldatlas-starter-1.0.0.fapack`. ZIP entries are uncompressed, ordered, and timestamped at the ZIP epoch so two clean builds from identical source bytes are identical.

The authoritative per-document provenance table is [fixtures/starter/LICENSES.md](fixtures/starter/LICENSES.md). No downloaded, scraped, paywalled, or third-party corpus is embedded in this starter pack.
