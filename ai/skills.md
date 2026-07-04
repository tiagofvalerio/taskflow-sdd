# ai/skills.md — Habilidades e áreas delegadas à IA

> Documento vivo: criado no Dia 0 com o plano de delegação e atualizado ao final
> do projeto para refletir o que de fato aconteceu. O diff entre as duas versões
> faz parte da evidência de uso crítico da IA.

## Estratégia geral

Uso o Claude Code (CLI da Anthropic) como ferramenta dirigida dentro de um fluxo
Specification-Driven Development. A IA não decide: ela produz, e toda produção
passa por revisão humana antes de ser aceita. O registro do que foi corrigido ou
rejeitado está em `ai/revisoes.md`; os prompts relevantes, em `ai/prompts.md`
(curados a partir de um log automático, `ai/prompt-log.md`).

## O que delego à IA (e por quê é seguro delegar)

| Área | Por que delego |
|---|---|
| Rascunho do contrato OpenAPI 3.0 | A spec passa por revisão adversarial (subagente `spec-reviewer`,  uma técnica em que dois sistemas ou agentes atuam em posições opostas para testar a robustez de um projeto, código ou ideia. Um agente propõe a solução, enquanto o outro assume um papel crítico para buscar vulnerabilidades, fraquezas e casos extremos, garantindo um resultado muito mais seguro e confiável.) e por minha leitura integral antes de virar fonte da verdade. |
| Scaffolding e boilerplate (Quarkus, Rails, Dockerfiles, GitHub Actions) | Baixo risco de decisão embutida; alto custo manual; sempre validado rodando localmente. |
| Geração de testes de contrato a partir da spec | Os cenários vêm da spec (fonte da verdade), não do código — o subagente `contract-tester` audita a matriz de cobertura. |
| Tradução do design de domínio entre stacks (Java → Ruby) | As 5 regras de negócio já foram definidas e testadas; a tradução é mecânica e verificada pelo subagente `domain-guardian` e pelas suítes de teste. |
| Mentoria em Ruby/Rails | Sou novo em Ruby. Delego a explicação de idiomas (bang methods, callbacks, concerns), mas não a responsabilidade de entendê-los: mantenho um guia de estudo e reviso cada explicação. |
| Investigação de versões e compatibilidade de bibliotecas | Sempre com instrução explícita de verificar em fontes oficiais antes de afirmar — sugestões desatualizadas da IA capturadas viram entradas em `ai/revisoes.md`. |

## O que NÃO delego (decisões que ficam comigo)

- Decisões de design da spec: semântica de PATCH, 400 vs 422 (incluindo o caso
  do `completedAt` enviado manualmente), ausência deliberada de paginação.
- Onde cada regra de negócio vive na arquitetura (entidade de domínio no Quarkus;
  modelo ActiveRecord no Rails) e a decisão de NÃO forçar hexagonal no Rails.
- Aceitar ou rejeitar cada finding dos revisores automáticos (`spec-reviewer`,
  `domain-guardian`, SonarQube) e dos mutantes sobreviventes (PIT).
- A mensagem final de cada commit e o momento de cada commit (a história do git
  precisa contar o fluxo SDD: spec antes de código).

## Ferramental construído para dirigir a IA

A pasta `.claude/` (versionada neste repositório) é parte da resposta à pergunta
"você sabe dirigir a IA com intenção?":

- **Hook `UserPromptSubmit`** — registra automaticamente todo prompt enviado em
  `ai/prompt-log.md`. A documentação de uso de IA é gerada PELO fluxo de
  trabalho, não reconstruída de memória ao final.
- **Skills `/log-ai` e `/revisar`** — rituais de um comando para curar entradas
  em `ai/prompts.md` e `ai/revisoes.md` no momento em que a revisão acontece.
- **Skill `/sdd-check`** — verificação sistemática de aderência da implementação
  à spec, executada antes de cada commit de implementação.
- **Subagentes `spec-reviewer`, `domain-guardian` e `contract-tester`** — revisão
  adversarial da própria saída da IA, com contexto isolado. Os findings deles
  que eu acato ou rejeito alimentam diretamente o `ai/revisoes.md`.
- **`CLAUDE.md`** — regras de metodologia (spec é a fonte da verdade), de
  arquitetura (regras de negócio nunca em controllers) e de disciplina de
  documentação, carregadas em toda sessão.

<!-- ATUALIZAR NO DIA 4: o que mudou em relação ao plano? Algo que planejei
     delegar e recuei? Algo que passei a delegar? Referenciar entradas
     específicas do ai/revisoes.md. -->