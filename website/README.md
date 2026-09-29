# Field Atlas website

Static branding and download site. This folder deploys independently from the
Android app. It uses local SVG artwork and system fonts; no client JavaScript,
analytics, account system, cookies, or external font requests.

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
headers. `build.mjs` copies an explicit allowlist of four public files to `dist/`.
Tests, node_modules, private evidence, and Android files are not published.
No environment variables, paid plan, or custom domain are required by this site.
Do not deploy the repository root.

Alternatively, after authenticating with the Vercel CLI, run `vercel` from
`website/` for a preview. Confirm the account/project before production deployment.
Local `.vercel/` metadata is ignored. Never commit credentials.

After deployment, check `/`, `/styles.css`, and `/assets/compass.svg`; also confirm
`/package.json` and `/tests/site.spec.mjs` return 404. To smoke-test a public URL:

```sh
SITE_URL=https://your-actual-deployment.vercel.app npm test --prefix website
```

Add a canonical URL and `og:url` only once the actual public hostname is known.
Social title/description are present; no unsupported SVG social-image preview is
claimed. The SVGs are the favicon/logo and decorative page artwork.

The compass needle rotates slowly unless Reduce Motion is enabled. Its SVG style
block is authorized by an exact SHA-256 hash in `vercel.json`, not `unsafe-inline`.
If that style block changes, update the CSP hash and rerun the browser tests.

## Updating the app download

The website currently targets **v1.2.0-rc.1**, explicitly marked **Prerelease**.
It must not use `/releases/latest`, which may point to an older stable release.
When updating, change these together in `index.html` and the browser assertions:

- Both APK links and version labels.
- Checksum link and release-note links.
- Tagged setup-guide and pack-tool links.
- Android requirements, APK size, available collections, and limitations if changed.

Verify the GitHub asset URLs without downloading large model or collection packs.
The APK is hosted on GitHub, never duplicated into this website. Android releases
are a separate workflow and are not built or published by Vercel.
