# Log de prompts curados

Registro dos prompts usados pra gerar artefatos do projeto, o que a IA
produziu, e o resultado. Complementa `ai/prompt-log.md` (raw) e
`ai/revisoes.md` (correções detalhadas).

---

## 1. Criação inicial do `spec/openapi.yaml`

1. **Contexto** — Fase de especificação. Primeiro contrato da API TaskFlow
   Projetos & Tarefas, a partir dos requisitos do desafio.

2. **Prompt (resumo)** — Pedido pra ler `CLAUDE.md` e gerar
   `spec/openapi.yaml` (OpenAPI 3.0) com os 8 endpoints (`/projetos`,
   `/projetos/{id}`, `/projetos/{id}/tarefas`, `/tarefas/{id}`), entidades
   Projeto e Tarefa com seus campos/enums, modelo de erro RFC 7807
   (`ProblemDetails`/`ValidationProblemDetails`), 5 regras de negócio com
   `type` URI distinto e exemplo cada, PATCH como partial update com
   rejeição de campo desconhecido/readOnly, e nota explícita de que não há
   paginação. Sem código de implementação.

3. **O que a IA produziu** — `spec/openapi.yaml` criado do zero: paths,
   parâmetros, um schema único por recurso (`Projeto`, `Tarefa`) reutilizado
   em request e response, schemas de erro, 5 exemplos de erro incluindo um
   comentário YAML com duas opções (400 vs 422) pra `completedAt` manual,
   nota de "sem paginação" no `info.description`. Validado com
   `swagger-cli` e `redocly lint`.

4. **Resultado** — Aceito com edições. Ver `ai/revisoes.md` entrada 1: os
   schemas single-shape (request=response) foram identificados como modelo
   de entidade de banco vazando pro contrato, e corrigidos numa rodada
   seguinte.

---

## 2. Refinamento do contrato: separação request/response schemas e decisão completedAt=400

1. **Contexto** — Refinamento do `spec/openapi.yaml` já commitado, depois
   de eu revisar a v1 e identificar dois problemas: (a) schemas
   single-shape vazando forma de persistência pro contrato, (b) decisão em
   aberto sobre `completedAt` manual (400 vs 422) que eu tinha deixado como
   comentário YAML pra eu mesmo decidir.

2. **Prompt (resumo)** — Duas instruções diretas, sem espaço pra
   interpretação:
   - Decisão 1: `completedAt` submetido manualmente = `400`, não `422`,
     porque é campo não-gravável pelo client — presença no body torna o
     request malformado no nível do contrato, não uma violação de regra de
     negócio. Remover o comentário com as duas opções e documentar a
     decisão na descrição do response 400 de `POST /projetos/{id}/tarefas`
     e `PATCH /tarefas/{id}`.
   - Decisão 2: separar schema de entidade em `CreateXRequest` /
     `UpdateXRequest` / `X` (response), com `additionalProperties: false`
     nos requests pra rejeitar campo desconhecido (inclusive `completedAt`)
     com 400. Religar cada endpoint ao schema correto.
   - Explícito: não mudar mais nada.

3. **O que a IA produziu** — Edições em `spec/openapi.yaml`:
   - Renomeou `Projeto`→`Project`, `ProjetoCreate`→`CreateProjectRequest`,
     `ProjetoUpdate`→`UpdateProjectRequest`, `Tarefa`→`Task`,
     `TarefaCreate`→`CreateTaskRequest`, `TarefaUpdate`→`UpdateTaskRequest`,
     atualizando todos os `$ref` nos paths.
   - Removeu o comentário de decisão aberta em `UpdateTaskRequest` e
     reescreveu a descrição afirmando a decisão tomada (400).
   - Trocou o `400` de `POST /projetos/{id}/tarefas` e
     `PATCH /tarefas/{id}` de `$ref` genérico pra resposta inline
     explicando o caso do `completedAt`.
   - Removeu o exemplo `TaskCompletedAtManual` (422) e o `type` URI
     `task-completed-at-readonly`, já que a regra virou 400.
   - Manteve intactos os schemas de enum/parâmetro (`ProjetoStatus`,
     `TarefaStatus`, `TarefaPriority`, `ProjetoId`, `TarefaId`), por não
     serem schemas de entidade.
   - Revalidou com `swagger-cli validate` (válido) e conferiu que o
     `redocly lint` não introduziu erros novos (mesmos 9 avisos de
     política — security/servers/license — de antes, não relacionados a
     esta mudança).

4. **Resultado** — Aceito como veio, sem nova rodada de correção. Este
   prompt já era, ele mesmo, a correção de dois problemas da v1 — ver
   `ai/revisoes.md` entrada 1 pro racional completo da separação
   request/response e da decisão completedAt=400.

---

## 3. Revisão adversarial v1 do `openapi.yaml` e resoluções

1. **Contexto** — Depois da rodada 2 (separação request/response +
   completedAt=400), pedi uma revisão adversarial dedicada do
   `spec/openapi.yaml` via subagente `spec-reviewer`, antes de considerar o
   contrato pronto pra implementação. Resultado: 19 achados agrupados por
   severidade. Numa segunda mensagem, resolvi todos eles em bloco.

2. **Prompt (resumo)** — Primeiro prompt: "usar o agente spec-reviewer pra
   revisar `spec/openapi.yaml` de novo, achados agrupados por severidade."
   Segundo prompt: lista fechada de resoluções pra cada achado — 7 fixes
   mecânicos (ex.: 400 pra UUID malformado em todo endpoint `{id}`,
   exemplos por recurso de `NotFound`, exemplos de `ValidationProblemDetails`
   com `errors[]`, `minLength: 1` em name/title, consolidar 400s inline no
   componente `BadRequest`, remover `default: about:blank` órfão, `format:
   uri` no header `Location`) e 9 decisões de modelagem (adicionar `GET
   /tarefas/{id}`; transições estritamente sequenciais com skip-ahead=422 e
   PATCH same-state=200 no-op; nova regra de negócio 6 — status de tarefa
   travado se projeto arquivado; renumerar regras pra ordem do desafio; não
   adicionar `DELETE /projetos`, documentar ausência como escopo
   deliberado; PATCH atômico; convenção nomes-EN/paths-PT/mensagens-PT;
   `maxLength: 2000` em description; bloco `servers:` + nota de
   autenticação fora de escopo). Fechado com "não mudar mais nada" e pedido
   de resumo de cada mudança.

3. **O que a IA produziu** —
   - `spec/openapi.yaml` reescrito por completo (via `Write`, não edits
     pontuais, dado o volume de mudanças interdependentes): renomeou
     `ProjetoStatus`→`ProjectStatus`, `TarefaStatus`→`TaskStatus`; separou
     `NotFound` genérico em `ProjetoNotFound`/`TarefaNotFound` com exemplo
     correto por recurso; adicionou `GET /tarefas/{id}`; consolidou 400s
     inline no componente `BadRequest` com 5 exemplos Portuguese
     (`errors[]` com campo raso: `name`, `title`, `completedAt`, `id`,
     `status`); adicionou regra de negócio 6 nova (tarefa de projeto
     arquivado não muda status) com `type` URI e exemplo próprios;
     traduziu `title`/`detail` de todos os exemplos de erro pra
     português; adicionou seção "Business rules" numerada (1–6) e novas
     notas de escopo (sem `DELETE /projetos`, PATCH atômico, same-state
     no-op, convenção de nomenclatura, sem autenticação) no
     `info.description`; adicionou bloco `servers:`.
   - `CLAUDE.md` — lista de invariantes renumerada pra bater com a nova
     ordem do spec, regra 6 adicionada, regra 3 (completedAt) anotada como
     400-não-422.
   - Validado com `swagger-cli validate` (válido) e `redocly lint`
     (mesmos 9 avisos de política — `security-defined`, sem relação com a
     mudança — nenhum erro novo introduzido).

4. **Resultado** — Aceito como veio. O achado mais relevante da revisão
   (furo de invariante retroativo: tarefa `pending` de projeto arquivado
   podia virar `in_progress` depois do arquivamento, violando a regra 1
   de fato) e o racional completo de todas as resoluções estão em
   `ai/revisoes.md` entrada 2.

---

## 4. Rodada 2 de revisão adversarial do `openapi.yaml`

1. **Contexto** — Depois de aplicar as 19 resoluções da rodada 1 (entrada
   3 acima), pedi uma segunda passada do subagente `spec-reviewer` sobre o
   `spec/openapi.yaml` já corrigido — checando se as próprias correções
   não introduziram contradição ou achado novo — antes de dar o contrato
   por fechado.

2. **Prompt (resumo)** — Primeiro prompt: rodar `spec-reviewer` de novo,
   achados agrupados por severidade, sem assumir que os 19 achados
   anteriores ainda valiam (re-derivar do zero), cross-checar contra os 6
   invariantes renumerados do `CLAUDE.md`. Retornou 16 achados. Segundo
   prompt: lista fechada de resoluções — 4 decisões de modelagem (no-op
   tem precedência sobre a regra 6; regra 6 reformulada como blacklist de
   status, não whitelist de title/description; parágrafo de "precedência
   de validação" 400→404→422 no `info.description`; desarquivamento
   `archived→active` permitido explicitamente) e 7 fixes mecânicos
   (renomear `TarefaPriority`→`TaskPriority`, `ProjetoId`→`ProjectIdParam`,
   `TarefaId`→`TaskIdParam`, `ProjetoNotFound`→`ProjectNotFound`,
   `TarefaNotFound`→`TaskNotFound`; convenção de nome de exemplo
   PascalCase/camelCase; desmembrar `BadRequest` em três componentes
   (`InvalidRequestBody`/`InvalidPathParameter`/`InvalidQueryParameter`)
   com `type` URI e exemplos próprios por endpoint; pointer de regra 4 no
   `POST /projetos/{id}/tarefas`; "invariant #3"→"business rule #3";
   `Location`/`instance` como `uri-reference` relativo; exemplo de filtro
   `priority` inválido; semântica AND pra filtros combinados; exemplo de
   PATCH `{}` vazio). Fechado com "não mudar mais nada" e pedido de resumo.

3. **O que a IA produziu** — `spec/openapi.yaml` reescrito por completo
   (via `Write`): renomeou os 5 componentes listados e todos os `$ref`;
   dividiu `BadRequest` em 3 response components com `type` URIs distintos
   (`invalid-request-body`, `invalid-path-parameter`,
   `invalid-query-parameter`), religando cada endpoint só às causas de 400
   que ele pode de fato produzir (endpoints com duas causas, ex. `PATCH`
   com path+body, usam `oneOf` combinando os dois schemas); moveu todos os
   payloads de exemplo pra `components.examples` PascalCase, com chave
   camelCase no ponto de uso; reescreveu a regra 6 como blacklist e
   adicionou a frase de precedência do no-op tanto na decisão de escopo
   quanto no texto da regra e na descrição do `PATCH /tarefas/{id}`;
   adicionou parágrafo de precedência de validação e bullet de
   desarquivamento permitido no `info.description`; trocou `instance` de
   todo exemplo pra path relativo e o schema `ProblemDetails.instance` de
   `format: uri` pra `format: uri-reference` (mais correto pra RFC 7807);
   adicionou exemplos `InvalidPriorityFilter` e `EmptyPatchBody`. Validado
   com `swagger-cli validate` (válido) e `redocly lint` (confirmado: zero
   ocorrências da regra `no-invalid-media-type-examples`; os 9 erros + 3
   avisos restantes são só as políticas de opinião já esperadas —
   `security-defined`, `info-license`, `no-server-example.com` —,
   nenhuma nova).

4. **Resultado** — Aceito como veio. As duas contradições mais relevantes
   que essa rodada achou — nas minhas próprias correções da rodada 1: (a)
   no-op vs. regra 6 sem precedência definida, (b) o `BadRequest`
   consolidado que resolveu "faltam exemplos" só pra criar "exemplos
   irrelevantes por endpoint com `type` URI errado no lugar" — e o
   racional completo de cada resolução estão em `ai/revisoes.md`
   entrada 3.

---

## 5. Rodada 3 de revisão adversarial — reestruturação dos 400s por contexto e correção de drift do `CLAUDE.md`

1. **Contexto** — Terceira passada do subagente `spec-reviewer` sobre o
   `spec/openapi.yaml`, depois das resoluções da rodada 2 (entrada 4
   acima). Objetivo: checar se a correção anterior (divisão do `BadRequest`
   em 3 componentes por categoria — path/query/body) tinha, ela mesma,
   introduzido problema novo, e cross-checar o `CLAUDE.md` contra o spec.

2. **Prompt (resumo)** — Primeiro prompt: rodar `spec-reviewer` de novo
   sobre o spec pós-rodada-2, re-derivando achados do zero, cross-check
   completo contra os 6 invariantes do `CLAUDE.md`. Retornou 8 achados,
   incluindo um crítico (`InvalidQueryParameter`, usado só por `GET
   /projetos`, documentava exemplo de filtro `priority` que esse endpoint
   nem tem, com enum de status errado — de tarefa, não de projeto) e um
   achado maior de que o `CLAUDE.md` tinha divergido do spec (regra 6 sem
   `priority` na lista de campos editáveis). Segundo prompt, em duas
   partes: (1) pedido explícito de corrigir `CLAUDE.md` primeiro e mostrar
   o diff antes de continuar — sem confiar só no resumo da IA; (2)
   resoluções pro spec — no-op reformulado como *por campo* (não por
   request inteiro); troca dos 3 componentes de 400 por categoria por 7
   componentes por *contexto de endpoint* (`PathParamBadRequest`,
   `ProjectBodyBadRequest`, `ProjectPatchBodyBadRequest`,
   `TaskCreateBodyBadRequest`, `TaskPatchBodyBadRequest`,
   `ProjectStatusFilterBadRequest`, `TaskFiltersBadRequest`); remoção de
   `instance` de exemplos reutilizáveis (mantido só nos 5 exemplos de
   regra de negócio, de uso único); fatoração do `oneOf` duplicado 3x num
   schema nomeado `AnyProblemDetails`; renomeação de todos os
   `operationId`s pra inglês. Fechado com "não mudar mais nada" e pedido de
   resumo.

3. **O que a IA produziu** — `CLAUDE.md`: regra 6 corrigida pra incluir
   `priority` na lista de campos editáveis, batendo com o spec; diff
   mostrado antes de prosseguir, como pedido. `spec/openapi.yaml`
   reescrito por completo (via `Write`) e depois ajustado com `Edit`
   pontual: os 7 componentes por contexto substituíram os 3 por categoria;
   endpoints com duas causas de 400 possíveis (path+body ou path+query)
   ganharam um componente próprio já combinando as duas causas (schema
   `AnyProblemDetails` quando envolve `ValidationProblemDetails`, ou
   `ProblemDetails` simples quando as duas causas compartilham o mesmo
   formato); todos os 9 `operationId`s renomeados
   (`createProjeto→createProject`, etc.); reformulação do no-op como por
   campo, refletida na decisão de escopo e nas descrições de
   `PATCH /projetos/{id}` e `PATCH /tarefas/{id}`. Na primeira tentativa de
   escrita, 4 dos 7 novos componentes ficaram órfãos (`redocly lint`
   acusou `no-unused-components: 8` ocorrências) porque o conteúdo tinha
   sido só inlinado no path, sem `$ref` de volta pro componente nomeado —
   corrigido numa segunda leva de edits fazendo cada componente ser a
   resposta completa e real do endpoint (com `$ref` direto), até zerar
   `no-unused-components` e `no-invalid-media-type-examples`.

4. **Resultado** — Aceito com uma correção própria no meio do processo (a
   IA detectou e consertou sozinha o problema dos componentes órfãos antes
   de reportar como concluído, via `redocly lint`). O racional completo —
   inclusive a admissão de que o `CLAUDE.md` "corrigido" na rodada 1 não
   tinha sido de fato sincronizado, e que a consolidação de 400s da rodada
   2 foi uma sobre-correção — está em `ai/revisoes.md` entrada 4.

---

## 6. Rodada 4 (final) de revisão adversarial do `openapi.yaml`

1. **Contexto** — Quarta passada do subagente `spec-reviewer` sobre o
   `spec/openapi.yaml`, depois das resoluções da rodada 3 (entrada 5
   acima). Objetivo: checar se a reestruturação dos 400s por contexto de
   endpoint (rodada 3) tinha, ela mesma, introduzido problema novo, e se
   havia alguma outra ambiguidade de regra de negócio sobrevivendo depois
   de 3 rodadas.

2. **Prompt (resumo)** — Primeiro prompt: rodar `spec-reviewer` de novo,
   re-derivando achados do zero, sem assumir nada das 3 rodadas anteriores,
   pedindo explicitamente pra dizer se o spec já estava genuinamente limpo
   em vez de inventar achado pra preencher relatório. Retornou 1 blocker
   (regras de negócio 5 e 6 podem colidir no mesmo request — toda tarefa
   `pending`/`done` de projeto arquivado, ao tentar mudar `status`, viola as
   duas regras ao mesmo tempo, e nada dizia qual vencia) e 1 major (o
   schema `AnyProblemDetails`, criado na rodada 3 pra combinar `path
   inválido` + `corpo inválido` num único componente via `oneOf`, não é de
   fato exclusivo — `ProblemDetails` não tem `additionalProperties: false`,
   então um payload com `errors[]` valida contra os dois ramos do `oneOf`
   ao mesmo tempo, quebrando a semântica exigida por `oneOf` e por
   validadores estritos como o `committee` gem que o `CLAUDE.md` já
   compromete o Rails a usar). Mais 4 achados menores (exemplos de body-400
   não exaustivos, `instance` hardcoded errado em 2 dos 4 usos de
   `ProjectNotFound`, sem política de tie-break pra múltiplos filtros
   inválidos, `ProjectNotFound`/`TaskNotFound` usando `example:` singular em
   vez do padrão `examples:`-map do resto do arquivo). Segundo prompt:
   resoluções pra todos os 6 — precedência explícita regra 6 antes de regra
   5 dentro do estágio 422 (generalizada como princípio: precondição de
   estado antes de regra de transição/valor); eliminação total do
   `AnyProblemDetails`, com os 3 componentes que o usavam virando
   `ValidationProblemDetails` puro; 2 exemplos novos (valor de enum
   inválido em `priority`, campo não aceito na criação); `instance` removido
   de `ProjectNotFound`/`TaskNotFound`; política de erro documentada (body
   reporta todos os campos em `errors[]`, query reporta só o primeiro,
   ordem documentada); `ProjectNotFound`/`TaskNotFound` convertidos pro
   padrão `examples:`-map. Fechado com "não mudar mais nada".

3. **O que a IA produziu** — Edições pontuais (não reescrita completa) em
   `spec/openapi.yaml`: parágrafo de "Validation precedence" ganhou a regra
   de precedência 422-interna; regra de negócio 6 e as duas bullets da
   descrição de `PATCH /tarefas/{id}` repetem a mesma precedência, pra não
   deixar a ambiguidade se esconder atrás de um único lugar; schema
   `AnyProblemDetails` deletado; os 3 componentes de response que o usavam
   (`ProjectPatchBodyBadRequest`, `TaskCreateBodyBadRequest`,
   `TaskPatchBodyBadRequest`) passaram a usar `ValidationProblemDetails`
   puro. No meio da correção, a própria tentativa de reaproveitar o exemplo
   `InvalidUuidPathParameter` (adicionando `errors[]` nele pra caber no
   novo schema) quebrou a validação nos 2 lugares onde ele ainda era usado
   como `ProblemDetails` puro (`redocly lint` acusou
   `no-invalid-media-type-examples: 4`) — corrigido separando em dois
   exemplos (`InvalidUuidPathParameter` sem `errors`,
   `InvalidPathParameterFieldError` com `errors`), cada um usado só onde o
   schema bate de verdade. Adicionou 2 exemplos novos
   (`TaskPriorityInvalid`, `ProjectStatusSubmittedOnCreate`), nota de
   "exemplos ilustrativos, não exaustivos" nos 4 componentes de body-400,
   removeu `instance` de `ProjectNotFound`/`TaskNotFound` e os converteu
   pro padrão `examples:`-map (novos `components.examples.ProjectNotFound`
   / `TaskNotFound`, sem `instance`). Validado com `swagger-cli validate`
   (válido) e `redocly lint`: 0 `no-invalid-media-type-examples`, 0
   `no-unused-components`, mesmos 9 erros + 3 avisos de política
   pré-existentes (sem auth, sem license, servers localhost — todos
   deliberados).

4. **Resultado** — Aceito com uma correção própria no meio do processo (o
   erro do exemplo reaproveitado incorretamente, pego pelo próprio
   `redocly lint` antes de reportar como concluído — mesmo padrão da
   rodada 3). Racional completo, incluindo a admissão de que documentar
   duas regras de negócio como itens paralelos sem checar sobreposição
   criou a mesma classe de bug que o no-op vs. regra 6 da rodada 2, está em
   `ai/revisoes.md` entrada 5. Essa entrada também registra um bug
   encontrado à parte nesta sessão: as seções 4/5 da entrada 2 de
   `ai/revisoes.md` tinham sido fisicamente deslocadas pro fim do arquivo
   numa edição anterior (bug de posicionamento do `Edit`, não do
   conteúdo) — corrigido antes de escrever a entrada 5.

---

## 7. Rodada 5 e encerramento da fase de especificação

1. **Contexto** — Quinta e última passada planejada do subagente
   `spec-reviewer` sobre o `spec/openapi.yaml`, depois das resoluções da
   rodada 4 (entrada 6 acima). Desta vez pedi explicitamente que o agente
   aplicasse um critério de parada declarado: só tratar achado como
   bloqueante se mudasse comportamento observável entre uma implementação
   Quarkus e uma Rails construídas estritamente a partir do spec; achado
   que fosse só nitpick de documentação deveria ser sinalizado como não
   bloqueante, não usado pra inventar mais uma rodada.

2. **Prompt (resumo)** — Primeiro prompt: rodar `spec-reviewer` de novo,
   re-derivando achados do zero, checando especificamente se a precedência
   422 (regra 6 antes de regra 5, resolvida na rodada 4) cobria todas as
   combinações possíveis, se a separação `ValidationProblemDetails` vs.
   `ProblemDetails` continuava consistente, se sobrava algum componente
   órfão ou referência morta a coisa já removida (`AnyProblemDetails`,
   nomes antigos de exemplo), e — o pedido novo — aplicar o critério de
   parada acima explicitamente no relatório final. Retornou 1 blocker (sem
   sub-ordem definida entre path parameter, query parameter e corpo dentro
   do próprio estágio `400`, pros endpoints que têm mais de uma categoria
   — ambiguidade estrutural igual à da rodada 4, um nível abaixo) e mais 2
   achados menores só que ainda comportamentais (header `Location` sem
   `required: true` nos dois `201`; nenhuma decisão sobre parâmetro de
   query desconhecido), além de itens explicitamente classificados como
   "abaixo do critério de parada" (presença inconsistente de `instance`
   opcional, `description` nullable-mas-não-required). Segundo prompt:
   resoluções pra sub-ordem 400 (path → query → body, fail-fast, com
   justificativa de que path identifica o recurso e a ordem já é o
   comportamento nativo de JAX-RS e Rails), `required: true` nos dois
   `Location`, decisão documentada de ignorar query param desconhecido
   (tolerant reader). Explícito: aceitar os itens abaixo do critério de
   parada como estão, não mexer neles.

3. **O que a IA produziu** — Edições pontuais em `spec/openapi.yaml`: bullet
   de "Validation precedence" ganhou a sub-ordem dentro do `400` com
   justificativa; bullet de "Error-reporting policy" ajustado pra deixar
   claro que só vale dentro do estágio de corpo; bullet novo de "query
   parameter desconhecido é ignorado"; `required: true` adicionado nos
   headers `Location` de `POST /projetos` e `POST
   /projetos/{id}/tarefas`; as 4 operações que têm mais de uma categoria de
   400 possível (`PATCH /projetos/{id}`, `POST /projetos/{id}/tarefas`,
   `GET /projetos/{id}/tarefas`, `PATCH /tarefas/{id}`) ganharam uma frase
   na própria descrição apontando pra sub-ordem central, em vez de deixar
   só no `info.description` geral. Validado com `swagger-cli validate`
   (válido) e `redocly lint` (0 `no-invalid-media-type-examples`, 0
   `no-unused-components`, mesmos 9 erros + 3 avisos de política de
   sempre).

4. **Resultado** — Aceito como veio. Nesta rodada o próprio agente de
   revisão, ao verificar o `CLAUDE.md`, percebeu que a cópia injetada no
   contexto da sessão estava desatualizada em relação ao arquivo real no
   disco — releu o arquivo antes de reportar e confirmou que o repositório
   já estava correto, sinalizando a diferença como artefato de contexto de
   sessão, não como defeito real. Com as 3 correções desta rodada
   aplicadas, o critério de parada foi atingido: a fase de especificação
   do `openapi.yaml` está encerrada após 5 rodadas de revisão adversarial.
   Racional completo, incluindo a admissão de que a ambiguidade da rodada 4
   (colisão de regras) tinha só migrado de estágio (422 → 400) em vez de
   ter sido eliminada de fato, está em `ai/revisoes.md` entrada 6.

---

## 8. Rodada 6 — garantia de ordenação nas listagens

1. **Contexto** — Sexta passada do subagente `spec-reviewer` sobre
   `spec/openapi.yaml`, pedida pelo usuário mesmo depois de eu ter
   declarado a fase de especificação "encerrada" ao fim da rodada 5
   (entrada 7 de `ai/prompts.md`). Mesmo critério de parada da rodada 5
   reaplicado: achado só bloqueia se muda comportamento observável entre
   Quarkus e Rails; nitpick de doc não bloqueia.

2. **Prompt (resumo)** — Primeiro prompt: rodar `spec-reviewer` de novo,
   re-derivando achados do zero, sem assumir a rodada 5 como definitiva,
   checando se a sub-ordem 400 (path→query→body) da rodada 5 realmente
   cobria as 4 operações certas, se sobrava componente órfão, e se
   `CLAUDE.md` (lido direto do disco, não do contexto da sessão) continuava
   batendo com o spec. Retornou 1 blocker novo: nenhum dos dois endpoints
   de listagem (`GET /projetos`, `GET /projetos/{id}/tarefas`) declarava
   ordem de retorno dos itens do array — JPA sem `ORDER BY` e ActiveRecord
   sem `.order` herdam a ordem arbitrária de cada banco, produzindo corpo
   de resposta observavelmente diferente entre os dois stacks pro mesmo
   conjunto de dados. Mais 3 achados classificados pelo próprio agente como
   abaixo do critério de parada (ambiguidade presença-vs-null em campo
   nullable-mas-não-required; `instance` presente em exemplos 404/422 mas
   ausente nos de 400; cross-reference genérico "ver info.description" em
   vez de nomear a bullet específica). Segundo prompt: resolver só o
   blocker — `createdAt` ascendente com `id` ascendente como desempate
   (ordem total, já que `createdAt` sozinho pode colidir em fixtures de
   teste rodando em lote), documentado como parte do contrato (coberto por
   teste de contrato, não deixado pro default de cada banco) na bullet de
   "No pagination" e na descrição de cada endpoint de listagem. Aceitar os
   outros 3 achados como estão, explicitamente.

3. **O que a IA produziu** — Edições pontuais em `spec/openapi.yaml`:
   bullet "No pagination" ganhou frase sobre ordenação determinística
   fazer parte do contrato; descrição de `GET /projetos` e `GET
   /projetos/{id}/tarefas` ganhou "ordered by `createdAt` ascending, `id`
   ascending as tie-breaker". Validado com `swagger-cli validate` (válido)
   e `redocly lint` (0 `no-invalid-media-type-examples`, 0
   `no-unused-components`).

4. **Resultado** — Aceito como veio. O ponto mais relevante desta rodada
   não é a correção em si (trivial, uma frase em três lugares) — é que o
   gap sobreviveu a 5 rodadas anteriores (19+8+6+16+6 achados) porque
   nenhuma delas cobria "propriedade que o spec nunca tentou declarar",
   só "declaração que o spec tentou fazer e ficou incompleta/contraditória".
   Racional completo, incluindo a reflexão sobre por que esse tipo de gap
   é estruturalmente mais fácil de escapar de revisão adversarial, está em
   `ai/revisoes.md` entrada 7.

---

## 9. Varredura de ausências e congelamento da spec

1. **Contexto** — Depois da rodada 6 (entrada 8 acima), pedido de uma
   verificação final — explicitamente NÃO uma rodada de revisão adversarial
   genérica via `spec-reviewer`, e sim uma varredura dirigida por
   checklist fechado, direto por mim (sem subagente), sobre propriedades
   transversais que a classe de revisão anterior (comparar o que o texto
   diz contra o que deveria dizer) tende a não cobrir.

2. **Prompt (resumo)** — Checklist explícito de 5 itens pra escanear só em
   `spec/openapi.yaml`, sem virar rodada completa: consistência de
   content-type (`application/json` vs `application/problem+json`),
   formato/timezone de datetime, casing de UUID no output,
   política null-vs-absent em campo opcional de response, tratamento de
   trailing slash. Pedido de reportar só risco de divergência
   comportamental real, sem nitpick de estilo, e responder "converged" se
   nada fosse achado. Depois, com 2 achados reais na mão (timezone,
   trailing slash), pedido das duas correções finais e declaração
   explícita de que a spec seria congelada em seguida.

3. **O que a IA produziu** — Verificação feita via `grep` direto no
   arquivo (sem subagente `spec-reviewer`, mais barato pra um check
   fechado desse tamanho): confirmou que content-type já era 100%
   consistente (25 blocos `content:`, sucesso sempre `application/json`,
   erro sempre `application/problem+json`) e que UUID/casing não tinha
   risco prático (ambos ecossistemas produzem lowercase por padrão) — não
   reportado como achado. Reportou 2 riscos reais: timezone de datetime
   sem política declarada (Jackson normalmente emite `Z`, Rails por padrão
   emite offset da zona configurada da app — mesmo instante, string
   diferente) e trailing slash sem comportamento definido (RESTEasy/Rails
   não normalizam igual por padrão). Null-vs-absent foi mencionado como já
   conhecido/aceito na rodada 6, não como achado novo. Na segunda etapa,
   editou `spec/openapi.yaml`: nova bullet de decisão de escopo pra
   datetime (UTC, sufixo `Z` literal, offset nunca emitido) com
   `description` repetindo a regra em `createdAt` (`Project`, `Task`) e
   `completedAt` (`Task`); nova bullet de escopo pra trailing slash (path
   canônico sem barra final; forma não-canônica fora do contrato, sem
   comportamento especificado, excluída de teste de conformidade).
   Validado com `swagger-cli validate` (válido) e `redocly lint` (0
   `no-invalid-media-type-examples`, 0 `no-unused-components`).

4. **Resultado** — Aceito como veio. Spec declarada congelada depois desta
   correção — 6 rodadas de revisão adversarial completa mais 1 varredura
   dirigida por checklist. Racional completo, incluindo a confirmação de
   que a lição da entrada 7 generaliza (ausência exige checklist
   deliberado, não releitura), está em `ai/revisoes.md` entrada 8.
