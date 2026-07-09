# Revisões de saídas da IA

Registro honesto de correções, rejeições e ajustes aplicados sobre o que a IA
gerou neste projeto. Objetivo: documentar onde a IA errou, foi ingênua ou
incompleta, e por quê — não é um changelog de features.

> Ordenado por força do achado, não por ordem cronológica: as entradas mais
> reveladoras sobre como dirigir IA neste domínio vêm primeiro. A numeração de
> cada entrada (`## N.`) é o identificador original, preservado para as
> referências cruzadas no texto (ex.: "entrada 9") continuarem válidas — não
> corresponde à posição no documento.

---

## 2. Furo de invariante retroativo — regra 1 só checada no momento do arquivamento

1. **Data / Fase** — 2026-07-04, fase de especificação (`spec/openapi.yaml`,
   revisão adversarial via agente `spec-reviewer`, 19 achados na rodada 1).

2. **O que a IA sugeriu** — Na v2 do spec (já com schemas separados), a
   regra de negócio 1 ("projeto só pode ser arquivado se nenhuma tarefa
   estiver `in_progress`") só aparecia como validação de `PATCH
   /projetos/{id}` no momento do arquivamento. Nada no `PATCH
   /tarefas/{id}` impedia uma tarefa `pending` — que é permitida dentro de
   um projeto já arquivado, já que o arquivamento só é bloqueado por
   tarefa `in_progress`, não por `pending` — de transicionar para
   `in_progress` depois que o projeto já estava arquivado.

3. **Problema identificado** — Eu tinha modelado a regra 1 como uma
   validação pontual (só dispara no ato de arquivar), não como um
   invariante mantido ao longo do tempo. Resultado: seguindo exatamente a
   v2 do spec, dava pra chegar num estado — projeto arquivado com tarefa
   `in_progress` — que a regra 1 existe justamente pra impedir. Eu não
   tinha essa lacuna na cabeça até o agente `spec-reviewer` apontar
   (achado #4 da rodada 1); eu tinha pensado a regra só a partir do
   endpoint onde ela é "óbvia" (arquivar), não a partir de todos os
   caminhos que levam ao estado que ela protege. Numa rodada de revisão
   mais ampla (19 achados no total), outros pontos também expuseram
   ingenuidade parecida: nenhuma exemplo de erro 400 concreto (achado #7),
   `NotFound` genérico reaproveitado com texto errado pra tarefa (achado
   #5, dizia "projeto" mesmo em 404 de tarefa), e numeração de regra de
   negócio nos exemplos do spec (`"Business rule 4"`) que já divergia da
   numeração do `CLAUDE.md` — ou seja, a documentação interna do próprio
   repo já estava dessincronizada do que eu tinha acabado de escrever.

4. **Correção aplicada** — Adicionei regra de negócio 6, nova e distinta:
   tarefa de projeto arquivado não pode mudar `status` (título/descrição
   continuam editáveis), com `type` URI e exemplo próprios, documentada no
   `PATCH /tarefas/{id}`. De quebra, formalizei duas decisões que fecham
   ambiguidade de transição de status: transições estritamente
   sequenciais (só `pending→in_progress` e `in_progress→done`; pular etapa
   é 422 pela mesma regra de transição) e PATCH same-state é no-op 200 (não
   conta como avanço nem retrocesso). Também tornei PATCH atômico
   explícito (violação de regra rejeita o request inteiro, nada é
   persistido parcialmente) e documentei a ausência de `DELETE /projetos`
   como decisão de escopo deliberada — antes ela simplesmente não existia,
   sem nenhuma nota dizendo que era proposital. Corrigi a numeração de
   regras no `CLAUDE.md` pra bater com a ordem do enunciado do desafio
   (1 arquivar, 2 deletar, 3 completedAt, 4 criar em projeto arquivado,
   5 transições, 6 nova regra), já que a v2 do spec e o `CLAUDE.md` tinham
   ficado com numeração divergente entre si.

5. **Lição** — Invariante de domínio não pode ser pensado só a partir do
   endpoint onde ele parece óbvio; preciso perguntar "quais outros
   endpoints/transições levam a um estado que essa regra deveria proteger?"
   antes de considerar a regra coberta. Rodar uma revisão adversarial
   dedicada (agente separado, não eu mesmo relendo o que escrevi) valeu a
   pena justamente porque achou o tipo de lacuna que quem escreveu o spec
   tende a não ver — vale repetir esse padrão a cada rodada de spec antes
   de congelar pra implementação.

---

## 4. Rodada 3 — o próprio `CLAUDE.md` que eu tinha "corrigido" ficou errado, e minha correção de 400s virou sobre-correção nova

1. **Data / Fase** — 2026-07-04, fase de especificação (`spec/openapi.yaml`
   e `CLAUDE.md`, terceira revisão adversarial via agente `spec-reviewer`).

2. **O que a IA sugeriu** — Na entrada 2 (rodada 1), eu tinha renumerado o
   `CLAUDE.md` pra bater com a nova ordem do spec e disse explicitamente,
   no resumo daquela entrada, que tinha "corrigido a numeração de regras no
   `CLAUDE.md` pra bater com a ordem do enunciado". Na rodada 2 (entrada
   3), reformulei a regra de negócio 6 do spec de whitelist
   (title/description editáveis) pra blacklist explícita
   (title/description/**priority** editáveis, só status travado) — mas só
   no `spec/openapi.yaml`, sem propagar a mesma mudança de volta pro
   `CLAUDE.md`. E, na mesma entrada 3, resolvi "faltam exemplos de 400"
   consolidando tudo num componente `BadRequest` único reaproveitado em
   todo endpoint.

3. **Problema identificado** — Duas coisas, achadas pelo `spec-reviewer`
   numa terceira passada, não por mim relendo o que escrevi:
   - **`CLAUDE.md` desatualizado por mim mesma**: a regra 6 lá continuava
     dizendo "title/description edits... remain allowed", sem mencionar
     `priority` — divergindo do `spec/openapi.yaml`, que eu tinha corrigido
     uma rodada antes. Eu literalmente escrevi, na entrada anterior deste
     mesmo arquivo, que tinha sincronizado os dois documentos — e não
     sincronizei por completo. O agente apontou isso como "CLAUDE.md
     diverge do spec e nem lista a regra 6" (um pouco exagerado — a regra 6
     existia, só estava com o texto antigo — mas o núcleo do achado
     procede: os dois arquivos não batiam).
   - **`BadRequest` consolidado (entrada 3, rodada 2) sobre-corrigiu de
     novo**: componentes de response `$ref`-compartilhados carregam TODOS
     os exemplos anexados a eles pra TODO endpoint que os referencia —
     então `GET /projetos` (sem path id, sem body) documentava exemplo de
     `completedAt` (só faz sentido em PATCH de tarefa) e de filtro
     `priority` (que esse endpoint nem tem). Era a mesma classe de erro da
     rodada 2 (over-generalização por consolidar demais), reaparecendo de
     um jeito mais sutil depois que eu achava ter corrigido.

4. **Correção aplicada** — Regerei o diff do `CLAUDE.md` antes de
   prosseguir (a pedido explícito do usuário) e corrigi a regra 6 pra
   "title, description e priority" — sincronizado de fato com o spec dessa
   vez, comprovado por diff, não por afirmação minha. Substituí o
   `BadRequest` único por 7 componentes de response por *contexto de
   endpoint* (`PathParamBadRequest`, `ProjectBodyBadRequest`,
   `ProjectPatchBodyBadRequest`, `TaskCreateBodyBadRequest`,
   `TaskPatchBodyBadRequest`, `ProjectStatusFilterBadRequest`,
   `TaskFiltersBadRequest`), cada um só com os exemplos que aquele endpoint
   específico pode de fato produzir — endpoints com duas causas possíveis
   (path malformado + corpo/query inválido) ganharam um componente próprio
   que já combina as duas, em vez de um bloco inline duplicado ou um
   componente genérico demais. Também reformulei a decisão de "PATCH
   same-state é no-op": não é "o request inteiro não faz nada", é *por
   campo* — reenviar o `status` atual não dispara transição, mas qualquer
   outro campo no mesmo request (ex.: `name`) é aplicado normalmente.
   Validei com `redocly lint` que os novos componentes não ficaram órfãos
   (0 ocorrências de `no-unused-components`) — não bastava criar
   componente por contexto, tinha que garantir que cada um fosse
   `$ref`-ado de algum lugar de verdade.

5. **Lição** — Quando peço pra IA editar dois arquivos que precisam ficar
   sincronizados (spec + `CLAUDE.md`), não posso confiar no resumo que ela
   escreve dizendo "sincronizei" — tenho que pedir o diff e checar eu
   mesma, porque uma mudança feita só num dos dois é fácil de escapar até
   pra quem fez as duas rodadas. E "consolidar pra reduzir repetição" é uma
   correção que, sem verificar escopo por endpoint, tende a reintroduzir o
   problema original (exemplo irrelevante documentado) de forma mais
   difícil de notar — vale sempre perguntar "esse componente compartilhado
   é usado por endpoints que genuinamente têm o mesmo formato de erro, ou
   só por conveniência de escrita?" antes de consolidar.

---

## 12. Coerção escalar do Jackson — drift de contrato invisível, pego pelo /sdd-check

1. **Data / Fase** — 2026-07-07, fase de implementação Quarkus (adapters
   já commitados e auditados; achado da primeira rodada do `/sdd-check`,
   corrigido em `5d20b93`).

2. **O que a IA sugeriu** — Os request DTOs tipavam todos os campos como
   `String` (decisão deliberada e correta em si: permitia coletar todos
   os erros de enum/validação de uma vez em `errors[]`, em vez do
   fail-fast do Jackson). O que a IA não configurou foi a coerção
   escalar default do Jackson por baixo disso: `{"name": 123}` era
   silenciosamente coagido para `"123"` e aceito com `201`.

3. **Problema identificado** — A spec declara `type: string`; um número
   JSON não é uma string, e a requisição deveria falhar com `400`
   `invalid-request-body`. O drift era duplamente invisível: (a) nenhum
   teste enviava tipo errado em campo de texto — o mesmo padrão
   recorrente das entradas 9 e 11 (mais adiante neste documento): a
   suíte testava o que foi escrito, não o espaço de entradas que o
   contrato promete; (b) a auditoria de código do
   `domain-guardian` (quarta passada) também não pegou, porque não há
   linha errada pra apontar — o defeito era um *default de framework
   não configurado*, exatamente a categoria "o que nunca foi escrito".
   Quem pegou foi o `/sdd-check`, comparação sistemática spec×código —
   a própria IA reportou o drift contra o próprio código, no modo
   report-only pedido. Notável: campos de enum não eram afetados (o
   valor coagido falhava na validação de domínio de valores), só os de
   texto livre.

4. **Correção aplicada** — `JacksonCoercionConfig` implements
   `ObjectMapperCustomizer` (global pra camada REST, não por DTO):
   coerção `Integer`/`Float`/`Boolean` → alvo textual configurada como
   `CoercionAction.Fail`, escopada a `LogicalType.Textual` — DTOs não
   têm campo numérico (direção inversa sem o que configurar) e a
   serialização de resposta fica intacta. A falha vira
   `MismatchedInputException`, que os mappers de erro do Jackson
   (entrada 11, mais adiante) já convertem no `400 invalid-request-body`
   com o campo nomeado — nenhum
   mapper novo, nenhum 500 cru. Dois testes de taxonomia novos:
   `{"name": 123}` → 400/`name`; `{"priority": 1}` → 400/`priority`.
   O código mudou, a spec ficou intocada — direção única do fluxo SDD.
   Segunda rodada do `/sdd-check`: zero drift. Suíte 121/121.

5. **Lição** — Default tolerante de framework é fonte de drift que nem
   revisão de código nem a suíte que o autor escreveu enxergam — só
   comparação sistemática contra o contrato (e, mecanicamente, a suíte
   de contrato/Schemathesis do próximo passo); conformidade não é o que
   o código faz, é o que o framework deixa passar por ele.

---

## 17. Lacuna prosa-vs-schema no `openapi.yaml` congelado — pega pelo Schemathesis, não por 6 rodadas de revisão humana

1. **Data / Fase** — 2026-07-08, primeira execução real do pipeline de CI
   (job `contract`, Schemathesis contra `spec/openapi.yaml`), já com a
   spec formalmente "congelada" (ver item 10).

2. **O que a IA sugeriu** — Nada de errado a corrigir na primeira
   passada: a IA (eu) escreveu `spec/openapi.yaml` com
   `ProjectIdParam`/`TaskIdParam` como `type: string, format: uuid` (sem
   `pattern`) e `name`/`title` como `minLength: 1, maxLength: N` (sem
   restrição de whitespace) — e a prosa ao lado (`info.description`,
   exemplos `ProjectNameInvalid`/`TaskTitleInvalid`) já prometia
   exatamente as regras mais estritas que o JSON Schema formal não
   codificava: UUID canônico case-insensitive só no shape 8-4-4-4-12, e
   "não pode estar em branco" além do comprimento. A implementação
   (`UuidPathParamFilter`, `BodyValidation.invalidText`) já fazia a coisa
   certa desde o início.

3. **Problema identificado** — Schemathesis gera dados a partir do JSON
   Schema formal, não da prosa. `format: uuid` não é validado por
   nenhuma engine de schema (é só anotação); `minLength: 1` sozinho
   aceita uma string de um único espaço. Rodando contra a API real, a
   ferramenta gerou UUIDs de path em forma não-canônica e nomes/títulos
   só-espaço, viu a API rejeitar corretamente (400), e reportou isso
   como "schema constraints don't match API validation" em 6 operações —
   um sinal de que o *schema* estava sub-declarado, não de que a API
   estava errada. Seis rodadas de revisão adversarial humana/IA sobre a
   spec (itens 1–9, 14) nunca pegaram essa lacuna especificamente porque
   revisão de prosa lê "canônico" e "não pode estar em branco" e
   preenche mentalmente a implicação de schema — a ferramenta de fuzzing
   não faz essa inferência, só lê o que está formalmente declarado.

4. **Correção aplicada** — `pattern` adicionado em 6 pontos, todos
   *aditivos* (nenhum `minLength`/`maxLength` removido ou enfraquecido):
   `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`
   em `ProjectIdParam`/`TaskIdParam` (case-insensitive — verifiquei antes
   de aplicar que uma leitura lowercase-only contradiria a decisão já
   fixada no item 3 deste documento e quebraria
   `uppercaseHexUuidPassesPathValidation`; o humano confirmou
   case-insensitive quando perguntado), e `.*\S.*` em
   `CreateProjectRequest.name`, `UpdateProjectRequest.name`,
   `CreateTaskRequest.title`, `UpdateTaskRequest.title`. Por ser emenda à
   spec congelada, passou por `spec-reviewer` como confirmação dedicada
   antes do commit (achou os dois regexes corretos, sem contradição nova
   com prosa/exemplos existentes) e pela suíte de contrato completa
   (162/162, sem quebra — validação client-side é desligada
   deliberadamente nos testes Java, então o aperto do schema não afeta
   os testes existentes, só ferramentas externas como o Schemathesis).
   Ver `docs/decisoes.md` item 12 para o racional completo.

5. **Lição** — Fuzzing baseado em schema mede a fidelidade do próprio
   schema, não da implementação: ele expõe exatamente a categoria de
   lacuna que revisão humana de prosa estrutura mal para achar (mesma
   lição do item 1 sobre "o que nunca foi escrito") — porque a máquina
   não preenche intenção a partir de texto, só valida o que está
   formalmente declarado. Rodar a suíte de contrato contra a API viva,
   cedo, teria achado isso antes da spec ser declarada congelada; rodá-la
   depois ainda vale, mas transforma toda emenda subsequente em mudança
   de artefato já "fechado" — daí o cuidado extra (spec-reviewer
   dedicado) que esta emenda específica recebeu.

---

## 18. Meio-fechamento do item 17 — o pattern de "não-branco" formalizou metade da regra e escondeu a outra metade

1. **Data / Fase** — 2026-07-08, execução seguinte do pipeline de CI
   após o fechamento do item 17 (patterns de UUID canônico e não-branco
   já aplicados e "confirmados" pelo `spec-reviewer`).

2. **O que a IA sugeriu** — No item 17, o pattern aplicado a
   `name`/`title` foi `pattern: '.*\S.*'` — só a metade "não pode estar
   em branco" da mensagem de exemplo (`ProjectNameInvalid`/
   `TaskTitleInvalid`: "não pode estar em branco **e** deve ter no
   máximo N caracteres"). A implementação (`BodyValidation.hasControlChar`,
   chamada por `invalidText` desde uma correção anterior desta mesma
   sessão) também rejeita qualquer caractere de controle — regra que já
   existia, já testada, já documentada — mas essa segunda metade nunca
   virou `pattern` no schema. O `spec-reviewer` disparado no item 17
   revisou exatamente esse pattern e não achou o problema, porque a
   pergunta feita a ele foi "esse regex está correto para 'não-branco'?"
   — nunca "esse regex cobre TODA a regra que o código aplica?". O
   escopo da pergunta limitou o que a revisão podia achar.

3. **Problema identificado** — Schemathesis, rodando contra a API real,
   gerou strings com caracteres de controle ASCII reais (0x07 BEL,
   0x1C FS) misturados com texto comum (`"=A"`) — que satisfazem
   `.*\S.*` (caractere de controle não é whitespace) mas que a API
   corretamente rejeita. 4 falhas de "API rejected schema-compliant
   request" — a mesma categoria do item 17, não uma nova: formalizar
   *parte* de uma regra existente no schema deixa a parte não-formalizada
   exatamente tão invisível para fuzzing quanto estava antes de qualquer
   pattern existir. Meio-fechamento tem o mesmo efeito prático que
   nenhum fechamento, para a parte que ficou de fora.

4. **Correção aplicada** — Antes de aplicar, a IA testou o próprio
   regex proposto (`^(?=.*\S)[^\x00-\x1F\x7F-\x9F]*$`) contra os 256
   codepoints 0x00–0xFF em Python e achou um bug nele mesmo: o `$` do
   Python (e do Java) casa tanto no fim absoluto da string quanto na
   posição logo antes de um `\n` final isolado — `"A\n"` casava com o
   pattern quando não devia (`\n` é caractere de controle). Trocado
   `$` por `(?![\s\S])` (lookahead de "nenhum caractere segue"), que não
   tem essa exceção em nenhum motor, e essa versão foi validada em
   Python E em Node/V8 (o motor ECMA-262 que a OpenAPI declara) antes de
   ser aplicada nos 4 pontos. Rodada local do Schemathesis com a mesma
   seed do achado original confirmou os 4 casos resolvidos — mas expôs
   um quinto, adjacente: `description` tem a mesma `hasControlChar` no
   código e nenhum pattern no schema (não estava no escopo desta
   rodada — `description` é opcional/nullable, então o pattern não pode
   ser o mesmo `.*\S.*`-based; registrado em `docs/decisoes.md` item 12
   como pendência, não aplicado ainda). O `spec-reviewer` rodado nesta
   emenda, por sua vez, achou uma quarta camada: `(?=.*\S)` usa `.`, e
   o `.` do Python exclui só `\n`, enquanto o `.` do ECMA-262 (o motor
   nominal da spec) também exclui U+2028/U+2029 — que não são
   caracteres de controle e deveriam ser aceitos, mas um validador JS
   estrito os rejeitaria via esse pattern. A varredura de codepoints
   feita pela IA (0x00–0x24F) nunca tocou U+2028/U+2029 — não cobria o
   ponto exato da divergência. Achado reportado ao usuário, correção
   (`(?=.*\S)` → `(?=[\s\S]*\S)`) ainda não aplicada, aguardando decisão.

5. **Lição** — Cada rodada desta série (itens 14, 17, 18) fechou um
   pouco e abriu o próximo: 6 rodadas de revisão de prosa não acharam a
   lacuna schema-vs-prosa (item 17); a primeira correção do schema
   formalizou só metade de uma regra e a fuzzing achou a outra metade
   (este item); a correção da segunda metade tinha um bug de motor de
   regex que só apareceu numa varredura de codepoints feita pela própria
   IA antes de aplicar; e mesmo essa versão corrigida tinha uma
   divergência de engine que só o `spec-reviewer` achou, numa faixa de
   codepoints que a varredura da IA nunca tinha testado. Nenhuma camada
   de verificação (revisão humana, teste de código, fuzzing de contrato,
   verificação própria da IA, revisão adversarial de agente) pegou
   tudo sozinha — cada uma pegou uma fatia diferente do mesmo problema
   recorrente, e só a composição delas, rodada após rodada, foi reduzindo
   a superfície do que sobrava. Dirigir IA em domínio de correção formal
   (regex, schema) exige pedir verificação *específica e exaustiva*
   ("varra todos os codepoints", "teste em dois motores") — pedir só
   "isso está certo?" produz uma confirmação tão limitada quanto o
   escopo da pergunta.

---

## 13. Testes de taxonomia sobre-especificados — igualdade exata em prosa ilustrativa da spec

1. **Data / Fase** — 2026-07-07, camada de validação de contrato do
   `quarkus-impl` (matriz de cobertura do agente `contract-tester` +
   validação de schema nas respostas; corrigido em `f4d8eb4`).

2. **O que a IA sugeriu** — Nos testes de taxonomia escritos em fases
   anteriores, a própria IA asseriu strings de `detail`/`message` com
   igualdade exata, copiadas dos `components.examples` da spec:

   ```java
   .body("detail", equalTo("Nenhum projeto encontrado com id " + unknown))
   .body("detail", equalTo("O identificador informado não é um UUID válido."))
   .body("errors[0].message", equalTo("deve ser um UUID válido"))
   ```

3. **Problema identificado** — O agente `contract-tester`, ao montar a
   matriz de cobertura, flagrou sobre-especificação nos meus próprios
   testes de taxonomia: igualdade exata em strings de detail copiadas
   dos examples da spec — examples em OpenAPI são ilustrativos, não
   normativos (o schema só exige `detail` legível), e a exigência
   forçaria o Rails a reproduzir as frases do Java caractere a
   caractere, divergência que o contrato nunca prometeu. O ponto sutil:
   o drift aqui é *para mais* — os testes exigiam mais do que a spec
   promete, o espelho do padrão de sub-especificação que aparece nas
   entradas 9, 10, 11 e 12 deste documento, onde a suíte exigia
   *de menos*. Ambos são desvio do contrato; este passaria despercebido
   até o dia 3, quando a suíte Rails ou reproduziria frases em
   português do Java ou "falharia" sem violar contrato algum.

4. **Correção aplicada** — Política de asserção explícita, decidida
   pelo usuário sobre o achado e documentada no javadoc do
   `ErrorTaxonomyTest` (vale também para a suíte Rails):
   - **Normativo — igualdade exata**: `type` URI, status HTTP, membro
     `status` do problem, shape do schema, `errors[].field`, resultados
     de precedência.
   - **Ilustrativo — token de carga (`containsString`)**: prosa de
     `detail`/`message`, com tokens escolhidos para provar que a regra
     certa disparou (ex.: `"retroceder"` para regra 5, `"estiver
     arquivado"` para regra 6).
   - **Exceção — exata**: texto que a política normativa de erros da
     spec cita verbatim (os details de filtro de query).
   Igualdades exatas ilustrativas abrandadas para tokens; asserções
   normativas mantidas (e reforçadas: as seis regras 422 agora asserem
   `type` + `status` + token do `detail`).

5. **Lição** — Testes também driftam do contrato — para mais, não só
   para menos: asserção mais forte que a promessa da spec vira contrato
   fantasma que a segunda implementação herda sem nunca ter sido
   pactuado; a fronteira normativo/ilustrativo precisa ser política
   explícita da suíte, não hábito do gerador.

---

## 20. Flag fabricada do Snyk — `--force-maven-cli` citada por síntese de busca, nunca existiu

1. **Data / Fase** — 2026-07-08, fase de CI/CD (pipeline de segurança).
   Erro cometido na correção de pipeline round 2; achado e corrigido na
   rodada seguinte, sobre investigação formal pedida pelo usuário.

2. **O que a IA sugeriu** — Diagnosticando por que o job `security`
   nunca chegava a escanear (`snyk/actions/maven@master` falhava antes
   de rodar), a IA propôs e aplicou `--force-maven-cli` aos args do
   Snyk em `ci.yml`, citada como a flag que força o uso do Maven
   instalado globalmente em vez do wrapper embarcado na imagem
   `snyk/snyk:maven`. A descrição da função estava certa; o nome da
   flag, não.

3. **Problema identificado** — Rodada seguinte, o mesmo erro
   ("Failed to validate Maven distribution SHA-256") persistiu
   idêntico — sinal de que a flag não estava surtindo efeito. Pedida
   investigação formal (não mais uma tentativa de nome no escuro), a
   IA instalou a CLI mais recente do Snyk localmente
   (`npm install -g snyk`, v1.1305.2), rodou `snyk test --help` e
   buscou o repositório `snyk/cli` inteiro (código e histórico de
   commits): `--force-maven-cli` **nunca existiu em nenhuma versão**.
   A flag tinha sido citada a partir de uma síntese de busca na
   internet de uma rodada anterior — que descreveu corretamente a
   *função* desejada mas inventou o *nome* — e a IA aplicou direto ao
   `ci.yml` sem checar contra a fonte primária (`--help` da CLI real,
   ou o próprio repositório `snyk/cli`) antes de propor a mudança.

4. **Correção aplicada** — A flag real, com a descrição exata da
   função pretendida ("Forces the use of a globally installed mvn
   command..."), é `--maven-skip-wrapper` — adicionada em março de
   2026 (PR #6653); a imagem Docker `snyk/snyk:maven` foi reconstruída
   em 5 de julho de 2026 (confirmado via API do Docker Hub), então a
   CLI empacotada era recente o bastante para já ter o flag.
   `--force-maven-cli` → `--maven-skip-wrapper` em `ci.yml`. A troca
   só foi aceita como fechada depois que o log de CI seguinte mostrou
   o Snyk de fato escaneando a árvore de dependências real (177
   dependências testadas), não um "0 dependências" ou skip silencioso
   — condição posta explicitamente antes de aceitar.

5. **Lição** — Uma alegação de pesquisa (mesmo quando a *função*
   descrita está certa) não é fonte primária: nome de flag/parâmetro
   de CLI precisa de verificação direta (`--help`, código-fonte,
   changelog) antes de entrar em configuração que só falha em CI, onde
   o ciclo de feedback é lento e caro. O erro só foi pego porque o
   usuário recusou aceitar "deve ser isso" numa segunda tentativa e
   pediu investigação formal com evidência, em vez de mais um chute de
   nome — a IA não teria achado sozinha sem esse pedido explícito.

---

## 19. Gate de segurança provou seu valor — CVE real no driver postgresql pego só depois do fix do Snyk (item ligado ao 17/18)

1. **Data / Fase** — 2026-07-08, fase de CI/CD (pipeline de segurança:
   Snyk + Trivy + gitleaks), depois da correção da flag do Maven CLI
   (`--maven-skip-wrapper`) que destravou o step do Snyk.

2. **O que a IA sugeriu** — Nas rodadas anteriores (`ci: fix pipeline`),
   o foco foi só destravar o step do Snyk em si — a flag certa para o
   CLI Maven embarcado na imagem `snyk/snyk:maven` validar a distribuição
   sem quebrar em "Failed to validate Maven distribution SHA-256". Nenhuma
   suposição foi feita sobre o que o scan ia encontrar depois de rodar —
   o objetivo daquelas rodadas era só "o Snyk consegue rodar", não "o
   Snyk aprova".

3. **Problema identificado** — Assim que o Snyk finalmente escaneou de
   verdade (177 dependências), achou 1 vulnerabilidade real de severidade
   alta: `SNYK-JAVA-ORGPOSTGRESQL-17874248` ("Incorrect Implementation of
   Authentication Algorithm") em `org.postgresql:postgresql@42.7.11`,
   puxado transitivamente por `io.quarkus:quarkus-jdbc-postgresql@3.37.1`,
   que por sua vez vem do `quarkus-bom@3.37.1` — o BOM oficial da
   plataforma, considerado "confiável" por definição. Isso mostra que
   confiar num BOM gerenciado não é garantia de dependências livres de
   CVE: o BOM fixa versões por compatibilidade testada, não por ausência
   de vulnerabilidade conhecida, e um driver JDBC de banco (superfície
   de autenticação) é exatamente o tipo de dependência transitiva que
   ninguém audita manualmente. Sem o gate de Snyk rodando de fato — e
   sem as duas rodadas anteriores insistindo em destravar a flag certa
   em vez de aceitar o step vermelho — essa CVE teria ido para produção
   silenciosamente, mascarada pelo verde do resto do pipeline.

4. **Correção aplicada** — Override de versão em `dependencyManagement`
   no `quarkus-impl/pom.xml`, declarado *depois* do import do
   `quarkus-bom` (ordem importa: a entrada mais específica declarada
   por último no mesmo `dependencyManagement` vence a do BOM importado),
   fixando `org.postgresql:postgresql` em `42.7.12` via propriedade
   `postgresql.version`, versão onde o Snyk reporta a issue como
   corrigida. Verificação em duas camadas antes de aceitar como pronto:
   `mvn dependency:tree -Dincludes=org.postgresql:postgresql` confirmando
   que a versão efetivamente resolvida no grafo é `42.7.12` (não bastava
   só declarar — BOMs e overrides têm regras de precedência que podem
   silenciosamente não pegar), e `./mvnw verify` completo (162 testes,
   incluindo os testes de integração via Testcontainers que sobem
   Postgres de verdade) confirmando que o bump de patch não quebrou
   nada em runtime. O Snyk local não pôde ser reexecutado para confirmar
   (401 — sessão não autenticada), então a confirmação final fica
   pendente da próxima rodada de CI, registrada como tal em vez de
   assumida como certa.

5. **Lição** — Um gate de segurança só entrega valor quando ele
   efetivamente roda e efetivamente falha quando deveria: as duas
   rodadas anteriores gastas destravando a flag do Maven CLI não foram
   trabalho de infraestrutura desperdiçado, foram a pré-condição para
   este achado real. Dirigir IA em pipeline de segurança exige separar
   "o step passou" de "o step rodou de verdade" — um step verde por
   erro de configuração (ex: SHA-256 do Maven falhando antes de sequer
   escanear) é pior que um step vermelho, porque cria falsa sensação de
   cobertura. E mesmo depois do fix, aceitar a correção proposta pela
   IA exige verificação de efeito real (dependency:tree, não só o
   diff do pom.xml) — declarar uma versão em XML não garante que o
   resolvedor de dependências do Maven vai de fato usá-la.

---

## 1. Schemas do OpenAPI modelados como entidade de banco

1. **Data / Fase** — 2026-07-04, fase de especificação (`spec/openapi.yaml`, v1 → v2).

2. **O que a IA sugeriu** — Um único schema por recurso, reutilizado em request
   e response (`Projeto`, `Tarefa`), incluindo campos `readOnly` como `id`,
   `createdAt`, `completedAt`, `projectId` no mesmo objeto usado para
   `POST`/`PATCH`. Rejeição de campo indevido ficava só a cargo de
   `readOnly: true` na doc, sem `additionalProperties: false` nos requests.

3. **Problema identificado** — Isso modela o contrato como se fosse a
   representação de banco, não como uma interface de API. `readOnly: true`
   é só metadado de documentação no OpenAPI 3.0 — a maioria dos geradores de
   código e validadores de schema NÃO o usa para rejeitar o campo se ele vier
   no body. Resultado prático: um client poderia mandar `completedAt` ou
   `id` no `PATCH` e, dependendo da ferramenta usada para validar contra o
   schema, isso passaria silenciosamente. Também misturava, no mesmo tipo,
   dado de entrada (o que o client controla) com dado derivado pelo sistema
   (o que o servidor decide) — RICH domain model exige essa fronteira clara
   também no contrato, não só no código.

4. **Correção aplicada** — Separação em três schemas por recurso:
   `CreateXRequest` (só campos aceitos na criação), `UpdateXRequest` (só
   campos patchable) e `X` como shape de response completo. Todo schema de
   request ganhou `additionalProperties: false`, que é enforcement real de
   schema (não documentação) — qualquer campo fora da lista, incluindo os
   `readOnly` da entidade, é rejeitado com `400`. Isso tornou explícita a
   decisão de que campos de sistema nunca são um "input opcional que o
   client pode ignorar": eles simplesmente não existem no schema de request.

   Decisão associada, mesma revisão: `completedAt` enviado manualmente no
   `PATCH /tarefas/{id}` = **400**, não 422. Raciocínio: `completedAt` é
   `readOnly`, controlado pelo domínio (`Task#complete`); sua presença no
   body é um problema de forma do request (contrato), equivalente a mandar
   `id` ou `createdAt` — não é uma regra de negócio violada por um request
   bem formado. Reservei `422` só para os 4 invariantes de domínio reais
   (arquivar com tarefa in_progress, deletar tarefa não-pending, regressão
   de status, criar tarefa em projeto arquivado). Documentei a decisão na
   própria descrição do response `400` desses dois endpoints, e removi o
   comentário YAML com as duas opções que eu tinha deixado em aberto na v1.

5. **Lição** — Ao pedir spec de API pra IA, pedir explicitamente "schemas de
   request separados de response, com `additionalProperties: false`" — do
   contrário a IA tende a gerar um schema por recurso (mais compacto, "DRY"
   na superfície) que na prática vaza a forma de persistência pro contrato
   e depende de `readOnly` (fraco) em vez de `additionalProperties: false`
   (forte) pra proteger campos de sistema.

---

## 3. Rodada 2 do spec-reviewer — minhas próprias correções da rodada 1 se contradiziam

1. **Data / Fase** — 2026-07-04, fase de especificação (`spec/openapi.yaml`,
   segunda revisão adversarial via agente `spec-reviewer`, 16 achados, sobre
   o spec já corrigido pela entrada 2 acima).

2. **O que a IA sugeriu** — Na rodada 1 eu tinha, na mesma leva de mudanças:
   (a) declarado "same-state PATCH é sempre no-op 200" como decisão de
   escopo genérica, e (b) criado a regra de negócio 6 dizendo "status de
   tarefa não pode mudar se o projeto está arquivado" — sem checar se as
   duas convivem. Também, na mesma rodada, "corrigi" a falta de exemplos de
   400 (achado #7 da rodada 1) consolidando tudo num único componente
   `BadRequest` reaproveitado por todo endpoint, com os mesmos 5 exemplos e
   o mesmo `type` URI (`invalid-request-body`) pra casos de corpo, path e
   query string.

3. **Problema identificado** — Duas contradições que eu mesmo introduzi
   tentando corrigir a rodada anterior, achadas pelo agente `spec-reviewer`
   numa segunda passada (não por mim relendo):
   - **Conflito no-op vs. regra 6**: reenviar `status` com o valor atual de
     uma tarefa cujo projeto está arquivado — é no-op 200 (primeira
     decisão) ou 422 de regra 6 (segunda decisão)? O spec da rodada 1 não
     dizia qual das duas minhas próprias regras vencia. Eu criei a regra 6
     pensando só no caso "tentar avançar status", sem testar o caso
     "reenviar o mesmo status" contra ela.
   - **BadRequest consolidado sobre-generalizou a correção anterior**: ao
     resolver "faltam exemplos de 400" (achado #7 da rodada 1) do jeito
     mais simples — um componente único reaproveitado em todo lugar —
     criei um problema novo: todo endpoint passou a "documentar" exemplos
     irrelevantes pra ele (ex.: `GET /projetos`, que não tem path id nem
     body, documentava exemplo de `completedAt` num PATCH e de UUID
     malformado num path que ela nem tem) e um único `type` URI
     ("invalid-request-body") cobrindo path/query/body — o que é
     tecnicamente errado (RFC 7807 usa `type` justamente pra diferenciar
     categorias de problema; "invalid **request body**" descrevendo um
     erro de path parameter é uma contradição textual).

4. **Correção aplicada** — Precedência explícita: no-op vence a regra 6
   ("reenviar o status atual não é uma mudança de status, logo não é
   violação de regra de negócio, mesmo com projeto arquivado") — declarada
   tanto na decisão de escopo quanto no texto da própria regra 6 e na
   descrição do `PATCH /tarefas/{id}`, pra não depender de quem lê achar
   uma e não a outra. Regra 6 também foi reformulada como *blacklist*
   (status é o único campo travado; title/description/priority continuam
   editáveis) em vez de *whitelist* implícita, que deixava a real do
   `priority` ambígua. `BadRequest` foi desmembrado em três componentes —
   `InvalidRequestBody` (schema body, `ValidationProblemDetails`),
   `InvalidPathParameter` e `InvalidQueryParameter` (ambos `ProblemDetails`
   simples) — cada um com `type` URI próprio, e cada endpoint religado só
   aos exemplos que ele pode de fato produzir (endpoints com duas causas
   possíveis, tipo `PATCH` com path+body, usam `oneOf` combinando os dois
   schemas). Também formalizei precedência de validação (400 → 404 → 422,
   a primeira falha vence e nunca chega na próxima etapa) e permiti
   desarquivamento (`archived → active`) explicitamente, que antes existia
   só implicitamente no enum sem nenhuma regra dizer se era permitido.

5. **Lição** — Corrigir um achado isoladamente pode contradizer uma decisão
   já tomada ou sobre-generalizar de um jeito que cria um achado novo (mais
   sutil) no lugar do antigo; depois de aplicar uma leva de correções, vale
   rodar a revisão adversarial de novo sobre o resultado, não assumir que
   "resolvido" = "sem novos problemas" — foi exatamente essa segunda
   passada que achou os dois conflitos acima, que eu não veria relendo meu
   próprio spec.

---

## 5. Rodada 4 — colisão de regras dentro do próprio 422, e um `oneOf` quebrado que eu mesma introduzi

1. **Data / Fase** — 2026-07-04, fase de especificação (`spec/openapi.yaml`,
   quarta revisão adversarial via agente `spec-reviewer`, sobre o spec já
   corrigido pelas entradas 3 e 4 acima).

2. **O que a IA sugeriu** — Na rodada 3 (entrada 4), eu tinha documentado a
   regra de negócio 5 (transição forward-only) e a regra 6 (status travado
   se projeto arquivado) como dois itens paralelos e independentes na lista
   de "Business rules", sem nenhuma relação de precedência entre elas. Na
   mesma rodada, criei o schema `AnyProblemDetails` — um `oneOf:
   [ProblemDetails, ValidationProblemDetails]` — pra representar, num único
   componente, os dois motivos de 400 possíveis (path malformado ou corpo
   inválido) nos endpoints que têm as duas causas ao mesmo tempo (ex.:
   `PATCH /tarefas/{id}`).

3. **Problema identificado** — Dois achados novos, de novo pelo
   `spec-reviewer` (quarta passada, não por mim):
   - **Regras 5 e 6 podiam colidir no mesmo request, sem precedência
     definida**: pela regra 1, projeto só arquiva se nenhuma tarefa está
     `in_progress` — logo toda tarefa dentro de um projeto arquivado só
     pode estar `pending` ou `done`. Qualquer tentativa de mudar o `status`
     dessa tarefa (`done→pending`, `done→in_progress`, `pending→done`)
     viola a regra 5 (transição inválida) **e** a regra 6 (travado por
     projeto arquivado) ao mesmo tempo. O parágrafo de "precedência de
     validação" que eu já tinha (400→404→422) só resolve a ordem ENTRE
     esses três estágios, não a ordem DENTRO do estágio 422 quando duas
     regras de negócio se aplicam ao mesmo request — eu tinha resolvido um
     tipo de colisão (rodada 3, no-op vs. regra 6) e deixado outro
     idêntico em espírito sem perceber.
   - **`AnyProblemDetails` era um `oneOf` que não exclui de fato**:
     `ProblemDetails` não tem `additionalProperties: false`, então um
     payload com `errors[]` (formato de `ValidationProblemDetails`) valida
     com sucesso contra AMBOS os ramos do `oneOf` ao mesmo tempo — o que
     quebra a semântica de `oneOf` (exige exatamente um). Como o
     `CLAUDE.md` compromete o Rails com o gem `committee` pra validação de
     conformidade OpenAPI, um `oneOf` ambíguo desse jeito é o tipo de coisa
     que passa no `swagger-cli validate` (só checa estrutura do documento)
     mas quebra silenciosamente ou de forma inconsistente num validador
     JSON Schema estrito de verdade — e eu só teria descoberto isso
     tentando gerar teste de contrato, não lendo o spec.

4. **Correção aplicada** — Precedência explícita dentro do 422: regra 6
   (precondição de estado — "o projeto está arquivado?") é checada antes da
   regra 5 (validade da transição) — generalizado como princípio
   ("precondições de estado antes de regras de transição/valor") no
   parágrafo de precedência de validação, e repetido na própria regra 6 e
   nas duas bullets da descrição do `PATCH /tarefas/{id}`, pra não deixar
   a mesma ambiguidade se espalhar por três lugares de novo. Removi
   `AnyProblemDetails` por completo: depois da divisão por contexto de
   endpoint (rodada 3), cada 400 já tem causa determinística — path
   malformado sempre vira `ProblemDetails` puro, corpo inválido sempre vira
   `ValidationProblemDetails` — então o `oneOf` nunca era necessário, só
   um artefato de eu ainda pensar em "categoria de erro" em vez de
   "endpoint específico". No processo de aplicar isso descobri que nem
   sequer dava pra reaproveitar o mesmo exemplo de "UUID inválido" nos dois
   contextos (`ProblemDetails` puro vs. `ValidationProblemDetails`) — o
   validador do `redocly lint` rejeita propriedade extra (`errors`) mesmo
   sem `additionalProperties: false` explícito — corrigido separando em
   dois exemplos (`InvalidUuidPathParameter` sem `errors`,
   `InvalidPathParameterFieldError` com `errors`), cada um só usado onde o
   schema realmente bate.

5. **Lição** — Cada rodada de correção pode introduzir o defeito da rodada
   seguinte — não é sinal de estar piorando, é o custo normal de resolver
   ambiguidade real: toda vez que declaro uma regra nova, preciso perguntar
   "essa regra pode se aplicar ao mesmo request que outra regra já
   existente, e se sim, qual vence?", em vez de só checar se a regra nova
   está bem escrita isoladamente. Revisão adversarial repetida vale a pena,
   mas precisa de critério de parada — o sinal de parar não é "zero
   achados" (isso pode não acontecer nunca), é "achados restantes são só
   nitpick de doc, não mais defeito que muda comportamento observável entre
   as duas stacks".

---

## 6. Rodada 5 (final) — ambiguidade tinha migrado do 422 pro próprio estágio 400

1. **Data / Fase** — 2026-07-04, fase de especificação (`spec/openapi.yaml`,
   quinta revisão adversarial via agente `spec-reviewer`, com critério de
   parada explícito desta vez: só continuar se achado for comportamental,
   parar se for nitpick de doc).

2. **O que a IA sugeriu** — Na rodada 4 (entrada 5), resolvi a colisão
   entre regra 5 e regra 6 dentro do estágio `422`, e formalizei que `400`
   vem antes de `404` que vem antes de `422`. O que eu não tinha percebido:
   dentro do próprio estágio `400`, quando um endpoint tem path id **e**
   corpo (ou path id **e** query string), eu nunca disse qual causa checar
   primeiro — só listava os exemplos possíveis lado a lado no mesmo
   componente de response, sem ordem. Os headers `Location` dos dois `201`
   também não tinham `required: true`, e não havia decisão nenhuma sobre
   parâmetro de query desconhecido (`?foo=bar`).

3. **Problema identificado** — Achado pelo `spec-reviewer` (quinta passada,
   aplicando o critério de parada que eu mesma dei): a ambiguidade que eu
   achava ter fechado na rodada 4 (colisão de regras) só tinha sido fechada
   no estágio `422` — o mesmo tipo de problema reapareceu um nível abaixo,
   no `400`. `PATCH /tarefas/nao-e-um-uuid` com corpo `{"title": ""}` podia
   legitimamente retornar `field: id` (implementação A, valida path
   primeiro), `field: title` (implementação B, valida corpo primeiro), ou
   os dois juntos (implementação C, valida tudo e agrega) — as três
   conformam com o spec como estava escrito, mas produzem corpos de
   resposta diferentes pro mesmo request. Mesma classe de bug da rodada 4
   (regra nova declarada sem checar sobreposição com outra regra/estágio
   já existente), um nível de abstração abaixo de onde eu já tinha
   resolvido da última vez — eu corrigi a colisão que via, não perguntei
   "existe uma colisão parecida em outro estágio da pipeline de
   validação?". `Location` sem `required: true` é falha mais simples: a
   doc dizia "toda criação retorna Location" em prosa, mas o schema não
   obrigava, então uma implementação que esquecesse o header ainda passava
   validação de contrato.

4. **Correção aplicada** — Sub-ordem explícita dentro do `400`: path
   parameters → query parameters → request body, fail-fast (só a primeira
   categoria que falha é reportada, nunca as duas juntas). Escolhi essa
   ordem com razão declarada no spec, não arbitrária: o path parameter
   identifica QUAL recurso o request endereça, então não faz sentido
   validar mais nada antes disso estar resolvido — e essa ordem já é o
   comportamento nativo de JAX-RS (resolve path antes de invocar validação
   de corpo) e do Rails (roteamento resolve segmentos de path antes do
   controller processar params), então documentar isso não força nenhum
   stack a se comportar diferente do que já faria por padrão. Adicionei
   `required: true` nos dois headers `Location`. Documentei parâmetro de
   query desconhecido como decisão deliberada (ignorado silenciosamente —
   tolerant reader —, não rejeitado), já que OpenAPI 3.0 não tem mecanismo
   tipo `additionalProperties: false` pra query string do jeito que tem pra
   corpo.

   Bônus de processo: o agente `spec-reviewer`, ao revisar, percebeu que o
   `CLAUDE.md` injetado no contexto da sessão (a versão que "veio de
   fábrica" na conversa) estava desatualizada e divergia do spec — só que,
   em vez de reportar isso como achado real, ele foi ler o arquivo de
   verdade no disco antes de concluir, e confirmou que o arquivo real já
   estava correto e sincronizado. Ele mesmo sinalizou a diferença como
   "artefato de contexto, não defeito de repositório".

5. **Lição** — Resolver uma ambiguidade num estágio não garante que a
   mesma ambiguidade não exista em outro estágio adjacente da mesma
   pipeline (aqui: 422 → 400, antes tinha sido no-op vs. regra de negócio);
   depois de fechar uma colisão, vale perguntar "essa mesma pergunta —
   'o que vence quando duas coisas se aplicam ao mesmo tempo' — já foi
   feita em TODOS os estágios de validação, ou só naquele onde o achado
   apareceu?". E, à parte do spec: contexto de sessão (o que foi carregado
   no início da conversa) pode envelhecer e divergir do filesystem real ao
   longo de uma sessão longa com múltiplas edições — quando a IA (ou eu)
   notar uma discrepância envolvendo um arquivo que já foi editado nesta
   sessão, ler o arquivo de novo do disco é mais confiável que confiar no
   que está no contexto.

---

## 7. Rodada 6 — ordenação de listagem sobreviveu a 5 rodadas de revisão sem ninguém notar

1. **Data / Fase** — 2026-07-04, fase de especificação (`spec/openapi.yaml`,
   sexta revisão adversarial via agente `spec-reviewer`, depois de eu ter
   declarado a rodada 5 como encerramento da fase — o usuário pediu mais
   uma passada mesmo assim).

2. **O que a IA sugeriu** — Desde a v1 do spec, `GET /projetos` e `GET
   /projetos/{id}/tarefas` diziam só "retorna toda a coleção que casa com
   o filtro, sem paginação" — nunca especificava a ORDEM dos itens dentro
   do array retornado. Isso passou por 5 rodadas de revisão adversarial
   (19 achados na primeira, mais 8, 6, 16 e 6 nas seguintes) sem nenhuma
   delas apontar isso.

3. **Problema identificado** — Achado pela sexta passada do
   `spec-reviewer`, depois que eu já tinha declarado a fase de spec
   "encerrada" no fim da rodada 5: `JPA`/Panache sem `ORDER BY` explícito e
   `ActiveRecord.all`/`where(...)` sem `.order(...)` explícito herdam,
   cada um, a ordem de retorno de linhas que o PRÓPRIO banco/query planner
   decidir — não é garantido que Postgres (Quarkus) e o banco do Rails
   devolvam na mesma ordem, e nem é garantido que a ordem seja estável
   entre execuções do mesmo banco. Isso é diferença observável no corpo
   da resposta HTTP (a mesma coleção de recursos, em ordem diferente) —
   exatamente o tipo de divergência comportamental entre as duas stacks
   que todas as rodadas anteriores estavam caçando, só que numa dimensão
   (ordem, não conteúdo/schema/regra) que nenhuma delas cobriu, porque
   toda revisão anterior comparava "o que o spec diz X vs. o que o spec
   deveria dizer sobre X" pra um X já mencionado — ordenação nunca tinha
   sido mencionada, então não havia "o que está errado" pra apontar, só
   "o que falta" — e "o que falta e nem foi tentado" é estruturalmente
   mais fácil de escapar de uma revisão adversarial do que "o que foi
   tentado e ficou incompleto/contraditório" (que foi a classe de quase
   todos os achados das rodadas 1-5).

4. **Correção aplicada** — Ordenação determinística declarada como parte
   do contrato: `createdAt` ascendente, com `id` ascendente como
   desempate. O desempate por `id` não é cosmético — `createdAt` sozinho
   não é ordem total (dois registros podem ser criados no mesmo
   milissegundo, especialmente em fixtures/seeds de teste rodando em lote,
   e aí a ordem entre eles ficaria indefinida de novo). Documentei em três
   lugares: a bullet de "No pagination" no `info.description` (agora diz
   explicitamente que ordenação é parte do contrato, coberta por testes de
   contrato, não deixada pro default de cada banco) e a descrição de cada
   um dos dois endpoints de listagem.

5. **Lição** — Revisão adversarial (minha ou de agente) é muito melhor
   pra achar "isso que o spec diz está errado/incompleto/contraditório" do
   que pra achar "isso que o spec deveria dizer e nunca disse" — a segunda
   categoria (propriedades transversais como ordenação, encoding,
   timezone, locale de erro) precisa de um checklist deliberado e
   proativo ("toda listagem tem ordem definida? todo timestamp tem
   timezone explícito? todo texto tem encoding declarado?"), não só
   descoberta incidental via "será que sobrou algo". Declarar uma fase
   "encerrada" não devia significar parar de aceitar revisão — o próprio
   fato de o usuário ter pedido mais uma rodada depois do meu
   "encerramento" na rodada 5 foi o que achou esse gap.

---

## 8. Varredura final de propriedades transversais — timezone e trailing slash

1. **Data / Fase** — 2026-07-04, fase de especificação (`spec/openapi.yaml`,
   varredura dirigida por checklist, não revisão adversarial genérica —
   pedida explicitamente como consequência da lição da entrada 7).

2. **O que a IA sugeriu** — Depois da rodada 6 (ordenação de listagem),
   o `spec/openapi.yaml` declarava `format: date-time` pros campos
   `createdAt`/`completedAt` sem dizer nada sobre timezone/offset no
   output, e nenhuma das 6 rodadas de revisão tinha comentado sobre
   trailing slash em nenhum path.

3. **Problema identificado** — Confirmando exatamente a lição da entrada
   7 (revisão adversarial de texto acha "o que está escrito errado", não
   "o que nunca foi escrito"), essa varredura foi dirigida por um
   checklist explícito de propriedades transversais em vez de pedir mais
   uma passada genérica do `spec-reviewer` — e achou 2 riscos reais de
   novo:
   - **Timezone de datetime**: `format: date-time` (RFC 3339) permite
     tanto `Z` quanto offset numérico (`-03:00` etc.) — o spec nunca
     escolhia um. Jackson (serialização padrão do Quarkus) normalmente
     emite `Instant` como `Z`; `ActiveSupport::TimeWithZone` do Rails, por
     padrão, serializa no fuso horário configurado da aplicação, com
     offset — não `Z`. Mesmo instante de tempo, string diferente no corpo
     da resposta, dependendo de qual stack responde.
   - **Trailing slash**: nada dizia se `/projetos/` (barra no fim) deveria
     casar com `/projetos`. Roteamento do Rails e do RESTEasy/JAX-RS não
     normalizam isso da mesma forma por padrão — um pode dar 404, o outro
     pode aceitar transparentemente, pro mesmo URL.

4. **Correção aplicada** — Duas decisões de custo de implementação zero
   (evitam forçar configuração extra nos dois stacks) em vez de tentar
   fazer os dois convergirem por acidente: (a) output de datetime é
   sempre UTC com sufixo `Z` literal, offset nunca é emitido — decisão de
   escopo no `info.description` mais `description` repetindo a regra em
   cada campo `createdAt`/`completedAt` nos schemas `Project`/`Task`; (b)
   trailing slash declarado fora do contrato — paths canônicos não têm
   barra no fim, requests pra forma não-canônica têm comportamento não
   especificado e excluído dos testes de conformidade (mais barato que
   obrigar os dois stacks a normalizar/rejeitar de forma idêntica).

5. **Lição** — A lição da entrada 7 generaliza: depois de fechar uma
   rodada de revisão adversarial de texto, vale rodar pelo menos uma
   passada dirigida por checklist de propriedades transversais
   (ordenação, timezone, encoding, casing, trailing slash, content-type)
   antes de considerar o contrato pronto — são exatamente o tipo de coisa
   que "reler o spec de novo" não vai achar, porque não há nada de errado
   escrito, só uma decisão que nunca foi tomada.

---

## 9. Auditoria do domain-guardian na camada de domínio Quarkus — pureza garantida só por convenção

1. **Data / Fase** — 2026-07-06, fase de implementação Quarkus (camada de
   domínio recém-commitada em `a1e6e5d`; auditoria via subagente
   `domain-guardian`, pedida explicitamente após a geração).

2. **O que a IA sugeriu** — A camada de domínio gerada estava correta em
   si: zero imports de framework, sem setters públicos em campos de
   invariante, as 6 regras de negócio como métodos com nome de intenção
   (`archive(hasTaskInProgress)`, `startProgress(ProjectStatus)`, etc.),
   precedência regra 6 antes da regra 5 dentro dos métodos de transição.
   A verificação de pureza, porém, foi feita só com `grep` de imports —
   um check pontual, executado uma vez, que não protege nenhum commit
   futuro.

3. **Problema identificado** — A auditoria (limpa em todos os itens
   obrigatórios) apontou duas lacunas prospectivas que a IA geradora não
   tinha levantado sozinha: (a) `quarkus-hibernate-orm-panache` e o
   driver JDBC já estão no classpath de compilação por causa dos futuros
   adapters — Maven não escopa dependência por pacote, então um
   `import jakarta.persistence.*` acidental dentro de `..domain..`
   **compilaria sem erro**; a pureza hexagonal, requisito não-negociável
   do `CLAUDE.md`, dependia só de grep manual e disciplina; (b) o setup
   de PIT (mutation testing) ainda não existe no `pom.xml`. A lacuna (a)
   é o achado relevante: uma regra arquitetural que não quebra o build é
   uma regra que vai ser violada silenciosamente na primeira sessão de
   trabalho apressada dentro dos adapters.

4. **Correção aplicada** — Aceita a recomendação (a) com timing
   antecipado deliberadamente: ArchUnit adicionado **antes** da fase de
   adapters, não depois — `archunit-junit5` 1.4.1 em escopo de teste e
   `HexagonalArchitectureTest` com duas regras: domínio não pode
   depender de `jakarta..`, `io.quarkus..`, `org.hibernate..`,
   `com.fasterxml..` nem de `..adapter..`/`..application..`; aplicação
   (quando existir — `allowEmptyShould` até lá) sob a mesma restrição de
   framework, podendo depender do domínio mas nunca dos adapters. Roda
   na fase de teste normal do Maven, e a regra foi validada
   negativamente: uma classe-canário anotada com `@Entity` dentro do
   domínio quebrou o build como esperado antes de ser removida. A
   recomendação (b) foi conscientemente adiada: PIT pertence à etapa de
   mutation testing do plano, não a esta — adotá-lo agora só adicionaria
   configuração morta.

5. **Lição** — Agente revisor não serve só pra achar erro no que foi
   escrito: também propõe melhoria estrutural que o gerador não propôs
   ("a regra existe mas nada a torna executável"). Mas as duas
   recomendações vieram com o mesmo peso, e foi a decisão humana que
   separou o timing — uma valia antecipar (barata agora, cara depois do
   primeiro vazamento), a outra valia adiar (inútil antes da fase a que
   pertence). Aceitar recomendação de agente em bloco, sem arbitrar
   timing, teria sido tão ruim quanto ignorá-la.

---

## 10. Testes de atomicidade do PATCH eram vácuos — passavam sem testar nada

1. **Data / Fase** — 2026-07-06, fase de implementação Quarkus (camada de
   aplicação; achado da terceira passada do `domain-guardian`, corrigido
   antes do commit `f390ee6`).

2. **O que a IA sugeriu** — A lista de testes aprovada em plan mode
   incluía dois testes de atomicidade do PATCH (itens 16 e 37): "campo
   válido + violação de regra no mesmo request → nada persiste". A IA os
   implementou assim:

   ```java
   // {name: válido, status: archived} com tarefa in_progress
   assertThrows(ProjectArchiveBlockedException.class, () ->
       useCase.execute(id, command(ofNullable("renamed"), absent(),
                                   ofNullable(ARCHIVED))));
   assertEquals("p", projects.findById(id).orElseThrow().name());
   ```

   E investiu em fakes com cópias defensivas (`reconstitute` em
   `save`/`findById`) justamente para que mutações não salvas nunca
   vazassem pro store — a infraestrutura certa pra testar atomicidade.

3. **Problema identificado** — Os dois testes eram **vácuos por ordem de
   processamento**: o use case processa `status` *antes* dos outros
   campos, então a exceção estourava antes de qualquer mutação válida
   acontecer. O teste passaria mesmo com fakes sem cópia defensiva,
   mesmo com múltiplos `save`, mesmo com atomicidade quebrada — a
   asserção final nunca exercitava o cenário que dizia testar. Ironia
   dupla: a IA construiu a infraestrutura de cópias defensivas correta e
   depois escreveu os únicos testes que a justificavam numa ordem que
   nunca a acionava. Cobertura de linha e nome do teste diziam
   "atomicidade testada"; o mutante "remover cópia defensiva"
   sobreviveria intacto. Foi o `domain-guardian` (terceira passada,
   item 6 do relatório) que pegou — não a autora dos testes.

4. **Correção aplicada** — Dois testes novos na direção que exercita de
   verdade: mutação válida *primeiro* (status avança em memória), campo
   inválido *depois* (nome em branco / título de 201 chars →
   `IllegalArgumentException` do domínio), asserção de que o avanço de
   status NÃO persistiu:

   ```java
   // status avança em memória; título inválido estoura depois;
   // nada pode ter chegado ao store
   assertThrows(IllegalArgumentException.class, () ->
       useCase.execute(id, command(ofNullable("a".repeat(201)), absent(),
                                   ofNullable(IN_PROGRESS), absent())));
   assertEquals(PENDING, tasks.findById(id).orElseThrow().status());
   ```

   Os testes originais foram mantidos (cobrem a direção "regra falha
   primeiro", que também é comportamento do contrato) — o par agora
   cobre as duas ordens. Suíte: 70/70 verde.

5. **Lição** — Teste com nome certo e asserção certa ainda pode ser
   vácuo se o caminho até a asserção não passa pelo estado que ela
   verifica; pra teste de atomicidade, a pergunta obrigatória é "a
   mutação que não pode vazar chegou a *acontecer* antes do throw?".
   Revisão de teste precisa simular a ordem de execução do código sob
   teste, não só ler a intenção — e mutation testing (PIT, etapa
   futura) teria pego isso mecanicamente: o mutante da cópia defensiva
   sobreviveria aos testes originais.

---

## 11. Taxonomia RFC 7807 tinha um buraco: erros do Jackson escapavam do contrato

1. **Data / Fase** — 2026-07-07, fase de implementação Quarkus (adapters;
   achado [major] da quarta passada do `domain-guardian`, corrigido em
   `414b39e` sobre os commits `c317388`/`188435c`).

2. **O que a IA sugeriu** — O adapter REST implementou a taxonomia de
   erros completa da spec: 9 type URIs, precedência 400→404→422,
   fail-fast path→query→body, `application/problem+json` em todo erro —
   e o `ErrorTaxonomyTest` cobria um teste por linha da tabela de
   mapeamento. Todos os `ExceptionMapper` registrados, porém, cobriam
   apenas as exceções *do próprio projeto* (domínio, aplicação e as três
   do adapter). Todos os testes de corpo inválido partiam de JSON
   *sintaticamente válido*.

3. **Problema identificado** — Corpo com JSON malformado (`{invalid`),
   corpo não-objeto ou valor de campo com formato errado
   (`"name": {"a":1}`) nunca chegavam aos mappers do projeto: caíam no
   tratamento default do framework — um `400` de texto puro (ou vazio),
   sem `type`, sem `application/problem+json` — violando o requisito
   não-negociável do `CLAUDE.md` de que TODO erro é RFC 7807. O buraco
   era invisível à suíte porque nenhum teste enviava JSON quebrado: a
   IA testou exaustivamente a taxonomia que *escreveu*, não o espaço de
   entradas que o contrato *promete cobrir*. Mesma classe de cegueira da
   lição da entrada 9 do processo de spec ("o que nunca foi escrito"),
   agora em código. Achado pelo agente auditor, não pela autora.

4. **Correção aplicada** — Três mappers novos, e a correção exigiu
   engenharia reversa do framework (decompilação de
   `RequestDeserializeHandler` e do `quarkus-rest-jackson`) porque as
   duas primeiras tentativas falharam por motivos distintos e
   não-óbvios: (a) um mapper genérico para `JacksonException` perdia
   para o `BuiltinMismatchedInputExceptionMapper` do Quarkus, mais
   específico na hierarquia de tipos — resolvido com um mapper próprio
   exatamente para `MismatchedInputException`, que desempata a
   especificidade a favor do projeto; (b) JSON malformado nem sequer
   carrega a causa: o framework lança `WebApplicationException(400)`
   *sem corpo e sem cause* — resolvido com um mapper de
   `WebApplicationException` que converte apenas a assinatura exata
   (400 + sem entity, ou cause Jackson) e deixa 404/405/415 passarem
   intactos. Dois testes novos no `ErrorTaxonomyTest` (JSON malformado,
   valor de campo com shape errado). No mesmo commit, o achado [minor]:
   desempate de ordenação nos fakes usava `UUID.compareTo` (comparação
   com sinal), que diverge da ordem unsigned de bytes do Postgres —
   fakes agora comparam a string hex canônica. Suíte 119/119.

5. **Lição** — Testar a taxonomia de erros que você escreveu não é
   testar o contrato de erros: os caminhos de erro que o *framework*
   gera (parse, negociação de mídia, binding) fazem parte da superfície
   observável da API e precisam de teste e mapeamento explícitos — e
   integrar com o comportamento default de um framework às vezes exige
   ler o código dele, não a documentação; a IA fez isso sozinha, mas só
   depois que o auditor apontou o buraco.

---

## 14. Duas lacunas na spec congelada — descobertas pela suíte, promovidas a texto normativo

1. **Data / Fase** — 2026-07-07, sequência da camada de validação de
   contrato (matriz do `contract-tester`, achados O2/O4); emenda da spec
   em `e19b290`, testes de pinagem em `4bc3a69`, racional em `d498908`.

2. **O que a IA sugeriu** — Em fases anteriores, a IA escreveu testes
   que respondiam duas perguntas que a spec (também escrita pela IA)
   nunca tinha respondido:

   ```java
   // spec não dizia qual type URI para JSON não-parseável:
   .body("{invalid").post("/projetos") ... .body("type", equalTo(ERR + "invalid-request-body"));
   // spec não dizia onde passa a linha textual de format: uuid:
   .get("/projetos/1-1-1-1-1") ... .statusCode(400);
   ```

   Ambas as respostas eram razoáveis — e ambas viviam só nos testes.

3. **Problema identificado** — A suíte de contrato revelou duas lacunas
   na spec congelada (não drift do código): type URI de JSON
   não-parseável indefinido, e forma textual de UUID sem linha normativa
   (o parser leniente do Java aceita `1-1-1-1-1`; um regex estrito
   rejeita — divergência garantida entre stacks). Conhecimento tácito no
   teste Java é contrato invisível para o Rails do dia 3: a segunda
   implementação só descobriria as duas regras falhando contra a suíte
   da primeira — engenharia reversa, o oposto de SDD. Testes provam
   conformidade ao contrato; não podem SER o contrato.

4. **Correção aplicada** — Promoção de ambas ao texto da spec via o rito
   de emenda: bullets de escopo em `info.description`, descrições dos
   componentes de 400 de corpo e dos params de path, exemplos novos
   (`UnparseableRequestBody`, `NonCanonicalUuidPathParameter`), e passe
   de confirmação do `spec-reviewer` — que **derrubou a primeira redação
   da própria promoção**: 3 bloqueadores, todos do mesmo tema ("fixou
   *que* é 400 com o type certo, mas deixou o payload indeterminado").
   "Campo nomeado quando a posição do parse permite" deixava `errors[]`
   dependente do parser (Jackson expõe path; `JSON.parse` do Ruby só
   linha/coluna); "canônico RFC 4122" sozinho era autocontraditório
   quanto a caixa (o RFC emite minúsculas mas aceita input em qualquer
   caixa). Redação final determinística: imparseável → sempre `field:
   body`; não-vinculável → fail-fast em ordem de documento, só o
   primeiro campo; hex sem distinção de caixa, só shape 8-4-4-4-12
   imposto. Cada regra ganhou teste de pinagem (76/76). Racional
   completo em `docs/decisoes.md`, seção 3.

5. **Lição** — Implementar contra a spec também é revisá-la: lacuna
   descoberta pela implementação vira texto normativo pelo mesmo rito de
   qualquer emenda (revisão adversarial inclusa — que aqui pegou a
   correção precisando de correção), nunca conhecimento tácito
   enterrado no teste de uma das stacks.

---

## 15. Cinco mutantes sobreviventes do PIT — testes com asserção unilateral, fechando o apontamento do guardian

1. **Data / Fase** — 2026-07-07, fase de testes de mutação do
   `quarkus-impl` (pitest-maven 1.25.6 + pitest-junit5-plugin 1.2.3,
   escopo domain + application). Fecha o apontamento do guardian da
   fase de domínio.

2. **O que a IA sugeriu** — Os testes de domínio e application escritos
   nas fases anteriores pela própria IA: 99% de cobertura de linha,
   53 testes, aparência de suíte completa. Primeira rodada do PIT:
   95% de score de mutação (96/101), 3 sobreviventes + 2 sem cobertura.

3. **Problema identificado** — Os cinco mutantes expuseram o mesmo
   defeito estrutural: asserção unilateral. Cobertura de linha alta
   mascarava testes que só olhavam um lado do comportamento:

   - `Task.validDescription` — teste rejeitava 2001 chars mas nunca
     aceitava 2000; o teste irmão de `title` tinha os dois lados
     (201 rejeita + 200 aceita). Inconsistência do mesmo gerador no
     mesmo arquivo: mutante de fronteira (`>` → `>=`) sobreviveu só
     na description.
   - `Project.isArchived` — só `assertTrue` após arquivar; `return
     true` forçado passava. Nunca asserido `false` em projeto ativo.
   - `UpdateTaskUseCase.execute` — todos os testes descartavam o
     retorno e re-liam o repositório; `return null` sobrevivia, embora
     o retorno alimente o corpo da resposta 200 no adapter.
   - Guard de projeto ausente (`orElseThrow` com `IllegalStateException`)
     — caminho nunca exercitado por nenhum teste.
   - `TaskStatusRegressionException.taskId()` — acessor sem nenhum
     chamador em produção nem em teste, ao contrário dos irmãos
     (`currentStatus()`/`requestedStatus()` eram asseridos).

4. **Correção aplicada** — Decisão mutante a mutante (humano decidiu
   cada um; todos → matar, nenhum aceito como equivalente): linha de
   aceitação no limite exato de 2000; `assertFalse(isArchived())` no
   teste de criação; captura e asserção do retorno do use case; teste
   novo de tarefa órfã esperando `IllegalStateException`; asserção de
   `taskId()` no bloco existente de regressão. Segunda rodada:
   **100% (101/101), zero sobreviventes, zero sem cobertura**. Para o
   quinto mutante a alternativa honesta era deletar o acessor morto —
   mantido por simetria com as exceções irmãs, mas registrado que o
   argumento "mutante equivalente" ali seria na verdade argumento de
   código morto.

5. **Lição** — Cobertura de linha mede execução, não verificação: a IA
   gera com facilidade testes que executam os dois lados de uma regra
   mas asserem só um; mutação é o instrumento que torna essa assimetria
   visível e barata de corrigir — rodá-la é fechar o ciclo que o
   guardian só consegue apontar.

---

## 16. Decisão de escopo — implementação Rails cortada; o plano dual-stack nunca foi questionado pela IA

1. **Data / Fase** — 2026-07-07, revisão de escopo pós-mutação (todos os
   artefatos atualizados na mesma sessão; `docs/decisoes.md` §10 é o
   registro da decisão).

2. **O que a IA sugeriu** — Rigorosamente, nada errado: o dual-stack
   (Quarkus + Rails sobre o mesmo contrato) foi meta autoimposta por mim,
   não proposta da IA. Mas a IA abraçou o plano integralmente e o
   entranhou em todos os artefatos que gerou — `CLAUDE.md` com dois
   stacks e modo de mentoria Ruby, subagentes (`domain-guardian` com
   seção Rails inteira, `contract-tester` citando a gem `committee`,
   `/sdd-check` aceitando `rails-impl` como argumento), pirâmide de
   testes com camadas Rails, tabela de delegação em `ai/skills.md` com
   duas linhas Ruby — sem em nenhum momento sinalizar que o desafio pede
   *uma* API e que a segunda era risco de prazo.

3. **Problema identificado** — Dois, de naturezas diferentes:
   - **De processo (meu e da IA)**: o custo do dual-stack só foi
     confrontado com o calendário depois de mutação + suíte de contrato
     + emendas de spec consumirem o tempo que o Rails ocuparia. A IA
     executa o escopo que recebe com a mesma convicção, seja ele viável
     ou não — em nenhuma das dezenas de sessões ela levantou "isso cabe
     no prazo?". Vigilância de escopo não é delegável.
   - **De consistência (achado na execução do corte)**: o plano estava
     mais espalhado do que minha própria lista de artefatos a atualizar —
     eu listei 5 itens; a varredura da IA achou mais 3 fora da lista
     (`domain-guardian`, `contract-tester`, `sdd-check` em `.claude/`),
     além de uma contagem defasada preexistente ("5 business rules" em
     agentes escritos antes da regra 6 existir). Escopo cortado em prosa
     mas vivo em tooling continuaria dirigindo sessões futuras da IA
     para um stack que não existe mais.

4. **Correção aplicada** — Corte com atualização consistente de todos os
   artefatos: `CLAUDE.md` single-stack (mentoria Ruby e comandos Rails
   removidos), `spec/openapi.yaml` com prosa neutralizada (6 pontos, só
   texto — contrato byte a byte intacto no que é normativo; servidor
   `:3000` removido), `docs/decisoes.md` §2 reescrito como arquitetura
   da entrega + §10 novo com o racional do trade-off, `ai/skills.md` com
   as linhas Ruby removidas e observação de processo, os 3 arquivos de
   `.claude/` de fora da minha lista corrigidos, `rails-impl/` (vazio)
   deletado. Ponto central preservado no §10: o trabalho de fechamento de
   divergência cross-stack na spec (ordenação, datetimes, forma de UUID,
   mapeamento de parse-failure, precedência) **não** vira custo afundado
   — é o que torna o contrato independente de implementação, que é a
   própria tese de SDD. O Rails funcionou como "segundo leitor
   hipotético" que forçou a spec a ser inequívoca; o corte remove a
   demonstração, não a propriedade. A redação do §10 passou por revisão
   minha antes dos demais artefatos serem tocados (a IA mostrou o
   rascunho e perguntou como tratar as demais seções — escolhi rewrite
   leve em vez de nota histórica).

5. **Lição** — A IA executa qualquer escopo com convicção igual; a
   pergunta "isso ainda cabe?" tem que vir do humano, cedo e
   recorrentemente — e quando o corte vier, pedir varredura mecânica de
   TODOS os artefatos (inclusive o tooling em `.claude/` que dirige a
   própria IA), porque plano morto que sobrevive em agente/skill vira
   instrução ativa nas sessões seguintes.
