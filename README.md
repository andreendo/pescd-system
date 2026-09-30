# PESCD System

Este é um fork do repositório do sistema PESCD: [lucasmd30/pescd-system](https://github.com/lucasmd30/pescd-system/), originalmente desenvolvido no contexto da disciplina de Desenvolvimento de Software para Web 1 (2026/1 - prof. André T. Endo). 

As contribuições originais de cada participante são listadas em [`CONTRIBUICOES-ORIGINAIS.md`](CONTRIBUICOES-ORIGINAIS.md).

Os requisitos, user stories e regras de negócio estão identificados em [`REQUISITOS.md`](REQUISITOS.md).

## Executando (infraestrutura)

O sistema depende de um PostgreSQL e de um SeaweedFS (armazenamento dos PDFs).
Ambos sobem via Docker Compose:

```bash
docker compose up -d      # sobe postgres (5432) e seaweedfs (9333/8081/8888)
./mvnw spring-boot:run    # sobe a API Spring Boot em http://localhost:8082 (+ Swagger em /swagger-ui.html)
```

### Front-end React

O sistema agora tem um front-end em React (Vite) que consome a REST API,
substituindo as telas Thymeleaf. Veja [`frontend/README.md`](frontend/README.md):

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173
```

### Armazenamento de arquivos no SeaweedFS

Os arquivos PDF (plano de trabalho, documentação e relatório) **não são mais
gravados como binário (`bytea`) no PostgreSQL**. Eles passam a ser enviados ao
[SeaweedFS](https://github.com/seaweedfs/seaweedfs#quick-start); no banco
relacional guardamos apenas o identificador retornado (o *fid*, ex.:
`3,01637037d6`) na coluna `file_fid` das tabelas `work_plans`, `documentations`
e `reports`. O acesso é encapsulado em `SeaweedFsStorageService`
(`/dir/assign` → upload no volume → `/dir/lookup` → leitura).

> **Migração de banco existente:** como o `spring.jpa.hibernate.ddl-auto=update`
> apenas *adiciona* a coluna `file_fid` e não remove a antiga `file_content`
> (que era `NOT NULL`), um banco já populado antes desta mudança precisa
> descartar a coluna antiga (ou ser recriado). Em bancos de desenvolvimento,
> basta recriar o schema; alternativamente:
>
> ```sql
> ALTER TABLE work_plans     DROP COLUMN file_content;
> ALTER TABLE documentations DROP COLUMN file_content;
> ALTER TABLE reports        DROP COLUMN file_content;
> ```
