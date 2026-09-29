import { mkdir, copyFile, rm } from 'node:fs/promises';

// Only these public assets enter the deployment. Tests and dependencies never do.
const root = new URL('./', import.meta.url);
const dist = new URL('./dist/', root);
await rm(dist, { recursive: true, force: true });
await mkdir(new URL('assets/', dist), { recursive: true });
for (const file of ['index.html', 'styles.css', 'motion.js', 'community.js', 'navigation.js', 'favicon.ico', 'favicon.png', 'apple-touch-icon.png', 'assets/atlas.svg', 'assets/compass.svg', 'assets/social-card.png', 'assets/bankr.svg', 'assets/dexscreener.png', 'assets/base.ico']) {
  await copyFile(new URL(file, root), new URL(file, dist));
}
console.log('Built website/dist with 14 public files.');
