// Writes the pack list on depack/index.html from the app's catalog (catalog-2.json), so the page
// always lists exactly what the app's Library offers. Run after a catalog change:
//   node tools/depack-list.mjs          rewrite the list
//   node tools/depack-list.mjs --check  fail if the page is out of date
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const catalogUrl = new URL('../app/src/main/assets/knowledge/catalog-2.json', root);
const pageUrl = new URL('depack/index.html', root);
// The app's Library order (KnowledgeCategory in AssetPresentation.kt).
const CATEGORIES = ['Encyclopedia', 'Places', 'Travel', 'Language', 'How-to', 'Science', 'Other'];

const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export function size(bytes) {
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(2)} GB`;
  if (bytes >= 1e7) return `${Math.round(bytes / 1e6)} MB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(1)} MB`;
  return `${Math.round(bytes / 1e3)} KB`;
}

export function licence(id) {
  return id.split(/\s+AND\s+/).map((part) => {
    const cc = /^CC-(.+)-(\d\.\d)$/.exec(part);
    if (cc) return `CC ${cc[1]} ${cc[2]}`;
    return { 'CC0-1.0': 'CC0', 'ODbL-1.0': 'ODbL', 'LicenseRef-US-Government-Work': 'US government work' }[part] ?? part;
  }).join(' + ');
}

// The app's short Discover line (discoverLine in AssetPresentation.kt), plus two the page needs.
const SHORT = {
  'wikipedia-encyclopedia': 'Wikipedia in English',
  'wikivoyage-places': 'Food, hotels, sights',
  'osm-vegan-places': 'Vegan food and shops',
  'osm-essentials': 'Pharmacies, hospitals, ATMs',
  'wikivoyage-guides': 'Getting around, money, safety',
  'world-knowledge-biology': 'Biology, health, ageing',
  'crypto-ethereum': 'Ethereum and cryptography',
  'encyclopedia-index': 'Every subject, found by meaning',
};
// Sizes written into a catalog description; the page shows them in their own column.
const SIZES = /;?\s*[0-9][0-9.,]*\s*[KMGT]B download(?:,\s*[0-9][0-9.,]*\s*[KMGT]B installed)?/g;
const prose = (d) => d.replace(SIZES, '').replace(/\s+·\s+/g, '. ').replace(/\.\./g, '.').trim();
export function short(pack) {
  if (SHORT[pack.id]) return SHORT[pack.id];
  const first = prose(pack.description).split(/[.;:·]/)[0].trim();
  return first.length > 40 ? `${first.slice(0, 39).trimEnd()}…` : first;
}

// One icon per collection; a new pack falls back to its category's icon.
const ICON = {
  'wikipedia-encyclopedia': 'book', 'encyclopedia-index': 'search', 'osm-essentials': 'cross',
  'wikivoyage-places': 'pin', 'osm-vegan-places': 'bowl', 'wikivoyage-guides': 'map',
  'practical-skills': 'flame', 'world-knowledge-biology': 'leaf', 'crypto-ethereum': 'cube',
  'openstax-maths-stats': 'sum',
};
const CATEGORY_ICON = { Encyclopedia: 'book', Places: 'pin', Travel: 'map', Language: 'speech', 'How-to': 'flame', Science: 'leaf', Other: 'file' };
// Words people search with that a description may not contain.
const ALSO = {
  Encyclopedia: 'wiki wikipedia facts history people', Places: 'near me nearby map location city',
  Travel: 'trip holiday city country visa transport', Language: 'phrases words translate',
  'How-to': 'survival emergency outdoors camping', Science: 'study school learn',
};
export const categoryOf = (pack) => (CATEGORIES.includes(pack.category) ? pack.category : 'Other');
export const iconOf = (pack) => ICON[pack.id] ?? CATEGORY_ICON[categoryOf(pack)];
const searchText = (pack, addOns) => [pack.title, short(pack), prose(pack.description), categoryOf(pack), ALSO[categoryOf(pack)] ?? '',
  licence(pack.license), ...addOns.map((a) => `${a.title} ${prose(a.description)}`)]
  .join(' ').toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '').replace(/\s+/g, ' ');

function row(pack, addOns) {
  const extra = addOns.map((a) => `
                <p class="addon">Add-on: ${escape(a.title.replace(/^.*?:\s*/, ''))}. ${escape(prose(a.description).split('. ')[0])}. <a href="${escape(a.url)}">Download, ${size(a.bytes)}</a></p>`).join('');
  return `
          <article class="book" id="${escape(pack.id)}" data-category="${escape(categoryOf(pack))}" data-bytes="${pack.bytes}" data-text="${escape(searchText(pack, addOns))}">
            <div class="cover">
              <svg class="icon stamp" aria-hidden="true"><use href="#i-${iconOf(pack)}"/></svg>
              <h3>${escape(pack.title)}</h3>
              <p class="line">${escape(short(pack))}</p>
            </div>
            <div class="take">
              <p class="size">${size(pack.bytes)}${pack.bytes >= 1e9 ? '<span class="wifi">Use Wi-Fi</span>' : ''}</p>
              <a class="get" href="${escape(pack.url)}" aria-label="Download ${escape(pack.title)}, ${size(pack.bytes)}"><svg class="icon" aria-hidden="true"><use href="#i-download"/></svg>Download</a>
            </div>
            <details>
              <summary>Details</summary>
              <div class="more">
                <p>${escape(prose(pack.description))}</p>
                <dl>
                  <div><dt>Installed</dt><dd>${size(pack.installedBytes)}</dd></div>
                  <div><dt>Licence</dt><dd>${escape(licence(pack.license))}</dd></div>
                  <div><dt>Version</dt><dd>${escape(pack.version)}</dd></div>
                  <div><dt>SHA-256</dt><dd><code>${escape(pack.sha256)}</code></dd></div>
                </dl>${extra}
              </div>
            </details>
          </article>`;
}

export function render(catalog) {
  const packs = catalog.packs;
  const addOnsOf = (id) => packs.filter((p) => p.requires?.id === id);
  const shown = packs.filter((p) => !p.requires);
  const slug = (c) => `group-${c.toLowerCase().replace(/[^a-z]+/g, '-')}`;
  const groups = CATEGORIES.map((category) => [category, shown
    .filter((p) => categoryOf(p) === category)
    .sort((a, b) => a.title.localeCompare(b.title))]).filter(([, list]) => list.length);
  return groups.map(([category, list]) => `
      <section class="group" aria-labelledby="${slug(category)}">
        <h2 id="${slug(category)}">${escape(category)} <span class="count">${list.length}</span></h2>
        <div class="shelf">${list.map((p) => row(p, addOnsOf(p.id))).join('')}
        </div>
      </section>`).join('') + '\n      ';
}

export function chips(catalog) {
  const shown = catalog.packs.filter((p) => !p.requires);
  const counts = CATEGORIES.map((c) => [c, shown.filter((p) => categoryOf(p) === c).length]).filter(([, n]) => n);
  return [['all', 'All', shown.length], ...counts.map(([c, n]) => [c, c, n])].map(([value, label, n]) =>
    `<button type="button" class="chip" data-category="${escape(value)}" aria-pressed="${value === 'all'}">${escape(label)} <span>${n}</span></button>`).join('');
}

export function totals(catalog) {
  const shown = catalog.packs.filter((p) => !p.requires);
  // The collections themselves; the optional search-by-meaning add-ons are not counted.
  const sum = (key) => shown.reduce((n, p) => n + p[key], 0);
  return { count: shown.length, download: size(sum('bytes')), installed: size(sum('installedBytes')) };
}

const START = '<!-- packs:start -->';
const END = '<!-- packs:end -->';
if (fileURLToPath(import.meta.url) === process.argv[1]) {
  const catalog = JSON.parse(readFileSync(catalogUrl, 'utf8'));
  const page = readFileSync(pageUrl, 'utf8');
  const [head, rest] = page.split(START);
  const tail = rest.split(END)[1];
  const t = totals(catalog);
  const next = `${head}${START}${render(catalog)}${END}${tail}`
    .replace(/(<dd data-total="count">)[^<]*/, `$1${t.count}`)
    .replace(/(<dd data-total="download">)[^<]*/, `$1${t.download}`)
    .replace(/(<dd data-total="installed">)[^<]*/, `$1${t.installed}`)
    .replace(/(<div class="chips" role="group" aria-label="Category">)[\s\S]*?(<\/div>)/, `$1${chips(catalog)}$2`);
  if (process.argv.includes('--check')) {
    if (next !== page) { console.error('depack/index.html is out of date: run node tools/depack-list.mjs'); process.exit(1); }
  } else {
    writeFileSync(pageUrl, next);
    console.log(`Wrote ${catalog.packs.length} packs to depack/index.html`);
  }
}
