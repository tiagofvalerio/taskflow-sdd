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

---

## 10. Consolidação das decisões de design em `docs/decisoes.md`

1. **Contexto** — Fase de especificação encerrada (spec congelada, entrada 9
   acima). Pedido de consolidar, num único documento em português, o
   racional honesto por trás de todas as decisões de design já tomadas e
   defendidas em `spec/openapi.yaml`, `CLAUDE.md` e `ai/revisoes.md` — não
   uma nova rodada de decisão, e sim a redação final do que já tinha sido
   decidido e disperso por várias fontes.

2. **Prompt (resumo)** — Lista fechada de 9 itens a cobrir, um por seção,
   com pedido explícito de tom honesto (não marketing) e nomeação de
   alternativa rejeitada onde houve uma: (1) racional spec-first e
   prevenção de drift via testes de contrato + validação de schema em CI
   pras duas stacks + diff da spec gerada por `smallrye` contra a manual;
   (2) arquitetura assimétrica deliberada (hexagonal Quarkus vs. Rails-way
   idiomático) e onde cada uma das 6 regras de negócio vive em cada stack;
   (3) modelo de erros RFC 7807, taxonomia de 400 por contexto, 400 vs 422
   incluindo `completedAt` manual; (4) modelo de precedência completo
   (400→404→422, sub-ordens internas); (5) regra de negócio 6 (furo do
   invariante retroativo) e semântica de no-op por campo; (6) convenções
   transversais (ordenação, UTC/`Z`, trailing slash, query param
   desconhecido, convenção de nomes); (7) Postgres via Testcontainers como
   desvio justificado do SQLite/in-memory do enunciado; (8) pirâmide de
   testes completa; (9) processo de revisão da spec (6 rodadas, contagem de
   achados 19→16→8→6→4→1→2, critério de parada, lição metodológica).
   Fechado com "pergunte antes de assumir algo não decidido".

3. **O que a IA produziu** — Leu `spec/openapi.yaml` (completo),
   `ai/revisoes.md` (completo, 8 entradas), `ai/prompts.md` e `ai/skills.md`
   antes de escrever, pra basear cada seção em decisão já registrada, não em
   invenção. Criou `docs/decisoes.md` do zero: 9 seções, uma por item do
   pedido, cada uma com a decisão, o racional, e — onde aplicável —
   alternativa rejeitada nomeada explicitamente (code-first em vez de
   spec-first no item 1; hexagonal simétrico nas duas stacks no item 2).
   Não pediu esclarecimento adicional ao usuário porque os 9 itens do
   próprio prompt já traziam o conteúdo específico a documentar (inclusive
   detalhes não escritos em nenhum arquivo do repo ainda, como o diff via
   `smallrye` e o uso de Schemathesis/`mutant` em CI) — tratados como
   decisão relatada pelo usuário no próprio prompt, não como suposição da
   IA. Confirmou via `ls`/`find` que `quarkus-impl/`, `rails-impl/` e
   `.github/` ainda estão vazios, pra não inventar detalhe de implementação
   inexistente nas seções 2, 7 e 8.

4. **Resultado** — Aceito como veio, sem edição. Ao rodar `/revisar` em
   seguida, não havia diff nenhum no arquivo (nunca editado após a escrita)
   e o usuário confirmou explicitamente que a revisão dele foi só de
   leitura ("ok to begin development") — sem correção a registrar, por isso
   nenhuma entrada nova foi criada em `ai/revisoes.md` para esta tarefa.

---

## 11. Scaffold do projeto Quarkus (`quarkus-impl/`)

1. **Contexto** — Início da fase de implementação Quarkus. Scaffold do
   projeto Maven em `quarkus-impl/`, sem nenhum código de domínio (SDD:
   spec congelada continua sendo a única fonte de verdade).

2. **Prompt (resumo)** — Pedido pra criar scaffold Quarkus 3.x (Maven)
   com alvo Java 25, mas **antes de gerar**: verificar qual é a última
   versão estável do Quarkus e se ela suporta Java 25 como alvo de
   compilação/runtime; se não suportasse, propor a maior opção
   LTS-compatível e aguardar decisão. Extensões pedidas: `rest-jackson`,
   `hibernate-orm-panache`, `jdbc-postgresql`, `hibernate-validator`,
   `smallrye-openapi`. Deps de teste: REST Assured, JUnit 5,
   Testcontainers PostgreSQL. Sem código de domínio.

3. **O que a IA produziu** —
   - Pesquisou antes de gerar (condição do prompt): Java 25 tem suporte
     pleno no Quarkus atual; última estável na pesquisa = 3.36.3, LTS =
     3.33.2.1. Usuário escolheu 3.36.3 (via pergunta estruturada) e também
     escolheu instalar JDK 25 real (`brew install openjdk@25`), já que a
     máquina só tinha JDK 21 e 26.
   - Instalou JDK 25 e registrou via symlink em nível de usuário
     (`~/Library/Java/JavaVirtualMachines/`), sem sudo.
   - Gerou o projeto com `quarkus-maven-plugin:create -DnoCode
     -DjavaVersion=25` — o registry do gerador resolveu a plataforma pra
     **3.37.1** (mais nova que a pesquisa indicava; mantida por ser a
     estável mais recente, desvio informado ao usuário).
   - `pom.xml`: `com.taskflow:quarkus-impl`, `maven.compiler.release=25`,
     packaging `quarkus` (estilo novo), artefato de teste `quarkus-junit`
     (novo no 3.37), REST Assured e Testcontainers adicionados à mão.
   - Esqueleto hexagonal só com pacotes + `.gitkeep`: `domain/`,
     `application/`, `adapter/in/rest/`, `adapter/out/persistence/`.
   - `application.properties` mínimo: `db-kind=postgresql` (Dev Services)
     e título do OpenAPI.
   - **Problema encontrado e corrigido no caminho**: Testcontainers 1.21.x
     (plano original) quebrou o dev mode com `ClassNotFoundException:
     org.junit.rules.TestRule` — a plataforma Quarkus 3.37 migrou pra
     Testcontainers **2.x** (artifact IDs novos, sem JUnit 4). Corrigido
     usando `testcontainers-postgresql` e `testcontainers-junit-jupiter`
     2.0.5, alinhados à plataforma.
   - Verificação: `./mvnw verify` BUILD SUCCESS em JDK 25; `quarkus:dev`
     subiu Dev Services com `postgres:18`, `/q/openapi` servindo OpenAPI
     3.1 e dev-ui respondendo 200.

4. **Resultado** — Aceito com ajustes feitos durante a própria sessão
   (não houve edição manual do usuário): versão da plataforma 3.37.1 em
   vez de 3.36.3 aprovada no plano, e Testcontainers 2.x em vez de 1.x.
   Desvios registrados em `ai/revisoes.md` (entrada correspondente à
   sessão de scaffold). Nada commitado ainda.

## 12. Camada de domínio Quarkus — 6 regras como métodos de intenção

1. **Contexto** — Primeira camada real da implementação Quarkus
   (pós-scaffold): domínio hexagonal puro em
   `quarkus-impl/src/main/java/com/taskflow/domain/`, e nada além dele.
   Commit `a1e6e5d`.

2. **Prompt (resumo)** — Implementar SOMENTE a camada de domínio:
   entidades `Project`/`Task`, enums de status/prioridade, exceções de
   domínio. Restrições explícitas: zero imports de framework, sem
   setters públicos em campos de invariante, as 6 regras do
   `CLAUDE.md`/spec como métodos que revelam intenção
   (`Project.archive()`, `Project.addTask()`, `Task.startProgress()`,
   `Task.complete()` setando `completedAt` internamente,
   `Task.canBeDeleted()`, guarda da regra 6), transições estritamente
   sequenciais e precedência regra 6 antes da regra 5. Condição de
   processo: **antes de escrever código**, propor como `Task` conhece o
   estado arquivado do projeto dono (regra 6) e aguardar aprovação —
   regra no domínio, nunca em service. Rodou em plan mode.

3. **O que a IA produziu** —
   - Leu spec congelada + `docs/decisoes.md`, apresentou 3 desenhos via
     pergunta estruturada: fatos como parâmetros (recomendado), passar
     `Project` inteiro, ou aggregate root com coleção bidirecional.
     Decisões do usuário: **fatos como parâmetros**
     (`task.startProgress(ProjectStatus)`,
     `project.archive(hasTaskInProgress)`), **ids tipados** (records
     `ProjectId`/`TaskId`) e **testes junto** no mesmo passo.
   - 7 classes de modelo + 6 exceções (`DomainRuleViolationException`
     sealed, subclasses espelhando 1:1 as chaves de exemplo da spec,
     pra mapeamento mecânico exceção → `type` URI no futuro adapter).
     Regra 3 sem exceção própria — é 400/schema, problema do adapter.
     Criação de `Task` só via `Project.addTask` (factory
     package-private). `reconstitute(...)` público pros adapters de
     persistência, sem checagem de regra.
   - 28 testes JUnit 5 puros (sem Quarkus, sem containers), incluindo o
     caso de precedência (tarefa `pending` + projeto arquivado +
     `complete()` viola 5 E 6 → erro da regra 6) e o no-op de arquivar
     projeto já arquivado.
   - Erro próprio detectado e corrigido em voo: classe gerada com typo
     `TaskStatusChangedBlockedException` (não batia com o nome do
     arquivo nem com o `permits` da sealed) — corrigida antes do
     primeiro build.
   - Auditoria pós-geração via subagente `domain-guardian`: limpa em
     todos os itens obrigatórios; duas lacunas prospectivas viraram a
     entrada 9 de `ai/revisoes.md` (ArchUnit antecipado com validação
     por classe-canário; PIT adiado deliberadamente).

4. **Resultado** — Aceito como gerado (typo corrigido pela própria IA
   antes do build; 28/28 testes verdes com JDK 25). Recomendação
   estrutural da auditoria aceita com timing arbitrado pelo usuário —
   ver `ai/revisoes.md`, entrada 9.

## 13. ArchUnit como enforcement de pureza hexagonal no build

1. **Contexto** — Sequência imediata da auditoria do `domain-guardian`
   sobre a camada de domínio (entrada 12; racional completo na entrada 9
   de `ai/revisoes.md`): a pureza hexagonal era garantida só por
   grep/convenção, com Panache e driver JDBC já no classpath de
   compilação — um `import jakarta.persistence.*` acidental no domínio
   compilaria sem erro.

2. **Prompt (resumo)** — Pedido pra adicionar ArchUnit AGORA, antes da
   fase de adapters: dependência em escopo de teste + classe de teste de
   arquitetura com duas regras — (1) nenhuma classe em `..domain..` pode
   depender de `jakarta.*`, `io.quarkus.*`, `org.hibernate.*`,
   `com.fasterxml.*` nem de pacotes `..adapter..`/`..application..`;
   (2) mesma restrição pra `..application..` quando existir, podendo
   depender do domínio mas nunca dos adapters. Rodando na fase de teste
   normal. PIT explicitamente mantido adiado pra etapa de mutação.

3. **O que a IA produziu** —
   - Consultou o Maven Central antes de fixar versão:
     `com.tngtech.archunit:archunit-junit5` **1.4.1** (test scope) em
     `quarkus-impl/pom.xml`.
   - `src/test/java/com/taskflow/architecture/HexagonalArchitectureTest.java`:
     `@AnalyzeClasses` sobre `com.taskflow` (sem testes), duas `@ArchTest`
     rules com os pacotes proibidos compartilhados numa constante;
     `allowEmptyShould(true)` na regra da aplicação, que ainda é só
     scaffold com `.gitkeep`.
   - Validação negativa da regra: classe-canário `PurityCanary` anotada
     com `@jakarta.persistence.Entity` criada temporariamente dentro do
     domínio — o build quebrou como esperado; canário e `.class` obsoleto
     em `target/` removidos em seguida.
   - Suíte completa verde: 30/30 (28 de domínio + 2 de arquitetura),
     ambas as regras executando na fase de teste padrão do Maven, sem
     plugin extra.

4. **Resultado** — Aceito como gerado. Origem da mudança documentada em
   `ai/revisoes.md`, entrada 9 (recomendação do agente auditor com
   timing decidido pelo usuário: ArchUnit antecipado, PIT adiado).

## 14. Camada de aplicação Quarkus — use cases espelhando as operações do contrato

1. **Contexto** — Segundo anel do hexágono (pós-domínio, pré-adapters):
   use cases e ports outbound em `com.taskflow.application`, com a regra
   ArchUnit da aplicação deixando de ser vácua. Commit `f390ee6`.

2. **Prompt (resumo)** — Criar a camada de aplicação: um use case por
   operação (com a escolha UpdateProject-genérico vs ArchiveProject
   dedicado delegada à IA pra propor), ports `ProjectRepository`/
   `TaskRepository` como interfaces puras na própria camada de
   aplicação. Restrições: use cases só orquestram — buscam fatos via
   ports (ex.: `existsByProjectIdAndStatus`) e passam ao domínio,
   conforme o desenho fatos-como-parâmetros já aprovado; nenhum use case
   re-implementa ou pré-checa regra de negócio; ports falam tipos de
   domínio (sem JPA, sem DTO); 404 sinalizado por exceção própria
   mapeada depois pelo adapter REST; testes unitários com fakes em
   memória (sem Mockito se fakes bastarem). Condição de processo: lista
   de testes primeiro, aguardando aprovação. Rodou em plan mode.

3. **O que a IA produziu** —
   - Duas decisões estruturadas antes do plano: (a) archive dentro de
     `UpdateProjectUseCase` — a spec não tem endpoint de arquivamento,
     `status` é só um dos campos patcháveis, e "um use case por
     operação" se refere a operações do contrato; (b) adição ao domínio
     de `Task.changeStatusTo(requested, ownerStatus)` — retrocesso pra
     `pending` não tem método de intenção, e o use case não pode lançar
     a regra 5 sozinho; regra 6 checada primeiro mesmo pra esse alvo.
   - Plano com lista de 41 testes aprovada em bloco via plan mode.
   - 18 arquivos main: 9 use cases (1:1 com operationIds da spec),
     2 ports (contrato de ordenação documentado no port, não deixado pro
     banco), `PatchField` tri-estado (ausente / null explícito / valor —
     `Optional` não expressa o estado do meio), 4 command records,
     2 exceções de not-found na camada de aplicação (existência de
     recurso é preocupação de persistência, não invariante de domínio).
   - Fakes em memória com cópias defensivas via `reconstitute` +
     9 classes de teste; no-op de status same-state resolvido no use
     case (semântica de PATCH da spec, não regra de negócio); PATCH
     atômico por construção (um único `save` no fim).
   - `allowEmptyShould` removido da regra ArchUnit da aplicação.
   - Auditoria (terceira passada do `domain-guardian`): sem violação de
     arquitetura; achado real de qualidade de teste — os dois testes de
     atomicidade eram vácuos por ordem de processamento. Corrigido antes
     do commit; suíte final 70/70.

4. **Resultado** — Aceito com uma correção pré-commit originada da
   auditoria (testes de atomicidade vácuos) — ver `ai/revisoes.md`,
   entrada 10. Arquitetura e desenho aceitos como propostos.

## 15. Adapters — persistência e REST com taxonomia de erros completa

1. **Contexto** — Fechamento do hexágono Quarkus: adapter de persistência
   (JPA/Panache implementando os ports) e adapter REST implementando
   `spec/openapi.yaml` exatamente, com a taxonomia RFC 7807 completa.
   Commits `c317388` (persistência) e `188435c` (REST).

2. **Prompt (resumo)** — Três blocos: (1) persistência com entidades JPA
   separadas do domínio, repositórios Panache implementando os ports,
   mappers com wrap/unwrap dos ids tipados, escolha Flyway-vs-Hibernate
   delegada à IA com justificativa de uma linha, colunas espelhando
   invariantes, ordenação obrigatória `createdAt ASC, id ASC`;
   (2) REST com paths em português, DTOs espelhando o split
   request/response da spec, campos desconhecidos rejeitados, 201 com
   Location, datetimes UTC com `Z`; (3) mapeamento de erros com a
   taxonomia e precedência COMPLETAS da spec (6 regras → 422 com type
   próprio, 404, três categorias de 400 com fail-fast
   path→query→body). Condição de processo: plano com layout de arquivos
   e tabela exceção→status→type URI antes de código. Escopo de teste
   decidido em pergunta estruturada: integração + taxonomia agora,
   suíte de contrato OpenAPI como passo seguinte.

3. **O que a IA produziu** —
   - **Flyway** (justificativa: schema é artefato revisado e explícito —
     invariantes como colunas/checks — não efeito colateral de anotação).
     `V1__initial_schema.sql` com checks de enum, non-blank e
     `(status='done') = (completed_at is not null)`.
   - Persistência: entidades com enums como String minúscula (valores de
     fio), mappers via `reconstitute`, repos Panache com
     `Sort.by("createdAt").and("id")` e `existsByProjectIdAndStatus`
     (fato da regra 1). Hibernate em modo `validate`.
   - REST: 3 resources (transações no adapter, `Location` relativo
     setado manualmente — `Response.created()` resolveria pra URL
     absoluta); DTOs com `@JsonAnySetter` coletando campos
     desconhecidos (Jackson fail-on-unknown aborta no primeiro campo; a
     spec exige reportar todos de uma vez) e setters registrando
     presença → `PatchField` tri-estado reutilizado da aplicação.
   - Precedência: `ContainerRequestFilter` validando UUID do path ANTES
     da desserialização do corpo (JAX-RS leria o corpo primeiro);
     regex canônica em vez de `UUID.fromString`, que aceita formas como
     `1-1-1-1-1`; query fail-fast status→priority em código; dois
     formatos de erro de path (plain vs `errors[]`) conforme os dois
     exemplos da spec. `ExceptionMapper` com switch exaustivo sobre a
     sealed `DomainRuleViolationException`.
   - Nota registrada no plano: o prompt pedia "404 por recurso com type
     próprio", mas a spec congelada define UM type compartilhado
     (`resource-not-found`) com `detail` por recurso — spec venceu.
   - Testes: 12 de persistência (Testcontainers/Dev Services, incluindo
     smoke de constraint no banco) + 35 REST (happy paths +
     `ErrorTaxonomyTest`: um teste por linha da tabela + precedências).
     Suíte 117/117.
   - Correção no caminho (própria IA): classes `*IT` não rodavam
     (surefire só pega `*Test`, `skipITs=true`) — renomeadas; e 2 testes
     de persistência quebraram ao compartilhar o banco Dev Services com
     os testes REST (asserções de lista exata vs dados commitados) —
     limpeza de tabelas em `@BeforeEach`.

4. **Resultado** — Aceito como gerado (2 correções de infraestrutura de
   teste feitas pela própria IA antes do commit, descritas acima; sem
   intervenção do usuário no código). Auditoria do `domain-guardian`
   sobre os adapters em andamento ao registrar esta entrada.

## 16. Correção do drift de coerção escalar (achado do /sdd-check)

1. **Contexto** — Sequência da primeira rodada do `/sdd-check` sobre
   `quarkus-impl` (report-only), que encontrou exatamente um drift:
   coerção escalar default do Jackson aceitava `{"name": 123}` como
   `"123"` onde a spec exige `type: string`. Racional completo e
   contexto do achado em `ai/revisoes.md`, entrada 12. Commit `5d20b93`.

2. **Prompt (resumo)** — Corrigir o drift via customizer global do
   ObjectMapper do Quarkus (não por DTO): número onde o schema espera
   string (e vice-versa) deve falhar a desserialização e emergir como o
   `400 invalid-request-body` da spec (`ValidationProblemDetails`)
   nomeando o campo ofensor. Testes de taxonomia exigidos: `POST
   /projetos` com `{"name": 123}` → 400 com `errors[]` contendo `name`;
   `PATCH /tarefas/{id}` com `{"priority": 1}` → mesmo tratamento.
   Verificar que a falha cai no tratamento 400 existente — não um 500
   cru do Jackson nem um 400 genérico sem ProblemDetails.

3. **O que a IA produziu** —
   - `adapter/in/rest/JacksonCoercionConfig.java`: `ObjectMapperCustomizer`
     (`@Singleton`) com `coercionConfigFor(LogicalType.Textual)` →
     `CoercionAction.Fail` para os shapes `Integer`, `Float` e
     `Boolean`. Escopo deliberadamente estreito: alvo textual apenas —
     os request DTOs não têm campo numérico (a direção "string onde se
     espera número" não tem o que configurar) e a serialização de
     resposta fica intacta.
   - Nenhum mapper novo: a coerção que falha lança
     `MismatchedInputException`, já capturada pelos mappers criados na
     correção da entrada 11 de `ai/revisoes.md` — o campo ofensor sai do
     path do próprio Jackson.
   - 2 testes novos no `ErrorTaxonomyTest`
     (`numberWhereStringExpectedIsRejectedNamingTheField`,
     `numberWhereEnumStringExpectedIsRejectedNamingTheField`), ambos
     asserindo status 400, content-type `application/problem+json`,
     type URI `invalid-request-body` e o campo nomeado.
   - Suíte 121/121 na primeira execução; segunda rodada do `/sdd-check`
     na sequência: zero drift.

4. **Resultado** — Aceito como gerado, sem edições. Origem do achado e
   análise do porquê nem testes nem auditoria pegaram: `ai/revisoes.md`,
   entrada 12.

## 17. Camada de validação de contrato — matriz de cobertura + validação de schema nas respostas

1. **Contexto** — Passo dedicado de validação de contrato do
   `quarkus-impl` contra a spec congelada (seis regras de negócio +
   garantias de precedência). Duas frentes: matriz de cobertura
   (cenário da spec × teste existente) produzida pelo agente
   `contract-tester`, e validação automática de schema OpenAPI em toda
   resposta da suíte REST via `swagger-request-validator-restassured`
   (Atlassian). Commits `83ffc4b` e `f4d8eb4`.

2. **Prompt (resumo)** — (1) Usar o agente `contract-tester` primeiro
   para produzir a matriz de cobertura refletindo a spec congelada (seis
   regras + precedências), não o texto original do desafio de cinco
   regras. (2) Implementar o que a matriz apontasse como faltante e
   adicionar validação de schema das respostas contra
   `spec/openapi.yaml` — investigando antes o estado atual do
   `swagger-request-validator-restassured` quanto à compatibilidade com
   as versões de RestAssured/Quarkus do projeto e reportando os achados
   antes de integrar. Toda resposta de teste REST deve validar contra o
   documento da spec, não só contra asserções manuais.

3. **O que a IA produziu** —
   - Investigação prévia (reportada antes da integração): última versão
     2.44.9 (jun/2025, sem linha 3.x), compilada contra RestAssured
     5.1.1; o BOM do Quarkus 3.37.1 fixa RestAssured **6.0.0** — API de
     `Filter` inalterada, compatibilidade confirmada executando a suíte.
     Spec em OpenAPI 3.0.3, suportada integralmente.
   - `SpecValidatedRestTest` (classe-base de teste): filtro global
     RestAssured validando toda resposta contra a spec. Response-only
     (`validation.request` → IGNORE, pois os testes de taxonomia 400
     enviam requests malformados de propósito) e
     `withResolveCombinators(true)` — sem isso o validador injeta
     `additionalProperties: false` por ramo do `allOf` e rejeita toda
     resposta `ValidationProblemDetails` (quirk documentado no FAQ do
     projeto; derrubou 28 testes na primeira rodada). As quatro classes
     REST estendem a base.
   - Prova negativa: renomeando `status` → `situacao` no
     `ProjectResponse`, o validador falhou o teste com "missing required
     properties [status]" — o filtro valida de fato. Revertido.
   - ~30 testes novos preenchendo a matriz: metade "regressão" da regra
     5 (três movimentos para trás), regra 2 com tarefa `done`, caminho
     de desbloqueio da regra 1, 404s de projeto-pai desconhecido e de
     PATCH/DELETE, 400s de path em GET/DELETE de tarefa, 400s de corpo
     (obrigatórios, tamanhos, enums, readOnly) nos quatro endpoints de
     escrita, ordenação contratual das duas listas, query params
     desconhecidos ignorados, no-op de status em projeto (exemplo da
     própria spec), edição não-status da regra 6, atomicidade do PATCH
     (projeto e tarefa). Todas as seis regras 422 agora asserem `type` +
     membro `status` + token do `detail`.
   - Política de asserção documentada no javadoc do `ErrorTaxonomyTest`
     (normativo = igualdade exata; ilustrativo = token do prose;
     exceção = detalhes de filtro citados verbatim na spec) — mesma
     política valerá para a suíte Rails no dia 3.

4. **Resultado** — Aceito com um refinamento do usuário: a política de
   asserção (abrandar strings exatas ilustrativas para tokens) foi
   decisão do usuário sobre achado de sobre-especificação da matriz, com
   a adição de documentá-la na própria suíte. Suíte completa: 156/156
   no `./mvnw verify`.

## 18. Testes de mutação — PIT no domínio+aplicação do Quarkus

1. **Contexto** — PIT no domínio+aplicação Quarkus — score 100%,
   sobreviventes triados um a um. Fase de qualidade de testes do
   `quarkus-impl`, fechando o apontamento do guardian da fase de
   domínio.

2. **Prompt (resumo)** — Dois prompts encadeados: (1) adicionar
   pitest-maven ao build, escopado SÓ a `domain` + `application`
   (adapters/DTOs/mappers fora — mutar plumbing de tradução é ruído);
   verificar versões atuais e compatibilidade com nosso nível de
   JUnit/Java antes de fiar; vincular a goal dedicado
   (`pitest:mutationCoverage`), nunca à fase de teste padrão; rodar e
   reportar score + lista completa de sobreviventes com
   arquivo/linha/mutador. (2) Para cada sobrevivente, propor teste que
   mata OU argumento de mutante equivalente — um a um, decisão humana
   em cada.

3. **O que a IA produziu** —
   - Verificação de versões antes de fiar: pitest-maven 1.25.6
     (lançado no mesmo dia; ASM 9.10.1 → class files Java 25;
     auto-desliga extensão jacoco do Quarkus) + pitest-junit5-plugin
     1.2.3. Achado relevante: Quarkus 3.37 traz JUnit Jupiter 6.1.1 e
     o plugin documenta só platform ≤1.10 — compatibilidade com JUnit 6
     confirmada empiricamente na primeira rodada (53 testes
     descobertos, 144 execuções sob mutação).
   - `quarkus-impl/pom.xml`: plugin declarado sem binding de lifecycle
     (só invocação explícita), `targetClasses`/`targetTests` em
     `com.taskflow.domain.*` + `com.taskflow.application.*` —
     @QuarkusTest/Testcontainers e ArchUnit fora do loop de mutação.
     Rodada completa: 8 segundos.
   - Primeira rodada: 95% (96/101), domínio 95,1%, application 95,0%;
     5 não-mortos extraídos do XML com arquivo/linha/mutador.
   - Triagem um a um via pergunta estruturada (humano decidiu os 5):
     todos mortos com mudanças cirúrgicas em `TaskTest`, `ProjectTest`
     e `UpdateTaskUseCaseTest` (1 teste novo, 4 asserções adicionadas).
     Nenhum aceito como equivalente — para `taskId()` a IA explicitou
     que o argumento de equivalência seria na verdade código morto.
   - Segunda rodada: **100% (101/101), zero sobreviventes, zero sem
     cobertura**.

4. **Resultado** — Aceito. Os 5 sobreviventes eram lacunas dos testes
   gerados pela própria IA em fases anteriores (asserção unilateral
   mascarada por 99% de cobertura de linha) — análise honesta em
   `ai/revisoes.md`, entrada 15.

## 19. Atualização de escopo — projeto single-stack (Quarkus)

1. **Contexto** — Revisão de escopo pós-mutação: a implementação Rails
   (meta autoimposta; o desafio pede uma API) foi cortada em favor de
   profundidade na stack única — mutação 100%, suíte de contrato
   validada por schema, emendas de spec. Todos os artefatos do repo
   precisavam refletir o corte de forma consistente.

2. **Prompt (resumo)** — Decisão de escopo anunciada com plano de 5
   itens: (1) `CLAUDE.md` sem stack Rails, sem mentoria Ruby, sem
   comandos Rails, arquitetura reescrita para stack única; (2)
   `spec/openapi.yaml` sem o servidor `:3000` e com `info.description`
   varrida por prosa que assuma duas implementações — mudança SÓ de
   prosa, contrato intocado, cada linha tocada flagrada; (3)
   `docs/decisoes.md` com §2 reescrito como arquitetura da entrega e
   entrada nova de decisão de escopo (trade-off tempo vs. profundidade
   + nota de que o trabalho cross-stack da spec permanece válido — é o
   que torna o contrato independente de implementação, a própria tese
   de SDD); (4) `ai/skills.md` sem as linhas de delegação Ruby, com
   observação de processo; (5) deletar `rails-impl/` vazio. Gate de
   revisão: mostrar o rascunho da entrada de decisoes.md antes de tocar
   o resto.

3. **O que a IA produziu** —
   - Rascunho do §10 de `docs/decisoes.md` apresentado antes de
     qualquer edição, junto com um achado fora da lista: Rails
     entranhado em decisoes.md além do §2 (§1, §3, §4, §6, §7, §8, §9)
     — pergunta estruturada com 3 tratamentos; escolhido rewrite leve.
   - Os 5 itens executados: `CLAUDE.md` single-stack; 6 pontos de prosa
     na spec flagrados um a um (menções a Rails sobrevivem só como
     exemplos ilustrativos de divergência entre ecossistemas, não como
     framework-alvo; YAML revalidado, 1 servidor); `decisoes.md` com §2
     reescrito (tabela de regras em coluna única, alternativa rejeitada
     trocada para modelo anêmico, nota histórica curta sobre a
     assimetria planejada) + §10 + rewrite leve das demais seções;
     `ai/skills.md` atualizado; `rails-impl/` deletado.
   - Varredura residual além da lista: 3 arquivos de `.claude/` ainda
     dirigiam a IA para o stack morto (`domain-guardian` com seção Ruby
     inteira, `contract-tester` citando `committee`, `sdd-check`
     aceitando `rails-impl`) — corrigidos; contagem defasada "5
     business rules" (anterior à regra 6) corrigida para 6. Logs
     históricos (`ai/prompt-log.md`, `prompts.md`, `revisoes.md`)
     deliberadamente intocados — são registro.

4. **Resultado** — Aceito com direção humana nos dois gates (rascunho
   do §10 aprovado antes do resto; tratamento das seções residuais
   escolhido entre opções). Análise de processo em `ai/revisoes.md`,
   entrada 16 — inclusive o ponto de que a IA nunca questionou a
   viabilidade do dual-stack em nenhuma sessão anterior.

## 20. Fechamento prosa→schema — patterns de UUID canônico e não-branco em name/title

1. **Contexto** — Primeira execução real do pipeline de CI. Job
   `contract` (Schemathesis contra `spec/openapi.yaml`, já formalmente
   congelada) reportou "schema constraints don't match API validation"
   em 6 operações. Triagem separada identificou duas causas-raiz
   distintas (3 operações cada): `id` de path sem `pattern` de UUID
   canônico, e `name`/`title` sem restrição de whitespace além de
   `minLength`. Investigação anterior já havia mostrado que, nos dois
   casos, a implementação estava certa e a spec já prometia a regra
   certa em prosa/exemplo — só não em JSON Schema formal. Esta sessão
   aplicou a correção.

2. **Prompt (resumo)** — Aprovado tightening da spec com condições
   específicas: (1) grupo A — `pattern` de UUID canônico em
   `ProjectIdParam`/`TaskIdParam`, com instrução explícita de verificar
   se `UuidPathParamFilter` concorda quanto a maiúsculas/minúsculas e
   reconciliar caso divirja; (2) grupo B — `pattern` de "contém
   não-whitespace" em `name`/`title`, ADICIONADO (não substituindo)
   `minLength`/`maxLength` existentes; (3) por ser emenda à spec
   congelada: rodar `spec-reviewer` como confirmação dedicada nas
   adições, revalidar suíte de contrato completa, atualizar
   `decisoes.md` registrando o fechamento prosa→schema — e mostrar o
   resultado do `spec-reviewer` antes de qualquer commit.

3. **O que a IA produziu** —
   - Verificação do grupo A antes de aplicar: a instrução do usuário
     pedia pattern "lowercase canonical", mas a prosa de
     `info.description` e `docs/decisoes.md` (item 3) já fixavam
     case-insensitive como decisão deliberada (rejeitando lowercase-only
     como autocontraditória), com teste (`uppercaseHexUuidPassesPathValidation`)
     pinando o comportamento. Contradição sinalizada explicitamente ao
     usuário via pergunta estruturada em vez de aplicar o literal ou
     substituir silenciosamente — usuário confirmou case-insensitive.
   - `pattern: '^[0-9a-fA-F]{8}-...-[0-9a-fA-F]{12}$'` em
     `ProjectIdParam`/`TaskIdParam`; `pattern: '.*\S.*'` somado a
     `minLength`/`maxLength` em `CreateProjectRequest.name`,
     `UpdateProjectRequest.name`, `CreateTaskRequest.title`,
     `UpdateTaskRequest.title` — 6 pontos, todos aditivos.
   - `spec-reviewer` disparado como confirmação dedicada (não revisão
     geral) nas 2 mudanças: regex do UUID e composição do pattern de
     name/title contra `minLength`/`maxLength` existentes e exemplos já
     presentes na spec.
   - Suíte de teste completa revalidada (162/162 — validação
     client-side já era desligada deliberadamente nos testes Java, então
     o aperto do schema não quebrou nada existente).
   - `docs/decisoes.md` item 12 escrito com o racional completo,
     incluindo a nota do cuidado com a leitura lowercase-only rejeitada.

4. **Resultado** — Aceito. `spec-reviewer` não achou problema em nenhuma
   das duas mudanças (regex corretos, sem contradição nova com
   prosa/exemplos). Análise de processo completa em `ai/revisoes.md`,
   entrada 17 — inclusive o ponto de que fuzzing baseado em schema acha
   uma categoria de lacuna (fidelidade schema formal) que revisão
   humana de prosa não acha.

## 21. Correção de pipeline round 2 — pattern control-char no schema, `--force-maven-cli` no Snyk

1. **Contexto** — Segunda execução real do pipeline de CI após o
   fechamento do item 20. Três jobs falharam: `contract` (Schemathesis
   achou uma categoria nova de falha), `security` (Snyk nunca chegou a
   escanear), `sonar` (quality gate falhou sem detalhe visível no log).
   Sessão anterior triou e classificou os três (config/wiring vs achado
   genuíno) e propôs correções, aguardando aprovação para os dois
   primeiros — `sonar` ficou pendente por falta de acesso a
   `SONAR_TOKEN` para puxar a condição exata do dashboard.

2. **Prompt (resumo)** — Aprovadas as duas correções propostas com
   condições de verificação explícitas: (1) contract — trocar o pattern
   de `name`/`title` pela versão que também exclui caracteres de
   controle, mas PRIMEIRO verificar que a classe de caracteres do regex
   rejeita exatamente os mesmos codepoints que
   `Character.isISOControl` (0x00–0x1F, 0x7F–0x9F); depois rodar
   `spec-reviewer` como confirmação dedicada (emenda à spec congelada) e
   revalidar a suíte completa confirmando que os 4 casos que falhavam
   agora passam; e anotar em `decisoes.md` que isso fecha a regra de
   caractere de controle no schema; (2) security — adicionar
   `--force-maven-cli` aos args do Snyk. Mostrar o resultado do
   `spec-reviewer` e parar.

3. **O que a IA produziu** —
   - Verificação do regex ANTES de aplicar: varredura Python de
     0x00–0x9F comparando contra `Character.isISOControl` achou um bug
     no próprio regex proposto (`^(?=.*\S)[^\x00-\x1F\x7F-\x9F]*$`) — o
     `$` do Python (e do Java) casa também logo antes de um `\n` final
     isolado, deixando `"A\n"` passar quando não devia. Corrigido para
     `^(?=.*\S)[^\x00-\x1F\x7F-\x9F]*(?![\s\S])`, revalidado em Python E
     em Node/V8 (motor ECMA-262) com varredura completa 0x00–0x24F,
     zero divergências.
   - Pattern aplicado nos 4 pontos (`CreateProjectRequest.name`,
     `UpdateProjectRequest.name`, `CreateTaskRequest.title`,
     `UpdateTaskRequest.title`).
   - `spec-reviewer` disparado como confirmação dedicada.
   - Verificação local: suíte Java completa (162/162, não exercita o
     pattern diretamente — validação client-side desligada) MAIS um
     boot manual da app com Schemathesis rodado com a mesma seed do
     achado original, confirmando os 4 casos resolvidos — e expondo um
     quinto, adjacente: `description` tem a mesma regra no código
     (`BodyValidation.invalidDescription` também chama
     `hasControlChar`) e nenhum pattern no schema. Fora do escopo desta
     rodada (aprovação era só `name`/`title`); reportado, não corrigido,
     anotado em `decisoes.md` item 12 como pendência.
   - `--force-maven-cli` adicionado aos args do Snyk em `ci.yml`.
   - `docs/decisoes.md` item 12 recebeu um addendum com o racional
     completo (o bug do `$`, a troca para o lookahead, e a pendência do
     `description`).

4. **Resultado** — Aceito com uma ressalva do `spec-reviewer`: achou uma
   quarta camada de divergência que nem a verificação da IA nem o
   `spec-reviewer` anterior (item 20) tinham pego — `(?=.*\S)` usa `.`,
   que no Python exclui só `\n` mas no ECMA-262 (motor nominal da spec)
   também exclui U+2028/U+2029, que não são caracteres de controle e
   deveriam ser aceitos. A varredura de codepoints da IA (0x00–0x24F)
   nunca tocou essa faixa. Correção sugerida pelo `spec-reviewer`
   (`(?=.*\S)` → `(?=[\s\S]*\S)`) reportada ao usuário, não aplicada —
   aguardando decisão. Análise de processo completa em
   `ai/revisoes.md`, entrada 18: três rodadas (14, 17, 18) desta mesma
   série de achados, cada uma fechando parte do problema e abrindo a
   próxima camada — nenhuma camada de verificação sozinha (revisão
   humana, teste, fuzzing, autoverificação da IA, `spec-reviewer`) pegou
   tudo; só a composição delas foi reduzindo a superfície.

## 22. Fechamento do `contract` — `positive_data_acceptance` configurado para aceitar 422 documentado

1. **Contexto** — Última falha aberta do job `contract`: um teste stateful
   do Schemathesis criava uma tarefa contra um projeto que a própria
   sequência aleatória tinha arquivado, recebia `422
   task-create-project-archived` (regra de negócio 4, comportamento
   correto, já coberto por `ErrorTaxonomyTest.rule4CreateTaskInArchivedProject`)
   e reportava isso como falha — o check `positive_data_acceptance` do
   Schemathesis não sabe que esse `422` é um resultado documentado e
   correto, só compara contra uma lista fixa de status "aceitáveis" que
   não inclui `422`. Não era lacuna de spec nem de código.

2. **Prompt (resumo)** — Pedido para avaliar 3 opções e recomendar uma:
   (a) configurar o run para tratar `422`s documentados como esperados,
   (b) excluir o check stateful especificamente para essa sequência, (c)
   um hook/whitelist para esse `422` específico — sem nunca afrouxar a
   spec para fingir que o endpoint retorna `2xx`. Depois de eu recomendar
   (a) global (não escopado a uma operação, já que as 6 regras de negócio
   deste projeto retornam `422` do mesmo jeito), aprovado com 3 condições:
   escrever a lista completa de status esperados (verificar se
   `expected-statuses` substitui ou estende o default — se não desse pra
   confirmar, escrever a lista cheia pra não derrubar nenhum default
   silenciosamente); reexecutar localmente contra a seed que falhou no CI
   E mais seeds, e fazer um sanity-check fazendo um endpoint devolver 418
   de propósito pra confirmar que o check ainda pega status genuinamente
   errado, revertendo depois; e uma linha em `decisoes.md` sobre a
   limitação de fuzzing schema-blind contra invariante com estado.

3. **O que a IA produziu** —
   - Verificação da semântica antes de escrever qualquer config: leu o
     código-fonte do pacote `schemathesis` instalado localmente
     (`config/_checks.py`) em vez de confiar só na documentação — confirmou
     que `expected_statuses` é substituição pura (`if expected_statuses is
     not None: usa a lista dada; senão usa o default`), nunca soma, e leu
     o valor exato do default
     (`["2xx", "401", "403", "404", "409", "5xx"]`) direto da constante.
   - `schemathesis.toml` criado na raiz do repo (auto-descoberto por
     busca em diretório atual + pais, confirmado lendo o código de
     `SchemathesisConfig.discover()` — nenhuma mudança em `ci.yml`
     necessária) com
     `positive_data_acceptance.expected-statuses = ["2xx", "401", "403", "404", "409", "422", "5xx"]`
     — a lista default completa mais `422`.
   - Verificação local: app local subida, Schemathesis rodado contra a
     seed exata do CI (`277391521400385141520460251304646725452` — todas
     as 4 fases ✅, 1964/1964) mais a seed de uma falha anterior mais uma
     seed nova aleatória — as 3 passaram limpas. Sanity-check: `POST
     /projetos` alterado temporariamente para devolver `418` em vez de
     `201`, Schemathesis rodado de novo e pegou a falha corretamente
     (`API rejected schema-compliant request`), revertido na sequência —
     `git diff` vazio confirmou reversão limpa. Suíte Java completa
     rodada de novo depois da reversão: 162/162.
   - `docs/decisoes.md` item 13 escrito com a linha pedida sobre o limite
     do fuzzing schema-based, deixando explícito que quem garante a
     corretude de cada `422` é `ErrorTaxonomyTest`, não o Schemathesis.

4. **Resultado** — Aceito como proposto, sem edição. Nenhum achado do
   tipo "a IA errou" nesta rodada — a única checagem que valia a pena
   destacar (substituição vs soma da config) foi resolvida lendo o
   código-fonte da ferramenta em vez de assumir a partir da doc, antes
   de escrever qualquer coisa. Fecha a série de achados do Schemathesis
   contra `spec/openapi.yaml` (itens 14, 17, 18 de `ai/revisoes.md`) — o
   job `contract` não tem mais falha conhecida em aberto.

## 23. Fix do Snyk — flag correta `--maven-skip-wrapper` (a anterior era fabricada pela própria IA)

1. **Contexto** — Última falha aberta do job `security`: o Snyk nunca
   chegava a escanear, travando em "Failed to validate Maven distribution
   SHA-256" — erro do wrapper `./mvnw` do projeto sendo bootstrapado
   dentro do container do Snyk. Duas rodadas atrás a IA tinha proposto
   `--force-maven-cli` como flag para forçar o Snyk a usar o `mvn` global
   do container em vez do wrapper; aplicado, mas o erro persistiu
   idêntico.

2. **Prompt (resumo)** — Pedido para investigar, sem propor fix ainda,
   qual das três hipóteses explicava o flag não ter efeito: (1)
   word-splitting — o `args` da action está passando a string inteira
   como um token só, então `--force-maven-cli` nunca chega a ser
   reconhecido como flag separada; (2) a versão do Snyk CLI empacotada
   na imagem do container não suporta esse flag; (3) abandonar essa
   abordagem e usar o Maven do próprio runner em vez do container Docker
   do Snyk. Reportar com evidência (docs, versão) e recomendar uma só,
   sem tentar variações de argumento no escuro.

3. **O que a IA produziu** —
   - Leu o `action.yml` real de `snyk/actions/maven` (via `gh api`) —
     confirmou que `args` é passado como item único de lista YAML, mas
     leu também o `docker-entrypoint.sh` real da imagem (repo
     `snyk/snyk-images`) e achou que ele faz `eval "exec $*"`
     deliberadamente para suportar exatamente esse caso (comentário
     explícito no script). Hipótese (1) descartada com evidência direta
     do código-fonte, não por suposição.
   - Instalou o Snyk CLI mais recente localmente (`npm install -g snyk`,
     v1.1305.2) e rodou `snyk test --help` — descobriu que
     `--force-maven-cli` **nunca existiu**: buscou o repo `snyk/cli`
     inteiro (código e histórico de commits) e não achou nenhuma
     ocorrência, em nenhuma versão. A flag real, com a descrição exata
     do que a IA queria ("Forces the use of a globally installed mvn
     command..."), é `--maven-skip-wrapper` — adicionada em março de
     2026 (PR #6653), a imagem Docker `snyk/snyk:maven` foi reconstruída
     em 5 de julho de 2026 (checado via API do Docker Hub), então a CLI
     empacotada é recente o bastante para ter o flag.
   - Reportou: hipótese (1) descartada, hipótese (2) confirmada mas por
     um motivo diferente do hedge original (não era uma CLI *desatualizada*
     sem o flag — era um flag que **nunca existiu**, inventado pela
     própria IA numa pesquisa anterior). Hipótese (3) descartada como
     desnecessária, já que existe flag real e correto.
   - Aplicado: `--force-maven-cli` → `--maven-skip-wrapper` em `ci.yml`.

4. **Resultado** — Aceito. Achado central desta rodada é um erro da
   própria IA de duas rodadas atrás: `--force-maven-cli` foi citado a
   partir de uma síntese de busca na internet que descreveu a função
   certa mas deu o nome errado da flag, e a IA não verificou contra a
   fonte primária (`--help` da CLI real, ou o repositório `snyk/cli`)
   antes de aplicar — só corrigiu isso quando o usuário pediu
   investigação formal em vez de aceitar a alegação de pesquisa direto.
   Ainda não confirmado se o fix funciona de fato (depende do próximo
   log de CI) — condição do usuário para fechar: citar a linha exata do
   log mostrando que o Snyk resolveu e testou a árvore de dependências de
   verdade, não "0 dependências" ou skip silencioso.

---

## 24. Fix de segurança — pin postgresql 42.7.12 resolvendo CVE alta pega pelo Snyk

1. **Contexto** — Sequência do item 23: a flag `--maven-skip-wrapper`
   corrigida destravou o step do Snyk pela primeira vez. O usuário pediu
   leitura do log resultante (`/tmp/snyk-fail.log`) e classificação: o
   mesmo erro de flag de novo, ou um erro *diferente* (CVE real ou outra
   config)? Exigiu citar a linha raiz e classificar antes de propor
   qualquer correção.

2. **Prompt (resumo)** — Ler o log, classificar entre duas hipóteses
   (1: flag ainda não pega, mesmo erro Maven SHA-256; 2: flag funcionou,
   erro novo — se CVE, nomear dependência/vuln/versão corrigida) e citar
   a linha raiz antes de propor fix. Depois de eu confirmar hipótese 2 e
   propor o pin, usuário aprovou com 3 condições explícitas: (1) pin via
   `dependencyManagement` com override de versão que vença o BOM do
   Quarkus, verificado via `dependency:tree` mostrando 42.7.12 e não
   42.7.11; (2) rodar `./mvnw verify` completo confirmando que o bump
   não quebra os testes de integração com Testcontainers; (3) confirmar
   Snyk local se possível, ou registrar que a próxima rodada de CI
   verifica.

3. **O que a IA produziu** —
   - Classificou hipótese 2: log mostra "Tested 177 dependencies... found
     1 issue" — o Snyk rodou de verdade. Achado:
     `SNYK-JAVA-ORGPOSTGRESQL-17874248` ("Incorrect Implementation of
     Authentication Algorithm"), High, em `org.postgresql:postgresql@42.7.11`,
     introduzido via `quarkus-jdbc-postgresql@3.37.1`. Fix disponível:
     `42.7.12`.
   - Editou `quarkus-impl/pom.xml`: nova propriedade
     `postgresql.version=42.7.12` e entrada `org.postgresql:postgresql`
     em `dependencyManagement`, declarada depois do import do
     `quarkus-bom` (ordem que garante precedência sobre a versão do BOM).
   - Verificou com `mvn dependency:tree -Dincludes=org.postgresql:postgresql`
     (JDK 25 via `JAVA_HOME`) — confirmou resolução em `42.7.12`, não
     `42.7.11`.
   - Rodou `./mvnw verify` completo: 162 testes, 0 falhas, BUILD SUCCESS,
     incluindo os testes de persistência via Testcontainers/Postgres real.
   - Tentou `snyk test` local para confirmar a condição 3 — falhou com
     401 (SNYK-0005, sessão não autenticada). Reportado como não
     verificável localmente, deixando a confirmação final para a
     próxima rodada de CI em vez de assumir sucesso.

4. **Resultado** — Aceito com as 3 condições cumpridas (2 verificadas
   diretamente, 1 declarada como pendente de CI por falta de auth local
   — não mascarada como sucesso). Ver `ai/revisoes.md`, item 19: análise
   de por que essa CVE só apareceu depois que o gate do Snyk foi
   destravado (itens 21/23), e por que um BOM confiável não é garantia
   de dependência transitiva livre de CVE.

---

## 25. Fix release-please — habilitada permissão de criação de PR por Actions no repo (`can_approve_pull_request_reviews`), mantendo `default_workflow_permissions` em `read` (grant mínimo)

1. **Contexto** — Último step vermelho do pipeline depois dos fixes de
   Snyk (itens 23/24): `release-please` falhava no
   `googleapis/release-please-action@v4`, todo o resto (test, spec-lint,
   sonar, security, contract) já verde.

2. **Prompt (resumo)** — Ler `/tmp/release-fail.log` e classificar entre
   três hipóteses: (a) permissão (falta `contents:write`/
   `pull-requests:write` no workflow, ou repo Settings > Actions >
   Workflow permissions em read-only), (b) config (manifest/config do
   release-please faltando, `release-type` errado pra projeto Maven/Java),
   ou (c) token (GITHUB_TOKEN padrão vs PAT). Citar a linha de erro exata
   antes de propor fix. Depois de eu classificar e propor, usuário
   aprovou aplicar ("Yes, run it") — pedido explícito antes de qualquer
   mudança de config de repositório.

3. **O que a IA produziu** —
   - Leu o log: ação criou branch, tree, commit e atualizou a ref com
     sucesso (prova que `contents:write` funcionava) e falhou só no
     passo final, criação de PR, com
     `##[error]release-please failed: GitHub Actions is not permitted to
     create or approve pull requests.`
   - Descartou (b) e (c) com evidência do próprio log (manifest ausente
     é comportamento normal de primeira execução, não erro; token
     autenticou e escreveu commits/refs sem problema).
   - Checou `.github/workflows/ci.yml:206-208` — job já declarava
     `permissions: {contents: write, pull-requests: write}` a nível de
     job, descartando a sub-hipótese de permissões faltando no YAML.
   - Rodou `gh api repos/.../actions/permissions/workflow` (leitura) e
     confirmou `can_approve_pull_request_reviews: false` — a config
     exata que gera essa mensagem de erro do GitHub.
   - Propôs o `gh api -X PUT` com o fix, mas a primeira tentativa de
     *executar* foi bloqueada pelo classificador de auto mode do Claude
     Code: mudança de permissão de Actions a nível de repositório exigia
     pedido explícito do usuário, não só a investigação. Reportou o
     bloqueio e a proposta em vez de contornar.
   - Após aprovação explícita ("Yes, run it"), executou o `PUT` mantendo
     `default_workflow_permissions=read` (não elevou para `write` —
     escopo mínimo, já que o job já tinha seu próprio `permissions:`
     bloco cobrindo o que precisava) e mudando só
     `can_approve_pull_request_reviews` para `true`. Verificou com `GET`
     depois: `can_approve_pull_request_reviews: true` confirmado.

4. **Resultado** — Aceito como proposto, sem edição. Fix ainda não
   confirmado end-to-end (depende do próximo push em `main` de fato
   abrir a PR do release-please) — registrado como pendente, não
   assumido como resolvido.
