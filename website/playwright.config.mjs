import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.mjs',
  outputDir: '../build/website/test-results',
  reporter: 'list',
  timeout: 15000,
  use: { baseURL: process.env.SITE_URL || 'http://127.0.0.1:4173', headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' },
  webServer: process.env.SITE_URL ? undefined : {
    command: 'npm run dev', url: 'http://127.0.0.1:4173', reuseExistingServer: false,
  },
});
