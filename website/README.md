# Field Atlas website

Static branding and download site. This folder deploys independently from the
Android app. It uses local SVG artwork and system fonts, with no analytics,
account system, cookies, or external font requests. A small optional script
controls animation; downloads, navigation, and FAQ work without JavaScript.

## Preview and verify

From the repository root, with Node and Python 3 installed:

```sh
npm ci --prefix website
npm run dev --prefix website
```

Open http://127.0.0.1:4173. This development server exposes the website source
folder on loopback only; it is not the production deployment.

Stop the preview before running browser tests (they own port 4173):

```sh
npm test --prefix website
node --test website/tests/build.test.mjs website/tests/release-links.test.mjs
npm run build --prefix website
```

`npm run build` first runs `tools/check-release-links.mjs`, which asks GitHub whether every
release, asset and file `index.html` links to is one a visitor can get (a published release, an
uploaded asset, a file at that tag), and stops the build if not. It needs network access to
github.com and api.github.com; without it the build fails rather than skipping the check. The
build tests run `build.mjs` directly and do not use the network.

Browser tests use installed Google Chrome by default. To use Playwright Chromium,
run `npx playwright install chromium` inside `website/`, then set
`PLAYWRIGHT_CHANNEL=chromium` when testing. Browser installation is development-only.
Results belong in ignored `build/website/`, not public assets.

## Deploy to Vercel

Import the existing repository into your Vercel account with these settings:

- Root Directory: `website`
- Framework Preset: Other
- Build Command: `npm run build`
- Output Directory: `dist`

The checked-in `vercel.json` supplies the build/output settings and security
headers. Its build command runs the release-link check above, so a page linking to a
release that is not published, or an asset not uploaded, fails to build and is not deployed;
the deployment already live stays. `build.mjs` copies an explicit allowlist of public files to `dist/`.
Tests, node_modules, private evidence, and Android files are not published.
No environment variables, paid plan, or custom domain are required by this site.
Do not deploy the repository root.

Alternatively, after authenticating with the Vercel CLI, run `vercel` from
`website/` for a preview. Confirm the account/project before production deployment.
Local `.vercel/` metadata is ignored. Never commit credentials.

After deployment, check `/`, `/styles.css`, and `/assets/atlas.svg`; also confirm
`/package.json` and `/tests/site.spec.mjs` return 404. To smoke-test a public URL:

```sh
SITE_URL=https://your-actual-deployment.vercel.app npm test --prefix website
```

The canonical URL is `https://www.getfieldatlas.com/`. Open Graph uses
`assets/social-card.png`, a 1200 × 630 PNG. X uses a `summary` card with
`assets/social-icon.png`, a 600 × 600 PNG of the Atlas mark, so compact link
previews can show the icon instead of an empty thumbnail. If the public domain
changes, update the canonical URL, `og:url`, and both image URLs together.
Social platforms may retain cached previews after deployment; the website
cannot force an existing X post to refresh.

The favicon uses `assets/atlas.svg`, with root-level ICO/PNG fallbacks and an
Apple touch icon. An asset-content version in the page URL avoids cached artwork from before the
filename swap. `assets/compass.svg` is the large hero illustration; its URL is
versioned too. Keep these versions in sync when changing either SVG.
To regenerate the favicon fallbacks and X preview icon from the same logo, run
`node website/tools/render-icons.mjs` and commit the generated icons. These are
separate from the social preview; chat apps may still use their own generic link icon.

The card is the island's title screen, rendered from the site itself. After changing
the hero or the world, run from the repo root:

```sh
node website/tools/render-social-card.mjs
```

The renderer uses the same installed browser as the tests. Commit the generated
PNG along with its source. Production builds copy the PNG without needing a
browser; the renderer and HTML template are excluded from the public build.

The page is an island tour. `boot.js` runs before first paint and picks the view:
the island (`html.game`) when WebGL is available and the screen is at least 500px
tall, otherwise the plain stacked page. Crawlers always get the plain page, and so
does anyone who follows "Skip to content". `game.js` maps the scroll position to the
seven stops, opens each section as a pop-up, and keeps the address in step (`#faq`
and the other section ids can be shared). `world.js` draws the island with the
vendored Three.js r160 (`vendor/`, MIT licence alongside it) and is loaded only for
the island view; if it cannot start, the plain page takes over. `audio.js`
synthesises the music and sound effects with Web Audio, so there are no media files.
Sound starts from a tap, never a key press, and visitors can mute it; the mute is
remembered.
Reduce Motion turns the world into a still picture: no walking animation, no
helicopter, no confetti. Background tabs stop drawing.
All animation styles are in `styles.css`. CSP allows only same-origin scripts,
styles, images, and fetches; it does not allow inline scripts or styles.

## Updating the app download

The Community section has its own numbered label and divider below FAQ, separately
from downloads. On mobile, `navigation.js` adds an expandable menu that closes on
selection or Escape. Without JavaScript, navigation links remain visible. Its contract
address is available without JavaScript; `community.js` adds clipboard copying
with a brief checkmark on success and visible failure feedback. No success text
or extra row is added. Bankr and Dex Screener open only on user clicks.
Their icons are hosted locally, sourced from `https://bankr.bot/favicon.svg` and
`https://dexscreener.com/favicon.png`; the Base network icon comes from
`https://www.base.org/favicon.ico`. These marks identify external platforms,
not partnerships. Preserve the independent-launch and fee disclosure when editing.

The website currently targets **v1.4.2**, marked **Release**.
It must not use `/releases/latest`, which may point to an older stable release.
When updating, change these together in `index.html` and the browser assertions:

- Both APK links and version labels.
- Checksum link and release-note links.
- Tagged setup-guide and pack-tool links.
- Android requirements, APK size, available collections, and limitations if changed.

Verify the GitHub asset URLs without downloading large model or collection packs.
The APK is hosted on GitHub, never duplicated into this website. Android releases
are a separate workflow and are not built or published by Vercel.
