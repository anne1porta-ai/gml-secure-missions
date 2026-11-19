import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 120000,
  expect: {
    timeout: 15000,
  },
  retries: 0,
  workers: 1,
  reporter: 'list',

  use: {
    baseURL: 'http://127.0.0.1:8088',
    browserName: 'chromium',
    headless: true,
  },
});
