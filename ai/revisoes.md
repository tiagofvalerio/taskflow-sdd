# Revisões de saídas da IA

Registro honesto de correções, rejeições e ajustes aplicados sobre o que a IA
gerou neste projeto. Objetivo: documentar onde a IA errou, foi ingênua ou
incompleta, e por quê — não é um changelog de features.

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
