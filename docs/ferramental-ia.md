# Ferramental de IA — dirigindo o Claude Code com intenção

Este documento detalha o que está na pasta `.claude/` (versionada neste
repositório) e por que cada peça existe. Não é uma lista de features do Claude
Code — é o registro de que decisões de engenharia foram tomadas para que a IA
fosse dirigida, revisada e auditada, em vez de só solicitada. O tratamento
resumido está no README (§2); este documento é a versão completa, com exemplos
reais do que cada mecanismo produziu neste projeto. O caso a caso do que foi
corrigido ou rejeitado está em `ai/revisoes.md` — este documento explica o
ferramental que tornou esses achados possíveis, não repete o conteúdo deles.

---

## 1. Hook `UserPromptSubmit` — captura automática de prompts

**O que é.** `.claude/settings.json` registra um hook `UserPromptSubmit` que
roda `.claude/hooks/log-prompt.sh` a cada prompt enviado à sessão. O script lê
o JSON do evento, extrai o campo `prompt` e acrescenta ao final de
`ai/prompt-log.md` um bloco com timestamp:

```bash
LOG_FILE="$CLAUDE_PROJECT_DIR/ai/prompt-log.md"
PROMPT=$(cat | python3 -c "import sys, json; print(json.load(sys.stdin).get('prompt',''))")
{
  echo ""
  echo "---"
  echo "**$(date '+%Y-%m-%d %H:%M')**"
  echo ""
  echo "$PROMPT"
} >> "$LOG_FILE"
```

**Por que foi construído.** O desafio exige documentação de uso de IA. A
alternativa óbvia — reconstruir de memória, ao final, "quais prompts usei" —
é exatamente o tipo de registro que não resiste a verificação: seletivo,
otimista, editado pelo viés de quem quer parecer metódico depois do fato. O
hook resolve isso deslocando a captura para fora do controle da sessão: todo
prompt entra no log no instante em que é enviado, disparado pelo runtime do
Claude Code, não por uma decisão minha de "isso vale a pena registrar". O log
bruto é material de primeira ordem — as skills da seção 2 curam a partir dele,
mas a curadoria nunca pode inventar o que não está lá.

**O que produziu na prática.** `ai/prompt-log.md` tem mais de 2100 linhas e
~120 entradas timestamped cobrindo toda a sessão, do primeiro prompt de
especificação (2026-07-04) até os fixes de pipeline mais recentes
(2026-07-08). É a fonte a partir da qual `ai/prompts.md` é curado (25+
entradas até o momento) — cada entrada de `prompts.md` cita explicitamente
que resume "o prompt real, cujo texto bruto está em `ai/prompt-log.md`".

---

## 2. Skills `/log-ai`, `/revisar`, `/sdd-check` — rituais de um comando

**O que são.** Três skills em `.claude/skills/`, cada uma um arquivo
`SKILL.md` com `disable-model-invocation: true` (só disparam quando eu digito
o comando, nunca por iniciativa própria da IA):

- **`/log-ai <contexto>`** — acrescenta entrada estruturada a `ai/prompts.md`
  (Contexto / Prompt resumido / O que a IA produziu / Resultado).
- **`/revisar <problema>`** — acrescenta entrada estruturada a
  `ai/revisoes.md` (Data-Fase / O que a IA sugeriu / Problema identificado /
  Correção aplicada / Lição).
- **`/sdd-check`** — compara sistematicamente `quarkus-impl` contra
  `spec/openapi.yaml` (todo path+método, schemas, error responses, status
  codes, query params) e reporta uma tabela de conformidade. Report-only por
  instrução explícita da skill: "Do NOT fix anything — report only."

**Por que foram construídas.** Documentação de qualidade tem um custo de
fricção, e fricção alta significa que ela só acontece no fim, quando a
memória já degradou e a pressão de prazo já comeu o detalhe. As três skills
existem para baixar esse custo a "um comando", tornando barato registrar no
*momento em que a decisão acontece* — que é a única hora em que o racional
completo (o que foi tentado, por que falhou, o que se decidiu) ainda está
disponível. `/sdd-check`, especificamente, resolve um problema diferente: a
promessa central de SDD ("o código conforma à spec") não pode depender de eu
lembrar de verificar manualmente a cada mudança — precisa ser um comando
barato o bastante para rodar antes de cada commit de implementação.

**O que produziram na prática.**
- `/log-ai` e `/revisar` produziram os dois documentos centrais desta
  entrega: `ai/prompts.md` (25+ entradas curadas) e `ai/revisoes.md` (20
  entradas, reordenadas por força do achado — ver nota no topo daquele
  arquivo).
- `/sdd-check`, rodado após a implementação dos adapters REST, achou um
  drift de contrato invisível a revisão de código: os DTOs de request
  tipavam todos os campos como `String`, mas a coerção escalar default do
  Jackson aceitava silenciosamente `{"name": 123}` como `"123"`, retornando
  `201` onde a spec exige `400`. Nem a suíte de testes (que só testava tipos
  corretos) nem a auditoria do `domain-guardian` (que só lê código, não
  comportamento de default de framework) tinham pegado — só a comparação
  sistemática spec×API viva encontrou (`ai/revisoes.md`, entrada "Coerção
  escalar do Jackson"). Corrigido com um `ObjectMapperCustomizer` global;
  segunda rodada do `/sdd-check` confirmou zero drift.

---

## 3. Subagentes `spec-reviewer`, `domain-guardian`, `contract-tester` — revisão adversarial em contexto isolado

**O que são.** Três agentes definidos em `.claude/agents/*.md`, cada um com
ferramentas restritas (só leitura — `Read`, `Grep`, `Glob`, e `Bash` só para o
`contract-tester`) e escopo declarado:

- **`spec-reviewer`** — "revisor hostil de design de API"; audita
  `spec/openapi.yaml` procurando lacunas, inconsistências e casos de erro
  faltando. Não corrige, só reporta, com severidade (bloqueador/maior/menor).
- **`domain-guardian`** — audita pureza hexagonal em `quarkus-impl`: zero
  import de framework em `domain/`/`application/`, sem setters públicos em
  campos de invariante, cada uma das 6 regras de negócio localizada num
  método de domínio nomeado por intenção.
- **`contract-tester`** — audita se a suíte de testes PROVA conformidade com
  a spec: monta uma matriz de cobertura (happy paths, os 6 `422`s de regra de
  negócio, 404s, 400s) e reporta cenários faltando como checklist.

**Por que foram construídos.** Pedir para a mesma sessão que gerou um
artefato também revisá-lo tem um viés estrutural: a IA (como eu) tende a
reler a própria intenção, não o texto literal — o que ela quis dizer, não
necessariamente o que escreveu. Rodar um subagente separado, com prompt e
contexto isolados do processo de geração, força uma segunda leitura genuína:
o agente não sabe "o que eu quis dizer", só lê o artefato como ele está. Os
findings desses três agentes alimentam diretamente `ai/revisoes.md` — a
aceitação ou rejeição de cada achado é decisão minha, documentada.

**O que produziram na prática — exemplos concretos:**

- **`spec-reviewer`, achado que criou a regra de negócio 6.** Na primeira
  rodada de revisão da spec, o agente perguntou "que outros caminhos levam
  ao estado que a regra 1 deveria proteger?" — e achou que nada impedia uma
  tarefa `pending` de um projeto já arquivado de avançar para `in_progress`
  depois do arquivamento, contradizendo a intenção da regra 1. Isso não
  existia como pergunta na minha cabeça: eu tinha pensado a regra 1 só a
  partir do endpoint onde ela é óbvia (arquivar), não a partir de todos os
  estados que ela deveria proteger. A resposta virou a regra de negócio 6
  (status de tarefa travado enquanto o projeto está arquivado) — uma
  invariante inteira que não existiria sem essa revisão adversarial
  dedicada. A spec passou por **6 rodadas** desse mesmo processo (cada uma
  achando uma classe de problema diferente: contradições que minhas próprias
  correções introduziam, colisões de regra dentro do mesmo estágio de
  validação, ordenação de listagem nunca especificada, timezone de
  datetime) — o critério de parada evoluiu de "zero achados" (que talvez
  nunca aconteça) para "achados restantes são nitpick de doc, não mudança de
  comportamento observável".

- **`domain-guardian`, pureza hexagonal e uma lacuna prospectiva.** A
  primeira auditoria da camada de domínio confirmou zero import de
  `jakarta.*`/`io.quarkus.*`/`org.hibernate.*`/`com.fasterxml.*`, zero
  setter público em campo de invariante, e as 6 regras de negócio como
  métodos de intenção (`Project.archive()`, `Task.startProgress()`,
  `Task.complete()`, `Task.changeStatusTo()`). Mas apontou algo que a
  geração não tinha visto sozinha: `quarkus-hibernate-orm-panache` e o
  driver JDBC já estavam no classpath de compilação por causa dos adapters
  futuros — e Maven não escopa dependência por pacote, então um
  `import jakarta.persistence.*` acidental dentro do domínio **compilaria
  sem erro**. A regra de pureza hexagonal, requisito não-negociável do
  `CLAUDE.md`, dependia só de disciplina manual. Aceito com timing
  antecipado: ArchUnit foi adicionado **antes** da fase de adapters, não
  depois, transformando a regra arquitetural em quebra de build — validado
  negativamente com uma classe-canário anotada `@Entity` dentro do domínio,
  que quebrou o build como esperado antes de ser removida.

- **`contract-tester`, sobre-especificação na direção contrária.** Ao montar
  a matriz de cobertura, o agente notou que os próprios testes de taxonomia
  de erro asseriam igualdade exata em strings de `detail` copiadas dos
  `examples` da spec — mas `examples` em OpenAPI são ilustrativos, não
  normativos. A exigência forçaria qualquer segunda implementação a
  reproduzir as frases em português caractere a caractere, uma divergência
  que o contrato nunca prometeu. É o espelho do padrão mais comum (testes
  exigindo *menos* que o contrato) — aqui os testes exigiam *mais*. Resultado:
  política de asserção explícita (normativo = igualdade exata; ilustrativo =
  token de conteúdo via `containsString`), documentada no javadoc da suíte.

---

## 4. `CLAUDE.md` — constituição carregada em toda sessão

**O que é.** O arquivo na raiz do repositório, carregado automaticamente no
início de toda sessão do Claude Code neste diretório. Não é documentação
passiva — é instrução ativa que o próprio texto marca como tal: "IMPORTANT:
These instructions OVERRIDE any default behavior and you MUST follow them
exactly as written."

**Por que foi construído.** Metodologia (spec é fonte única da verdade,
código nunca implementa comportamento não definido na spec), arquitetura
(regras de negócio só no domínio, zero import de framework), e disciplina de
documentação (lembrar `/log-ai` após geração significativa, `/revisar` após
qualquer correção) não podem depender de eu repetir as mesmas instruções em
todo prompt, ao longo de dezenas de sessões e vários dias. `CLAUDE.md`
move essas regras de "coisa que eu tenho que lembrar de pedir" para "coisa
que já está carregada antes do primeiro prompt".

**O que produziu na prática.** As seis regras de negócio numeradas em
`CLAUDE.md` são a mesma numeração usada em toda a spec, no código de domínio
e nos subagentes — quando um deles divergiu (ver `ai/revisoes.md`, entrada
"CLAUDE.md ficou errado"), a divergência foi achada justamente porque
`CLAUDE.md` é referência ativa, comparada contra a cada rodada, não
documento estático. A disciplina de conventional commits (`feat:`, `fix:`,
`ci:` — usada em toda a história deste repositório) e a regra "spec commita
antes da implementação" também vêm de lá, e são verificáveis diretamente no
`git log`.

---

## 5. Plan mode como gate de decisão

**O que é.** Antes de gerar código para qualquer decisão de design com mais
de uma resposta razoável, a instrução era propor as opções com trade-offs e
esperar aprovação — não implementar a primeira solução plausível. Não é uma
feature específica do Claude Code sendo "ligada"; é um padrão de prompt
aplicado deliberadamente nos pontos de decisão que importam.

**Por que foi construído.** A IA executa qualquer direção que recebe com a
mesma convicção — gerar código plausível é barato para ela, e "plausível" não
é o mesmo que "a decisão certa para este domínio". Decisões de modelagem de
domínio (como uma entidade aprende o estado de outra sem virar acoplamento
direto, como nomear um método que faz dispatch de múltiplas regras) têm
consequências que só aparecem depois — na testabilidade, na legibilidade, na
superfície que o `domain-guardian` vai auditar depois. Pedir a opção com
trade-off, em vez do código direto, move a decisão de volta para onde ela
precisa estar: comigo, antes do código existir, não depois como refatoração.

**Exemplos concretos.**

- **Facts-as-parameters.** Ao modelar como `Task` aprende que seu projeto
  está arquivado (necessário para a regra 6) sem a entidade `Task` depender
  de `Project` ou de um serviço de consulta, a instrução explícita foi
  "propor como... antes de escrever qualquer código". A decisão aprovada:
  os métodos de transição de `Task` recebem o fato relevante como parâmetro
  (`ProjectStatus`), não o objeto `Project` inteiro nem uma referência a
  repositório. A camada de aplicação busca o fato via porta
  (`existsByProjectIdAndStatus`) e passa para o método de domínio — a regra
  vive inteiramente no domínio, mas o domínio nunca sabe *como* o fato foi
  obtido. Auditado depois pelo `domain-guardian`: "Facts-as-parameters —
  PASS."
- **IDs tipados.** `ProjectId`/`TaskId` como `record` que envolve `UUID`
  (`quarkus-impl/src/main/java/com/taskflow/domain/model/ProjectId.java`),
  em vez de `UUID` cru circulando por assinaturas de método — decisão que
  torna impossível passar um `TaskId` onde um `ProjectId` é esperado, erro
  que o compilador pega em vez de um teste de integração.
- **`changeStatusTo` — nomear um método que despacha múltiplas regras.**
  `Task.changeStatusTo(TaskStatus, ProjectStatus)` precisava checar a regra
  6 (projeto arquivado) *antes* de despachar para `startProgress`/
  `complete` — inclusive para o alvo `PENDING`, que não tem método de
  transição próprio (retrocesso nunca é válido, mas ainda precisa reportar
  o erro certo se o projeto estiver arquivado). O javadoc do método
  documenta essa decisão explicitamente: "Rule 6 is checked first even when
  the target has no transition method". Pinado por teste
  (`rule6WinsEvenForThePendingTargetThatHasNoTransitionMethod`), verificado
  depois pelo `domain-guardian`: "precedência correta incluindo o alvo sem
  método de transição".

---

## Reflexão — onde essa engenharia importou de verdade

Nenhum desses cinco mecanismos vale como exibição isolada; o valor apareceu
nos pontos em que um achou o que os outros não achariam. Três exemplos que
atravessam mais de um mecanismo, com o registro completo em
`ai/revisoes.md`:

- **A IA admitindo que o próprio `CLAUDE.md`, que ela disse ter sincronizado
  numa rodada, continuava desatualizado na rodada seguinte.** Só apareceu
  porque o `spec-reviewer` (mecanismo 3) comparou os dois arquivos de novo,
  não porque a IA notou sozinha — o resumo que ela mesma escreveu duas
  entradas antes ("sincronizei os dois documentos") estava errado, e só um
  diff comparado expôs isso. Lição registrada: não dá para confiar no
  resumo que a própria IA escreve sobre seu trabalho; tem que se checar o
  artefato.
- **A flag fabricada do Snyk.** Uma alegação de pesquisa (`--force-maven-cli`)
  descrevia corretamente a função desejada mas inventava o nome da flag —
  aplicada direto ao pipeline sem checar `--help` da CLI real ou o
  repositório-fonte. Só foi pega porque, numa segunda falha idêntica, o
  pedido explícito mudou de "tenta de novo" para investigação formal com
  fonte primária. Nenhum dos três subagentes cobre esse domínio (CI/CD) —
  o mecanismo que pegou foi o hábito de exigir evidência antes de aceitar
  "deve ser isso", o mesmo princípio por trás dos subagentes, aplicado fora
  deles.
- **Os dois modos de falha do fuzzing de contrato.** Schemathesis, rodando
  contra `spec/openapi.yaml` já "congelada", achou que o *schema* (não a
  API) estava sub-declarado: `format: uuid` sem `pattern`, `minLength: 1`
  sem excluir string só-espaço — a prosa da spec já prometia as regras
  certas, mas o JSON Schema formal não as impunha. Seis rodadas de revisão
  humana/`spec-reviewer` nunca pegaram essa lacuna especificamente porque
  revisão de prosa lê "canônico" e "não pode estar em branco" e completa a
  implicação mentalmente — uma ferramenta de fuzzing não faz essa inferência,
  só valida o que está formalmente declarado. E a primeira correção do
  schema formalizou só metade da regra (não-branco, mas não caractere de
  controle), abrindo uma segunda rodada de achado no mesmo padrão. Nenhuma
  camada de verificação sozinha — revisão humana, subagente, fuzzing,
  autoverificação da IA — pegou tudo; a composição delas, rodada após
  rodada, foi reduzindo a superfície do que sobrava.

O padrão comum aos três: cobertura (de linha, de rodada de revisão, de
convicção da IA) não é o mesmo que verificação. Cada mecanismo desta pasta
`.claude/` existe para forçar uma segunda pergunta — "isso foi verificado
contra a fonte certa, ou só parece certo?" — no ponto em que seria mais fácil
não perguntar.
