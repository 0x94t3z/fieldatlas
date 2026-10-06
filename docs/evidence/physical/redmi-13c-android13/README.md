# Redmi 13C, Android 13: Field Atlas 1.4.0

Checks of the 1.4.0 source on a Redmi 13C (`23108RN04Y`, 6 GB, Android 13, API 33, ARM64) on
6–7 October 2026, over USB. The build was the optimized release APK signed with the local test key
the phone already had (the public-key APK cannot update a test-key installation without removing
the app's data), installed with `adb install -r` over 1.3.0 so the model and collections stayed. The
table below was recorded on the build of 22:03 (SHA-256
`0a16013e5a9851c76087a867c28366227b303ee695514a4f603dfc8ec8350518`); the final 1.4.0 source, which
differs by a few routing guards found in the last gradings, was then built, installed the same way
and re-asked the phrase, the fuel calculation and the heat question with the same answers (4 s,
0 s, 40 s). That final APK's SHA-256 is in [`apk-sha256.txt`](apk-sha256.txt); the package version in
[`package-version.txt`](package-version.txt); the Android release in
[`android-version.txt`](android-version.txt).

Airplane mode was on and Wi-Fi and mobile data were off for every answer below
([`airplane-mode.txt`](airplane-mode.txt), [`home-offline.png`](home-offline.png) shows "Offline
now"). The five 1.4.0 collections (Travel guides 2026.09.01, Vegan places 2026.10.05, Essentials
2026.10.05, Cryptography & Ethereum 2026.10.05, Encyclopedia in depth 2026.10.06) were imported from
`.fapack` files; Library switched the older versions off by itself ("not used") and kept them
installed.

| Question | Path | Time to the answer | Screenshot |
| --- | --- | --- | --- |
| How do I say thank you in Japanese? | Phrasebook, quoted alone, no model | 4 s | [`phrase.png`](phrase.png) |
| Road trip is 840 km. The rental uses 6.5 litres per 100 km and petrol is 1.89 euros a litre. If 3 of us split the fuel, how much does each person pay? | Calculated on the phone, no model | 0 s (54.6 L, 103.19 euros, 34.40 euros each) | [`fuel.png`](fuel.png) |
| Someone in our group collapsed in the heat on a hike. Their skin is hot, they're confused and they've stopped sweating. What do we do? | Heat illness guide; the Heat Stroke section leads (call emergency services, do NOT give fluids), then the model's steps | 52 s | [`heat.png`](heat.png) |
| why do onions make you cry when you cut them? | Encyclopedia in depth: the "Eye irritation" section of the English article quoted, then the model | 1 m 45 s | [`onions.png`](onions.png) |
| Tell me about Japan | Encyclopedia article quoted, then the model | 2 m 24 s | [`japan.png`](japan.png) |
| Best vegan restaurants in Berlin | Vegan places, six places with the ordering stated, no model | 3 s | [`vegan-berlin.png`](vegan-berlin.png) |
| Tell me the best vegan restaurants in the city I am currently in | Phone position; no vegan place is mapped within 15 km of where the phone was (a rural area), and the answer said so | 0 s after the fix | not shown (the screen names the place) |
| Where am I? | Phone position: the town from the nearest mapped places, the coordinates and the three closest places | 0 s | not shown (the screen names the place) |

The times are the app's own "took" figure. The first answer after opening the app waits for the
model to load (about 20 s on this phone); the position questions wait up to 30 s for a GPS fix,
which indoors did not come in airplane mode, so the phone was briefly connected to get a fix and
then put back in airplane mode: the answers used that fix, which the app reports with its age when it
is older than ten minutes. The two position answers are not shown because they name where the phone
was.

The first 1.4.0 build stopped on launch: Android's regex engine (ICU) reads `[:.]` as the start of a
POSIX class and refused a calculator pattern the JVM accepts. Two patterns were rewritten and an
instrumentation test (`OnDeviceRegexTest`) now compiles every pattern literal of the app on the
device and runs the calculators there; it passed on this phone (416 patterns, 7 calculations).

Memory while idle with the model loaded is in [`app-meminfo.txt`](app-meminfo.txt) (total PSS about
1.67 GB, most of it the mapped model file); the phone reports 5,867,976 kB of RAM
([`phone-meminfo.txt`](phone-meminfo.txt)).

Not checked here: the public-key APK, the in-app downloads, a clean install following only the
README, network traffic during research, and the optional 4B (this phone has 6 GB and is not offered
it).
