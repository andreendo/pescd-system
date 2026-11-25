export class BasePage {
  constructor(page) {
    this.page = page;
    this.loggedUser = page.locator('.user-chip');
    this.logoutButton = page.getByRole('button', { name: 'Sair' });
    this.errorMessage = page.locator('.alert-error');
    this.successMessage = page.locator('.alert-success');
  }

  navLink(name) {
    return this.page.getByRole('navigation').getByRole('link', { name });
  }

  async logout() {
    await this.logoutButton.click();
  }
}
