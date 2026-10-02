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
node --test website/tests/build.test.mjs
npm run build --prefix website
```

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
headers. `build.mjs` copies an explicit allowlist of fourteen public files to `dist/`.
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

To edit the card, update `tools/social-card.html`, then run from the repo root:

```sh
node website/tools/render-social-card.mjs
```

The renderer uses the same installed browser as the tests. Commit the generated
PNG along with its source. Production builds copy the PNG without needing a
browser; the renderer and HTML template are excluded from the public build.

`motion.js` loads the local compass SVG into the page for continuous needle and
headline animation. There is no on-page motion control. The SVG file remains
the single artwork source. If the script or SVG request fails, the page stays
static and usable. Reduce Motion disables animation, and background tabs pause it.
Feature cards, setup steps, collections, and FAQ rows also ease into view on
scroll and replay when they re-enter. IntersectionObserver tracks stationary
containers; only their children move. Content is never fully hidden, and keyboard
focus stops the reveal on the focused container. Without JavaScript or observer
support, these sections remain static.
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

The website currently targets **v1.2.0**, marked **Release**.
It must not use `/releases/latest`, which may point to an older stable release.
When updating, change these together in `index.html` and the browser assertions:

- Both APK links and version labels.
- Checksum link and release-note links.
- Tagged setup-guide and pack-tool links.
- Android requirements, APK size, available collections, and limitations if changed.

Verify the GitHub asset URLs without downloading large model or collection packs.
The APK is hosted on GitHub, never duplicated into this website. Android releases
are a separate workflow and are not built or published by Vercel.
