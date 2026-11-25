import { test, expect } from '../fixtures/test.js';

test('U.01 - administrador faz login e vai para a tela de usuários', async ({ page, loginPage }) => {
  await loginPage.goto();
  await loginPage.login('admin', 'admin123');

  await expect(page).toHaveURL('/admin/usuarios');
  await expect(loginPage.loggedUser).toContainText('ADMIN');
});
