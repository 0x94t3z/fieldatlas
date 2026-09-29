import { mkdir, copyFile, rm } from 'node:fs/promises';

// Only these public assets enter the deployment. Tests and dependencies never do.
const root = new URL('./', import.meta.url);
const dist = new URL('./dist/', root);
await rm(dist, { recursive: true, force: true });
await mkdir(new URL('assets/', dist), { recursive: true });
for (const file of ['index.html', 'styles.css', 'motion.js', 'assets/compass.svg', 'assets/atlas.svg', 'assets/social-card.png']) {
  await copyFile(new URL(file, root), new URL(file, dist));
}
console.log('Built website/dist with 6 public files.');
