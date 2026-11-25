import { test as base, expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage.js';
import { USERS } from './users.js';

export const test = base.extend({
  loginPage: async ({ page }, use) => {
    await use(new LoginPage(page));
  },

  loginAs: async ({ page, loginPage }, use) => {
    await use(async (username) => {
      const user = USERS[username];
      if (!user) {
        throw new Error(`Usuário ${username} não está em fixtures/users.js`);
      }
      await loginPage.goto();
      await loginPage.login(username, user.password);
      await page.waitForURL(user.home);
    });
  },
});

export { expect };
