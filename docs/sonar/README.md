# Análise do SonarCloud (O-4)

Análise rodada com `mvnw verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar` num projeto manual do SonarCloud (`es2-projetopratico_pescd-system`, perfil de regras *Sonar way core*).

| Análise | Commit | Vulnerabilidades |
|---|---|---|
| 26/11/2026 09:35 | `4b5e207` (antes da correção) | 1 — `java:S4502`, proteção CSRF desligada para `/api/**` no `SecurityConfig` |
| 26/11/2026 10:39 | `fa3ae1e` (com a correção do #24) | 0 |

- `sonar-1-resumo-0-vulnerabilidades.jpg`: resumo depois da correção (0 issues, notas A, quality gate aprovado).
- `sonar-2-csrf-s4502-fixed.jpg`: a issue `java:S4502` com status *Fixed*.
- `sonar-3-atividade-1-para-0.jpg`: histórico das duas análises, com as issues indo de 1 para 0.
