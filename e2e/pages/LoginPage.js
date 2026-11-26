import { BasePage } from './BasePage.js';

export class LoginPage extends BasePage {
  constructor(page) {
    super(page);
    this.usernameInput = page.getByLabel('Usuário');
    this.passwordInput = page.getByLabel('Senha');
    this.submitButton = page.getByRole('button', { name: 'Entrar' });
  }

  async goto() {
    await this.page.goto('/login');
  }

  async login(username, password) {
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.submitButton.click();
  }
}
