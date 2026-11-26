# Testes E2E

Testes de ponta a ponta do PESCD com [Playwright](https://playwright.dev/), usando o padrão PageObjects.

## Como rodar

Com o PostgreSQL e o SeaweedFS de pé (`docker compose up -d` na raiz do projeto):

```bash
cd e2e
npm install
npx playwright install chromium   # só na primeira vez
npm test
```

O Playwright sobe a API (`./mvnw spring-boot:run`) e o front (`npm run dev` em `frontend/`) se eles ainda não estiverem rodando. Se já estiverem, ele reaproveita os servidores.

Outros comandos:

```bash
npm run test:ui                     # modo interativo
npx playwright test tests/u01.spec.js   # só um arquivo
npm run report                      # abre o relatório HTML da última execução
```

Quando um teste falha, o relatório guarda o screenshot e o *trace* da execução.

## Estrutura

```
e2e/
  playwright.config.js   # baseURL, servidores, relatório
  pages/                 # um PageObject por tela
  fixtures/              # usuários do seed e fixtures de login
  tests/                 # um arquivo por user story (u01.spec.js, al02.spec.js...)
```

- Os seletores ficam **só nos PageObjects**; os testes chamam os métodos deles.
- Para entrar no sistema, use a fixture `loginAs('<usuário>')` de `fixtures/test.js`. Os usuários disponíveis estão em `fixtures/users.js`.
- O nome de cada teste começa pela story: `test('AL.02 - aluno envia plano de trabalho', ...)`.

## Dados

Os testes usam os dados criados pelo `DataInitializer`. Ao reiniciar a API, esses registros voltam ao estado inicial, então testes que mudam status (aprovar plano, aprovar relatório etc.) precisam de uma API recém-iniciada ou devem criar os próprios dados.
