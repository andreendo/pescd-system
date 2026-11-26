import { defineConfig, devices } from '@playwright/test';

const isCI = !!process.env.CI;
const mvnw = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';

export default defineConfig({
  testDir: './tests',
  // Os testes compartilham o mesmo banco (dados do DataInitializer),
  // então rodam em sequência para um não alterar o estado do outro.
  fullyParallel: false,
  workers: 1,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: [
    {
      command: `${mvnw} spring-boot:run`,
      cwd: '..',
      url: 'http://localhost:8082/api/offers',
      reuseExistingServer: !isCI,
      timeout: 180_000,
    },
    {
      command: 'npm run dev',
      cwd: '../frontend',
      url: 'http://localhost:5173',
      reuseExistingServer: !isCI,
      timeout: 60_000,
    },
  ],
});
