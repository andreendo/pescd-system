# Uso de Inteligência Artificial

Este documento registra quais ferramentas e agentes de IA o grupo usou no
projeto e de que forma foram usados.

## Ferramentas utilizadas

| Ferramenta | Tipo | Modelo |
| --- | --- | --- |
| [Claude Code](https://claude.com/claude-code) (app desktop) | Agente de programação | Claude Opus 5.5 (Anthropic) |

## Como a IA foi utilizada

### Entrega preliminar

O Claude Code foi usado como agente, com acesso ao repositório clonado e à
conta do GitHub de um integrante, para apoiar as tarefas da lista de issues:

| Issue | Uso da IA |
| --- | --- |
| #A / Fork | Nenhum. O fork foi criado manualmente pelo grupo. |
| #B | Redigiu o texto das issues A–G e as criou no repositório pela API do GitHub. |
| #C | Gerou o `ES2-README.MD` com um breve resumo do projeto, a partir de informações prévias fornecidas pelo grupo (nomes, perfis do GitHub e documentos existentes no repositório). |
| #D | Nenhum. Os colaboradores foram adicionados manualmente pelo dono do repositório. |
| #E | Gerou a primeira versão deste arquivo, que passou por alterações em versões subsequentes. |
| #F | Executou o build (backend e front-end) para conferir os passos e escreveu o roteiro do vídeo. A gravação foi feita pelo grupo. |
| #G | Subiu o sistema, navegou pelas telas e escreveu o roteiro da demo. A gravação foi feita pelo grupo. |

### Entrega final

Cada integrante descreve abaixo como usou ferramentas de IA nas suas tarefas
(bullets H a Q).

#### Anderson Soares

_A preencher pelo integrante._

#### Caio Miyaji

_A preencher pelo integrante._

#### Gustavo Bragaia

_A preencher pelo integrante._

#### Lucas Martinez Domingues

Usei o Claude Code (Claude Opus 5.5) como agente, com acesso ao repositório
clonado, à minha conta do GitHub e a um navegador, nas seguintes frentes:

- **Planejamento das tarefas do grupo.** Usei a LLM para dividir os bullets
  H a Q entre os quatro integrantes de forma estratégica, equilibrando a carga
  para que ninguém ficasse sobrecarregado. Como eu apresento antes do grupo,
  fiquei com os "fundamentos" de que os outros dependem: JaCoCo (K), estrutura
  dos testes E2E (I), status novos usados pelas mudanças e a pipeline de CI (L).
- **Encontrar vulnerabilidades e bugs e sugerir mudanças.** A LLM interpretou
  a análise do SonarCloud (vulnerabilidade `java:S4502`, CSRF desligado na API),
  comparou o código com as regras do `REQUISITOS.md` para encontrar o bug do
  reencerramento de oferta (RNG-4) e sugeriu as alterações da Mud1, do O, do P
  e do Q.
- **Código feito em conjunto.** As alterações foram desenvolvidas em conjunto:
  a LLM propunha o código e os testes, e eu revisava cada diff, pedia ajustes e
  só então aprovava o commit. Todos os testes foram rodados localmente e no CI
  antes de cada PR.
- **Medição da cobertura.** A cobertura foi medida com o JaCoCo 0.8.16, que
  configurei no `pom.xml` (K): ele instrumenta o código enquanto os testes
  rodam e, na fase `verify` (`./mvnw verify`), gera o relatório em
  `target/site/jacoco/`. O CI publica o mesmo relatório como artefato
  (`cobertura-jacoco`).
- **Evidências e documentação.** Me apoiei na LLM para gerar as evidências:
  os testes manuais de PS.* (`docs/testes-manuais/ps.md`) foram executados com
  o agente operando o navegador, a partir dos casos definidos, e tirando os
  prints; os prints da análise do SonarCloud (`docs/sonar/`) também foram
  tirados pelo agente. Ela também ajudou a redigir a documentação: as seções do
  `README.md`, as descrições das issues e dos PRs, as issues de bug e os
  relatórios de estudo que usei para me preparar para a apresentação (estes
  fora do repositório).
