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
| Scaffolding e boilerplate (Quarkus, Dockerfiles, GitHub Actions) | Baixo risco de decisão embutida; alto custo manual; sempre validado rodando localmente. |
| Geração de testes de contrato a partir da spec | Os cenários vêm da spec (fonte da verdade), não do código — o subagente `contract-tester` audita a matriz de cobertura. |
| Investigação de versões e compatibilidade de bibliotecas | Sempre com instrução explícita de verificar em fontes oficiais antes de afirmar — sugestões desatualizadas da IA capturadas viram entradas em `ai/revisoes.md`. |

## O que NÃO delego (decisões que ficam comigo)

- Decisões de design da spec: semântica de PATCH, 400 vs 422 (incluindo o caso
  do `completedAt` enviado manualmente), ausência deliberada de paginação.
- Onde cada regra de negócio vive na arquitetura (entidade de domínio, nunca
  em resource/controller — ver `docs/decisoes.md` §2).
- Aceitar ou rejeitar cada finding dos revisores automáticos (`spec-reviewer`,
  `domain-guardian`, SonarQube) e dos mutantes sobreviventes (PIT).
- A mensagem final de cada commit e o momento de cada commit (a história do git
  precisa contar o fluxo SDD: spec antes de código).

## Ferramental construído para dirigir a IA

A pasta `.claude/` (versionada neste repositório) é parte da resposta à pergunta
"você sabe dirigir a IA com intenção?". Tratamento completo — o quê, por quê,
e exemplos concretos do que cada mecanismo produziu — está em
[`docs/ferramental-ia.md`](../docs/ferramental-ia.md); resumo abaixo:

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

## Observações de processo — o que mudou em relação ao plano

- **Corte de escopo: a implementação Rails foi cortada** (ver
  `docs/decisoes.md` §10). Com isso, duas áreas de delegação planejadas no
  Dia 0 deixaram de existir: a tradução do design de domínio entre stacks
  (Java → Ruby) e a mentoria em Ruby/Rails. Não foi um recuo por
  desconfiança da IA — foi trade-off de tempo vs. profundidade: o tempo foi
  reinvestido em mutation testing (PIT, 100% kill rate), na suíte de
  contrato validada por schema e nas emendas de spec com revisão
  adversarial. As linhas removidas da tabela acima permanecem no histórico
  git deste arquivo, que é parte da evidência de processo.

## Retrospectiva — Dia 4

O plano do Dia 0 previa delegar principalmente geração (spec, scaffolding,
testes). O que de fato sustentou o projeto foi outra coisa: a delegação que
importou não foi "gerar código", foi "gerar código e depois submetê-lo a uma
segunda passada adversarial — agente separado, ferramenta de fuzzing, ou
investigação formal — antes de aceitar". Quase todo achado forte do projeto
(`ai/revisoes.md`) veio dessa segunda passada, não da geração em si.

**O que mudou em relação ao plano**:
- Dual-stack (Quarkus + Rails) cortado por trade-off de tempo — a IA nunca
  questionou o escopo sozinha, mesmo entranhando o plano Rails em todo
  artefato que gerou (`ai/revisoes.md`, entrada 16). Vigilância de escopo
  não foi delegável; teve que vir de mim, e tarde.
- Não planejado no Dia 0, mas virou rotina: pedir à IA que verifique a
  própria produção contra fonte primária antes de aplicar (varredura de
  codepoints em dois motores de regex, leitura de código-fonte de
  framework/CLI em vez de doc) — nasceu de erros específicos, não de
  intenção original.

**Momentos de maior valor de revisão** (a IA gerou algo plausível; revisão —
minha, de agente, ou de ferramenta de fuzzing — achou o defeito real):
- **Invariante retroativo não percebido** (`ai/revisoes.md`, entrada 2): a
  regra de negócio 6 inteira só existe porque o `spec-reviewer` perguntou
  "que outros caminhos levam a esse estado?", pergunta que eu não tinha
  feito sozinha.
- **A própria IA divergindo do que disse ter sincronizado** (entrada 4): ela
  relatou "sincronizei spec e CLAUDE.md" numa entrada e o arquivo continuava
  desatualizado na rodada seguinte — só um diff comparado, não o resumo da
  IA, expôs isso.
- **Coerção silenciosa do Jackson e lacunas prosa-vs-schema** (entradas 12,
  17, 18): três variações do mesmo ponto cego — a IA testa exaustivamente o
  que ela mesma escreveu, nunca o espaço de entradas que o contrato promete
  cobrir. Só ferramenta mecânica (`/sdd-check`, Schemathesis) tem esse
  alcance; revisão de prosa, humana ou de agente, estruturalmente não vê
  "o que nunca foi escrito".
- **Drift na direção contrária** (entrada 13): a mesma cegueira, invertida —
  testes exigindo *mais* do que a spec promete (igualdade exata de prosa
  ilustrativa), achado só porque o `contract-tester` comparou contra a spec,
  não contra os próprios testes.
- **Uma alegação de pesquisa que virou config quebrada** (entrada 20): a IA
  citou `--force-maven-cli`, uma flag que nunca existiu, a partir de síntese
  de busca — só foi pega porque recusei aceitar "deve ser isso" numa segunda
  tentativa e exigi investigação com fonte primária.
- **O gate de segurança pago em duas rodadas de infraestrutura** (entrada
  19): destravar o Snyk (entrada 20) não foi trabalho perdido — foi a
  pré-condição para achar uma CVE alta real (`SNYK-JAVA-ORGPOSTGRESQL-17874248`)
  transitiva de um BOM "confiável". Um gate verde por erro de configuração
  é pior que um gate vermelho: cria falsa sensação de cobertura.

Lição que atravessa todas: cobertura (de linha, de rodada de revisão, de
convicção da IA) não é o mesmo que verificação. O padrão que funcionou foi
sempre o mesmo — gerar, depois perguntar "isso foi verificado contra a
fonte certa, ou só parece certo?" — repetido em domínios diferentes (spec,
código, regex, config de CI) com o mesmo resultado.