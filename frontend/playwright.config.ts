import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    channel: process.env.PLAYWRIGHT_CHANNEL,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command:
        'java -Djava.awt.headless=true -jar ../backend/target/optifit-0.1.0.jar --server.address=127.0.0.1',
      url: 'http://127.0.0.1:8080/actuator/health',
      reuseExistingServer: !process.env.CI,
      env: { OPTIFIT_MODE: 'demo', IP_HOURLY_LIMIT: '100' },
    },
    { command: 'npm run dev', url: 'http://127.0.0.1:5173', reuseExistingServer: !process.env.CI },
  ],
})
