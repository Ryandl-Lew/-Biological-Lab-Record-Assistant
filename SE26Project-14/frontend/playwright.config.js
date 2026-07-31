import { defineConfig, devices } from '@playwright/test'

const backendPort = process.env.E2E_BACKEND_PORT || '8080'
const backendOrigin = `http://127.0.0.1:${backendPort}`
const frontendPort = process.env.E2E_FRONTEND_PORT || '5173'
const frontendOrigin = `http://127.0.0.1:${frontendPort}`
const outputDir = process.env.E2E_OUTPUT_DIR || 'test-results'
const htmlReportDir = process.env.E2E_HTML_REPORT_DIR || 'playwright-report'
const databaseUrl =
  process.env.E2E_DB_URL ||
  'jdbc:mysql://127.0.0.1:3306/bionote?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
const h2Database = databaseUrl.startsWith('jdbc:h2:')

export default defineConfig({
  testDir: './tests/e2e',
  outputDir,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: htmlReportDir }]],
  use: {
    baseURL: frontendOrigin,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: '.\\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.profiles=dev"',
      cwd: '../backend',
      url: `${backendOrigin}/actuator/health`,
      timeout: 120_000,
      reuseExistingServer: false,
      env: {
        ...process.env,
        SERVER_PORT: backendPort,
        E2E_API_BASE: `${backendOrigin}/api/v1`,
        VITE_API_PROXY_TARGET: backendOrigin,
        DB_URL: databaseUrl,
        DB_USERNAME: process.env.E2E_DB_USERNAME || (h2Database ? 'sa' : 'bionote'),
        DB_PASSWORD: process.env.E2E_DB_PASSWORD ?? (h2Database ? '' : 'bionote-dev'),
        JWT_SECRET: 'e2e-secret-with-at-least-thirty-two-characters-123456',
        FRONTEND_ORIGIN: frontendOrigin,
        DEV_SEED_ENABLED: 'false',
        AGENT_ENABLED: 'true',
        AGENT_PROVIDER: 'fake',
        AGENT_MODEL: 'fake-deterministic-v1',
        AGENT_SAME_SUBJECT_COOLDOWN_SECONDS: '0',
        AGENT_WORKER_POLL_MS: '500',
        UPLOAD_ROOT: process.env.E2E_UPLOAD_ROOT || '../tmp/e2e-uploads',
      },
    },
    {
      command: `npm.cmd run dev -- --host 127.0.0.1 --port ${frontendPort}`,
      cwd: '.',
      url: frontendOrigin,
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
