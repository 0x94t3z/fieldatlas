// Runs before the website is built for deployment (`npm run build`, Vercel's build command): every GitHub release,
// asset and file the page links to must be one a visitor can get, or the build fails and the deployment with it.
// A tag is not a release (github.com shows a page for a tag with no release, and drafts are invisible), so:
//  - a download must be answered with the file or a redirect to it, which GitHub gives only for an uploaded asset
//    of a published release (redirects are not followed, so nothing is downloaded);
//  - a release page needs a published release in the GitHub API, unless a download of that tag proved it;
//  - a file at a tag must exist.
// The same rules, for the docs, are tools/maintenance/check_release_links.py at the repository root.
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const REPO = '0x94t3z/fieldatlas';
const TAG = 'v\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.]+)?';

export function links(text, repo = REPO) {
  const base = 'https://github\\.com/' + repo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const pattern = new RegExp(`${base}/(?:(releases/download)/(${TAG})/([^\\s"'<>)]+)|(releases/tag)/(${TAG})|(blob|tree)/(${TAG})/([^\\s"'<>)#]+))`, 'g');
  const found = [];
  text.split('\n').forEach((line, i) => {
    for (const m of line.matchAll(pattern)) {
      const url = m[0].replace(/[.,;:]+$/, '');
      if (m[1]) found.push({ line: i + 1, kind: 'download', tag: m[2], url });
      else if (m[4]) found.push({ line: i + 1, kind: 'release', tag: m[5], url });
      else found.push({ line: i + 1, kind: 'file', tag: m[7], url });
    }
  });
  return found;
}

/** 'file:line url: why' for every link a visitor would not get. head(url) -> status; release(tag) -> release or null. */
export async function problems(files, head, release, repo = REPO) {
  const found = Object.entries(files).flatMap(([path, text]) => links(text, repo).map((link) => ({ path, ...link })));
  const status = new Map();
  const checkHead = async (url, ok, what) => {
    if (!status.has(url)) {
      try {
        const code = await head(url);
        status.set(url, ok(code) ? null : `${what} (HTTP ${code})`);
      } catch (error) {
        status.set(url, `could not be checked (${error.message})`);
      }
    }
    return status.get(url);
  };
  const served = (code) => code >= 200 && code < 400;
  const proven = new Set();
  for (const link of found) {
    if (link.kind === 'download' && (await checkHead(link.url, served, 'no published asset')) === null) proven.add(link.tag);
  }
  const releases = new Map();
  for (const link of found) {
    if (link.kind !== 'release' || proven.has(link.tag) || releases.has(link.tag)) continue;
    try {
      const data = await release(link.tag);
      releases.set(link.tag, data === null ? 'no published release' : data.draft ? 'the release is a draft' : null);
    } catch (error) {
      releases.set(link.tag, `could not be checked (${error.message})`);
    }
  }
  const out = [];
  for (const link of found) {
    const why = link.kind === 'download' ? await checkHead(link.url, served, 'no published asset')
      : link.kind === 'release' ? (proven.has(link.tag) ? null : releases.get(link.tag))
      : await checkHead(link.url, (code) => code === 200, 'no such file at this tag');
    if (why) out.push(`${link.path}:${link.line} ${link.url}: ${why}`);
  }
  return out;
}

async function httpHead(url) {
  const response = await fetch(url, { method: 'HEAD', redirect: 'manual', headers: { 'user-agent': 'fieldatlas-release-links' },
    signal: AbortSignal.timeout(30_000) });
  return response.status;
}

async function apiRelease(tag) {
  const headers = { accept: 'application/vnd.github+json', 'user-agent': 'fieldatlas-release-links' };
  if (process.env.GITHUB_TOKEN) headers.authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  const response = await fetch(`https://api.github.com/repos/${REPO}/releases/tags/${tag}`, { headers, signal: AbortSignal.timeout(30_000) });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`GitHub API answered ${response.status}`);
  return response.json();
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const page = new URL('../index.html', import.meta.url);
  const found = await problems({ 'index.html': await readFile(page, 'utf8') }, httpHead, apiRelease);
  for (const problem of found) console.error(`not served: ${problem}`);
  if (found.length) {
    console.error(`${found.length} link(s) a visitor would not get: the website is not built. Publish the release first, or keep this page local.`);
    process.exit(1);
  }
  console.log('Release links: every release, asset and file the page links to is served.');
}
