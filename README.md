# TaskFlow — Desafio SDD

[![CI](https://github.com/tiagofvalerio/taskflow-sdd/actions/workflows/ci.yml/badge.svg)](https://github.com/tiagofvalerio/taskflow-sdd/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=tiagofvalerio_taskflow-sdd&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=tiagofvalerio_taskflow-sdd)

API de gestão de projetos e tarefas, entregue como exercício de
**Specification-Driven Development (SDD)**: implementação Quarkus, contrato
OpenAPI como fonte única da verdade.

## 1. Visão geral

O fluxo seguido é spec-first, do início ao fim:

1. `spec/openapi.yaml` é escrito e revisado **antes** de qualquer linha de
   implementação — inclusive antes da modelagem de domínio.
2. Toda regra de negócio (as 6 invariantes do domínio — ver `docs/decisoes.md`
   §2) é primeiro uma decisão de contrato: qual código de erro, qual `type`
   RFC 7807, qual payload. Só depois vira código.
3. Onde a implementação revelou uma lacuna da spec (ex.: forma canônica de
   UUID, mapeamento de corpo não-parseável — `docs/decisoes.md` §3 e §12), a
   lacuna foi promovida a texto normativo na spec, com revisão adversarial,
   nunca deixada como convenção implícita dos testes.
4. O código conforma à spec; a spec nunca conforma ao código. Divergência é
   bug, não é "spec desatualizada".

Esse é o compromisso não-negociável do projeto (ver `CLAUDE.md`): **a spec
manda, sempre**.

## 2. IA dirigida com intenção — Claude Code como parte da metodologia

Este projeto usa o Claude Code (CLI da Anthropic) como ferramenta dirigida
dentro do fluxo SDD — não como autopilota. A IA produz; toda produção passa
por revisão explícita antes de ser aceita. A pasta `.claude/`, versionada
neste repositório, é evidência concreta de como essa direção foi estruturada:

- **Hook `UserPromptSubmit`** (`.claude/hooks/log-prompt.sh`) — captura
  automaticamente todo prompt enviado em `ai/prompt-log.md`. A documentação
  de uso de IA nasce do próprio fluxo de trabalho, não é reconstruída de
  memória ao final do projeto.
- **Skill `/log-ai`** (`.claude/skills/log-ai/`) — ritual de um comando para
  curar, a partir do log bruto, uma entrada estruturada em `ai/prompts.md`
  (contexto, prompt resumido, o que foi produzido, resultado da aceitação) no
  momento em que a decisão acontece.
- **Skill `/revisar`** (`.claude/skills/revisar/`) — ritual equivalente para
  `ai/revisoes.md`, o documento mais importante do desafio: todo caso em que
  a saída da IA foi corrigida, rejeitada ou se mostrou ingênua, registrado
  com honestidade (o que foi sugerido, por que estava errado, a correção, a
  lição).
- **Skill `/sdd-check`** (`.claude/skills/sdd-check/`) — verificação
  sistemática de aderência da implementação a `spec/openapi.yaml` (endpoints,
  schemas, códigos de erro, status codes), rodada antes de cada commit de
  implementação. Só reporta — nunca corrige sozinha.
- **Subagentes com contexto isolado e função adversarial**:
  - `spec-reviewer` (`.claude/agents/spec-reviewer.md`) — revisor hostil do
    contrato OpenAPI, procura lacunas, inconsistências e casos de erro
    faltando antes da implementação começar.
  - `domain-guardian` (`.claude/agents/domain-guardian.md`) — audita pureza
    hexagonal (zero import de framework em `domain/`/`application/`) e que
    cada uma das 6 regras de negócio vive na entidade de domínio, nunca em
    controller.
  - `contract-tester` (`.claude/agents/contract-tester.md`) — audita a
    matriz de cobertura de testes de contrato contra a spec, não contra o
    comportamento atual do código.

Os findings desses subagentes, aceitos ou rejeitados, alimentam diretamente
`ai/revisoes.md`. O detalhamento da estratégia de delegação — o que é seguro
delegar à IA e o que fica comigo — está em `ai/skills.md`.

Tratamento completo de cada mecanismo (o quê, por quê, e o que produziu na
prática, com exemplos concretos) em
**[`docs/ferramental-ia.md`](docs/ferramental-ia.md)**.

## 3. Rodando o projeto

### Pré-requisitos

- **JDK 25** (`maven.compiler.release=25` no `pom.xml`).
- **Docker** — necessário para os testes de integração (Testcontainers sobe
  PostgreSQL real) e para os Dev Services do Quarkus em modo `dev`/`test`.

### Rodar a aplicação

```shell
cd quarkus-impl
./mvnw quarkus:dev
```

Modo dev sobe um PostgreSQL descartável automaticamente via Dev Services —
não é preciso configurar banco à mão.

### Rodar os testes

```shell
cd quarkus-impl
./mvnw verify
```

Cobre unitários de domínio/aplicação, integração de adapters contra
PostgreSQL real (Testcontainers) e testes de contrato (RestAssured +
validação de schema contra `spec/openapi.yaml`).

### Rodar mutation testing (PIT)

```shell
cd quarkus-impl
./mvnw test-compile pitest:mutationCoverage
```

Escopo: domínio e aplicação. Resultado da entrega: 100% de kill rate — prova
que os testes unitários efetivamente matam mutantes, não apenas alcançam
cobertura de linha sem asserção real (`docs/decisoes.md` §8).

### Executando via Docker

Build (gera o jar antes; a imagem não builda a partir do source):

```shell
cd quarkus-impl
./mvnw package
docker build -f src/main/docker/Dockerfile.jvm -t taskflow-quarkus .
```

Run — o container não sobe banco embutido (Dev Services só vale para
`dev`/`test`); é preciso um PostgreSQL externo, apontado via env vars:

```shell
docker run -i --rm -p 8080:8080 \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/taskflow \
  -e QUARKUS_DATASOURCE_USERNAME=taskflow \
  -e QUARKUS_DATASOURCE_PASSWORD=taskflow \
  taskflow-quarkus
```

O Postgres pode ser qualquer instância alcançável (instalação local, ou
`docker run -p 5432:5432 postgres:16`); as migrations Flyway rodam
automaticamente na subida contra o banco apontado.

## 4. Decisões de escopo

Entrega **single-stack**: apenas a implementação Quarkus. Uma segunda
implementação (Rails), planejada como prova de que o contrato é
implementável de forma independente por dois ecossistemas, foi cortada por
trade-off de tempo — profundidade numa stack única (mutation testing com
100% de kill rate, suíte de contrato com matriz de cobertura auditada,
lacunas de spec descobertas pela implementação e promovidas a texto
normativo) em vez de duas implementações rasas. O corte remove a
*demonstração* da independência de implementação, não a propriedade em si —
o contrato em `spec/openapi.yaml` permanece implementável por qualquer stack,
sem engenharia reversa do código Java.

Não há deploy hospedado: a avaliação é local, com os comandos da seção 3.

Racional completo: **`docs/decisoes.md`, item 10**.

## 5. Mapa de links

| O quê | Onde |
|---|---|
| Contrato (fonte única da verdade) | [`spec/openapi.yaml`](spec/openapi.yaml) |
| Decisões de design e trade-offs | [`docs/decisoes.md`](docs/decisoes.md) |
| Estratégia de delegação à IA | [`ai/skills.md`](ai/skills.md) |
| Ferramental de IA em detalhe (hook, skills, subagentes, plan mode) | [`docs/ferramental-ia.md`](docs/ferramental-ia.md) |
| README do módulo Quarkus (build/native/Docker específicos do módulo) | [`quarkus-impl/README.md`](quarkus-impl/README.md) |
| Prompts curados (o que foi pedido e produzido) | [`ai/prompts.md`](ai/prompts.md) |
| Revisões e correções sobre saída da IA | [`ai/revisoes.md`](ai/revisoes.md) |
| Log automático de prompts (bruto) | [`ai/prompt-log.md`](ai/prompt-log.md) |
| Regras de metodologia e arquitetura | [`CLAUDE.md`](CLAUDE.md) |
