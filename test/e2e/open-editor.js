const { chromium } = require('playwright');

const required = (name) => {
  const value = process.env[name];
  if (!value) {
    console.error(`missing ${name}`);
    process.exit(2);
  }
  return value;
};

const baseUrl = required('PENPOT_URL').replace(/\/+$/, '');
const email = required('PENPOT_EMAIL');
const password = required('PENPOT_PASSWORD');
const teamId = required('TEAM_ID');
const fileId = required('FILE_ID');
const executablePath = process.env.CHROME || undefined;

(async () => {
  const browser = await chromium.launch({ executablePath });
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 1000 } })).newPage();
  page.on('console', (message) => {
    if (/MCP STATUS/.test(message.text()) && /status="connected"/.test(message.text())) {
      console.log('connected');
    }
  });
  await page.goto(`${baseUrl}/#/auth/login`);
  await page.fill('input[type=email], input[name=email]', email);
  await page.fill('input[type=password]', password);
  await page.click('button[type=submit]');
  await page.waitForURL(/dashboard/, { timeout: 30000 });
  await page.goto(`${baseUrl}/#/workspace?team-id=${teamId}&file-id=${fileId}`);
  console.log('opened');
  const shutdown = async () => {
    await browser.close();
    process.exit(0);
  };
  process.stdin.on('end', shutdown);
  process.stdin.resume();
})().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
