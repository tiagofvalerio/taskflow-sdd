# Decisões de Design — TaskFlow

Este documento consolida as decisões de design já tomadas e defendidas ao
longo do projeto. Não é um changelog: é o racional por trás de cada decisão,
incluindo alternativas rejeitadas e seus trade-offs. Fontes: `spec/openapi.yaml`
(congelado), `CLAUDE.md`, e o histórico de revisão em `ai/revisoes.md` e
`ai/prompts.md`.

---

## 1. Por que spec-first, e como prevenimos drift

`spec/openapi.yaml` é a fonte única da verdade: nenhuma das duas stacks decide
comportamento por conta própria, e nenhuma delas é "mais autoritativa" que a
outra. O risco real de duas implementações independentes (Quarkus e Rails)
partindo do mesmo contrato é drift silencioso — cada stack, sob pressão de
prazo, tende a resolver ambiguidade do jeito que seu framework favorece por
padrão, e essas escolhas divergem sem ninguém perceber até um cliente real
bater na diferença.

Prevenção de drift, em três camadas:

1. **Testes de contrato em cada stack, contra o mesmo schema.** Quarkus usa
   RestAssured combinado com um validador de schema OpenAPI; Rails usa a gem
   `committee`. Ambos validam requisição e resposta reais contra
   `spec/openapi.yaml` — não contra uma cópia reescrita ou um schema
   gerado a partir do próprio código.
2. **Schemathesis em CI, contra as duas APIs rodando.** Testes baseados em
   propriedade gerados a partir do próprio `openapi.yaml`, disparados contra
   as duas implementações (Quarkus em `:8080`, Rails em `:3000`). Isso pega
   divergência comportamental que testes unitários não pegam, porque explora
   o espaço de entradas a partir do contrato, não a partir do que cada
   desenvolvedor lembrou de testar manualmente.
3. **Diff entre a spec gerada pelo `smallrye-openapi` (extensão padrão do
   Quarkus, que introspecciona as anotações JAX-RS/Jakarta do código e produz
   um OpenAPI derivado) e a spec manual congelada.** A hierarquia nunca
   inverte: divergência encontrada nesse diff é sempre bug de implementação a
   corrigir no código Quarkus, nunca motivo para atualizar `spec/openapi.yaml`
   a partir do gerado. Esse diff existe só para detectar quando a
   implementação Quarkus silenciosamente expôs um campo, endpoint ou status
   code que a spec não previu.

**Alternativa rejeitada — code-first (spec gerada a partir das anotações de
uma das stacks, ou de ambas independentemente).** Rejeitada porque contradiz a
metodologia SDD que o próprio `CLAUDE.md` declara: um contrato derivado de
anotações por stack não passa pela revisão adversarial centralizada que a
spec manual recebeu (ver item 9) — cada stack chegaria a uma variação
ligeiramente diferente da mesma regra de negócio, sem um único lugar de
verdade pra arbitrar qual delas está certa.

---

## 2. Arquitetura assimétrica deliberada

Quarkus usa arquitetura hexagonal (domain layer com zero imports de
framework — nenhum `jakarta.*`/JPA/Quarkus na camada de domínio; entidades
JPA existem só nos adapters, com mappers de tradução). Rails usa o Rails-way
idiomático: modelos ActiveRecord ricos, regra de negócio dentro do model,
controllers finos, convenções padrão (validations, scopes, strong params).

Isso é assimetria deliberada, não inconsistência: cada stack é fiel ao que é
idiomático no próprio ecossistema.

- **Quarkus** tem, nativamente, os elementos que tornam hexagonal barato:
  CDI para inversão de dependência, interfaces de porta injetáveis, e uma
  separação natural entre a anotação JAX-RS/JPA (infraestrutura) e uma classe
  de domínio pura (POJO). Não é esforço extra — é o caminho que o próprio
  ecossistema já pavimenta.
- **Rails** amarra persistência e domínio por design: ActiveRecord *é* a
  camada de acesso a dados e o lugar convencional pra regra de negócio ao
  mesmo tempo ("fat models, skinny controllers" é o próprio idioma do
  framework, não um desvio dele). Forçar hexagonal em Rails significaria lutar
  contra o framework — introduzir uma camada de domínio isolada de
  ActiveRecord exigiria replicar manualmente o que o Rails já resolve
  (mapeamento objeto-relacional, callbacks, validações), sem ganho real de
  testabilidade que já não exista via `ActiveRecord::Base` mockável em specs.

**Alternativa rejeitada — hexagonal nas duas stacks, por simetria.** Rejeitada
porque `CLAUDE.md` exige explicitamente "idiomatic to its ecosystem" pra cada
stack, e forçar uma arquitetura estranha ao Rails produziria código que um
entrevistador Rails reconheceria como não-convencional — o oposto do que o
modo de mentoria Ruby deste projeto busca (aprender o Rails-way de verdade,
não uma tradução literal do Java).

**Onde cada uma das 6 regras de negócio vive:**

| Regra | Quarkus (domain layer) | Rails (ActiveRecord model) |
|---|---|---|
| 1. Arquivar bloqueado por tarefa `in_progress` | Entidade `Project` (ou equivalente), verificada na transição de status | Modelo `Project`, validação/callback antes de persistir `status: archived` |
| 2. Só tarefa `pending` pode ser deletada | Entidade `Task`, guarda de exclusão | Modelo `Task`, validação/callback em `destroy` |
| 3. `completedAt` setado internamente | Entidade `Task`, método de domínio que transiciona pra `done` e atribui o timestamp | Modelo `Task`, callback (`before_save`/`around_save`) acoplado à transição de status |
| 4. Projeto arquivado não aceita tarefa nova | Entidade `Project`/`Task`, checado na criação | Modelo `Task`, validação de criação que consulta o projeto pai |
| 5. Transição de status forward-only | Entidade `Task`, guarda de transição | Modelo `Task`, validação de transição de estado |
| 6. Status travado se projeto arquivado | Entidade `Task`, checado antes da regra 5 (precedência — ver item 4) | Modelo `Task`, mesma precedência |

Em ambas as stacks, nenhuma regra de negócio vive em controller/resource — é
requisito não-negociável do `CLAUDE.md`, não uma preferência de estilo.

---

## 3. Modelo de erros: RFC 7807, taxonomia de 400, e 400 vs 422

Todo erro da API usa `ProblemDetails` (RFC 7807): `type` (URI estável,
identificador — não necessariamente um link resolvível), `title`, `status`,
`detail`, `instance` opcional. Violação de regra de negócio (`422`) sempre
tem um `type` URI próprio e distinto por regra — nunca um `type` genérico
reaproveitado entre regras diferentes.

**Taxonomia de `400` por contexto**, não um único componente genérico:

- `PathParamBadRequest` — `id` de path não é UUID válido (`ProblemDetails`
  simples, sem `errors[]`).
- `ProjectStatusFilterBadRequest` / `TaskFiltersBadRequest` — filtro de query
  fora do enum (`ProblemDetails` simples, um problema por resposta).
- `ProjectBodyBadRequest`, `ProjectPatchBodyBadRequest`,
  `TaskCreateBodyBadRequest`, `TaskPatchBodyBadRequest` — corpo malformado
  (`ValidationProblemDetails`, com `errors[]` reportando todos os campos
  violados de uma vez).

Cada componente carrega **só** os exemplos que aquele endpoint específico
pode de fato produzir — decisão que só chegou nessa forma depois de duas
tentativas mais genéricas (um único `BadRequest` compartilhado, depois três
componentes por categoria path/query/body) terem sido descartadas por
vazarem exemplo irrelevante pra endpoint que não pode produzi-lo (ver
`ai/revisoes.md`, entradas 3 e 4).

**400 vs 422 — critério.** `400` é reservado para requisição malformada
(violação de schema): campo ausente, fora de enum, tipo errado, campo
desconhecido ou `readOnly`. `422` é reservado para requisição bem-formada que
viola um invariante de domínio. A distinção não é gravidade — é sintaxe vs.
semântica de negócio.

**Caso `completedAt` enviado manualmente em `PATCH /tarefas/{id}` = `400`,
não `422`.** `completedAt` é `readOnly`, atribuído internamente pelo domínio
quando a tarefa completa (regra 3). Sua presença no corpo da requisição é
equivalente, em natureza, a enviar `id` ou `createdAt`: um problema de forma
do contrato, não uma regra de negócio sendo violada por uma requisição bem
formada. Reservamos `422` só para os 4 invariantes de domínio reais que
dependem de estado (arquivar com tarefa `in_progress`, deletar tarefa
não-`pending`, criar tarefa em projeto arquivado, transição de status
inválida/travada). Essa decisão foi tomada cedo (fase de refinamento do
contrato, antes da primeira revisão adversarial) e sobreviveu intacta a
todas as rodadas seguintes.

`additionalProperties: false` em todo schema de request é o mecanismo real
de enforcement — `readOnly: true` sozinho é só metadado de documentação no
OpenAPI 3.0 e não é respeitado pela maioria dos validadores de schema. Ver
`ai/revisoes.md` entrada 1 para o racional completo dessa correção.

---

## 4. Modelo de precedência completo: 400 → 404 → 422

Quando uma requisição poderia falhar de mais de uma forma, a ordem de
checagem é fixa e para na primeira falha:

```
400 (malformado)  →  404 (recurso não existe)  →  422 (regra de negócio)
```

Uma requisição que falha num estágio anterior nunca chega ao próximo — por
exemplo, um `id` de path que não é UUID sempre retorna `400`, nunca `404`,
independente de existir ou não um recurso com aquele literal.

**Dentro do próprio estágio 400**, sub-ordem também fail-fast:

```
path parameters  →  query parameters  →  request body
```

Só a primeira categoria que falha é reportada. `PATCH /tarefas/nao-e-um-uuid`
com corpo inválido retorna só o problema de path parameter, nunca os dois
juntos. Racional: o path parameter identifica *qual* recurso a requisição
endereça — não faz sentido validar mais nada antes disso estar resolvido — e
essa ordem já é o comportamento nativo de ambos os frameworks-alvo (JAX-RS
resolve path antes de invocar validação de corpo; roteamento do Rails resolve
segmentos de path antes do controller processar params). Documentar isso não
força nenhuma stack a se comportar diferente do que já faria por padrão — é
esse fato, aliás, que torna a escolha barata: nenhuma stack precisa de
configuração extra pra convergir.

**Dentro do próprio estágio 422**, precondições de estado são checadas antes
de regras de transição/valor: em `PATCH /tarefas/{id}`, a regra 6 (o projeto
da tarefa está arquivado?) é checada antes da regra 5 (a transição pedida é
válida?). Isso importa porque as duas podem se aplicar ao mesmo tempo: pela
regra 1, todo projeto arquivado só pode ter tarefas `pending` ou `done` (uma
tarefa `in_progress` teria bloqueado o próprio arquivamento) — então
*qualquer* tentativa de mudar `status` numa tarefa dessas viola a regra 5
(transição inválida) **e** a regra 6 (travado por projeto arquivado) ao
mesmo tempo. Sem essa precedência declarada, dois implementadores
conformantes ao spec poderiam legitimamente retornar `type` URIs diferentes
para a mesma requisição.

**Por que isso garante resposta idêntica entre as duas stacks:** cada ponto
de possível ambiguidade — 400 vs 404 vs 422, path vs query vs body dentro do
400, precondição vs transição dentro do 422 — tem exatamente uma ordem
declarada e uma única resposta possível. Não sobra nenhum ramo de decisão
deixado para o framework de cada stack resolver "do jeito que ele resolve por
padrão". Essa precedência não surgiu de uma vez: foi fechada em camadas
sucessivas ao longo de 5 rodadas de revisão adversarial (ver item 9) — cada
rodada fechando uma colisão só pra revelar a mesma classe de ambiguidade um
nível de abstração abaixo (primeiro 422-vs-422, depois 400-vs-400).

---

## 5. Regra de negócio 6 — o furo do invariante retroativo

Descoberta na primeira rodada de revisão adversarial (`spec-reviewer`, 19
achados), não em revisão própria: a regra 1 ("projeto só arquiva se nenhuma
tarefa está `in_progress`") tinha sido modelada como validação pontual,
disparada só no ato de arquivar — não como invariante mantido ao longo do
tempo. Como arquivamento só é bloqueado por tarefa `in_progress` (tarefa
`pending` é permitida dentro de projeto já arquivado), nada impedia essa
tarefa `pending` de transicionar para `in_progress` *depois* que o projeto já
estava arquivado, produzindo exatamente o estado que a regra 1 existe para
impedir.

**Correção**: regra de negócio 6, nova e distinta — uma tarefa de projeto
arquivado não pode mudar `status`; `title`, `description` e `priority`
continuam editáveis (blacklist de um único campo travado, não whitelist
implícita — a primeira formulação, whitelist de `title`/`description`,
deixava `priority` ambíguo e foi corrigida na rodada seguinte).

**Semântica de no-op, por campo**: reenviar o `status` atual de uma tarefa
não é uma transição — é aceito com `200`, mesmo que o projeto esteja
arquivado, porque um no-op não é uma *mudança* de status. Isso é por campo,
não por requisição inteira: outros campos enviados no mesmo PATCH (ex.:
`title`) aplicam normalmente, e PATCH continua atômico em violação de regra
(se qualquer parte da requisição viola uma regra, a requisição inteira é
rejeitada, nada é persistido parcialmente). A formulação "por campo" só
chegou depois de uma rodada intermediária que dizia "o request inteiro não
faz nada", ambígua sobre se outros campos no mesmo corpo seriam aplicados.

**Desarquivamento é permitido**: `PATCH /projetos/{id}` pode mover `status`
de `archived` de volta para `active`. Nenhum invariante guarda essa direção
(a regra 1 só guarda o sentido de arquivar), e reativar o projeto torna suas
tarefas transicionáveis de novo — uma tarefa `pending` presa pela regra 6
pode voltar a mudar de status assim que o projeto volta a `active`.

---

## 6. Convenções transversais que evitam divergência observável

Um conjunto de decisões de baixo custo de implementação (não exigem
configuração extra em nenhuma das stacks) que fecham divergência que, de
outro modo, ficaria a cargo do default de cada framework:

- **Ordenação determinística nas listagens**: `createdAt` ascendente, `id`
  ascendente como desempate. `createdAt` sozinho não é ordem total — dois
  registros podem ser criados no mesmo milissegundo (comum em fixtures/seeds
  de teste rodando em lote), e a ordem entre eles ficaria indefinida de novo
  sem o desempate. Sem essa declaração, `ORDER BY` implícito de JPA/Panache e
  de ActiveRecord herdariam a ordem que o próprio query planner de cada banco
  decidisse — não é garantido que sejam a mesma, nem que seja estável entre
  execuções.
- **Datetimes sempre UTC, sufixo `Z` literal**: nunca offset numérico
  (`-03:00`). Sem essa decisão, Jackson (Quarkus) tende a emitir `Z` por
  padrão, mas `ActiveSupport::TimeWithZone` (Rails) serializa no fuso horário
  configurado da aplicação, com offset — mesmo instante, string diferente no
  corpo da resposta, dependendo de qual stack responde.
- **Trailing slash fora do contrato**: paths canônicos não têm barra final
  (`/projetos`, nunca `/projetos/`). Requisição pra forma não-canônica tem
  comportamento não especificado, excluída de teste de conformidade — mais
  barato que obrigar as duas stacks a normalizar ou rejeitar de forma
  idêntica, já que RESTEasy/JAX-RS e o roteamento do Rails não normalizam
  isso da mesma forma por padrão.
- **Query params desconhecidos são ignorados** (tolerant reader): `?foo=bar`
  não é rejeitado. Decisão deliberada, não omissão — casa com o
  comportamento nativo padrão de ambos os frameworks, e OpenAPI 3.0 não tem,
  pra query string, um mecanismo equivalente a `additionalProperties: false`
  pra impor o contrário.
- **Nomes de componentes em inglês, paths em português**: `schemas`,
  `parameters`, `responses`, `examples` e `operationId` são todos em inglês
  (`Project`, `CreateTaskRequest`, `createProject`); paths permanecem em
  português (`/projetos`, `/tarefas`), a língua do domínio de negócio deste
  desafio. Os dois eixos são independentes — nomenclatura de schema não
  determina nomenclatura de URL, e vice-versa. `title`/`detail` dentro de
  `ProblemDetails` são em português, já que é a língua real que um consumidor
  desta API veria.

---

## 7. Persistência: PostgreSQL via Testcontainers

Testes de integração usam PostgreSQL real via Testcontainers, não SQLite ou
banco em memória — desvio deliberado do que o enunciado do desafio sugeria
como caminho mais simples.

**Por quê**: este projeto trata timezone/formato de datetime como parte
explícita do contrato (item 6) — exatamente o tipo de comportamento que
diverge entre SQLite (sem tipo `timestamptz` nativo, datas frequentemente
tratadas como texto) e Postgres (tipo `timestamptz` nativo, é o banco real de
produção em ambos os stacks). Rodar os testes de integração contra o mesmo
motor de banco que rodaria em produção elimina uma fonte inteira de "passou
no teste, quebrou em produção" — o tipo de divergência que motivou várias
das decisões deste documento. Testcontainers mantém isso hermético e
reproduzível (sobe/derruba um Postgres descartável por execução de suíte),
sem exigir um Postgres compartilhado de verdade rodando full-time.

**Trade-off honesto**: suíte de integração mais lenta pra subir (cada run
inicializa um container) e exige Docker disponível em CI e na máquina de
desenvolvimento — SQLite/in-memory seria instantâneo e sem dependência
externa. Aceito porque o ganho (paridade com produção, detecção precoce de
divergência de tipo/timezone entre bancos) supera o custo de tempo de
inicialização, que é de segundos, não minutos, com a imagem `postgres:*`
já em cache.

---

## 8. Pirâmide de testes

```
unit (domínio Java / model specs Rails)
        ↓
mutation (PIT no domínio Quarkus; mutant em app/models Rails, se viável)
        ↓
integração de adapters (Testcontainers PostgreSQL)
        ↓
contrato (RestAssured + validador de schema / committee gem)
        ↓
Schemathesis em CI, contra as duas APIs rodando
```

Cada camada testa uma classe de erro diferente, subindo em custo/tempo de
execução e descendo em granularidade:

- **Unit**: regra de negócio isolada, sem I/O — a camada mais barata e mais
  numerosa.
- **Mutation (PIT no Quarkus; `mutant` no Rails, se viável)**: garante que os
  testes unitários realmente matam mutantes na camada de domínio/aplicação,
  não só alcançam cobertura de linha sem asserção efetiva.
- **Integração de adapters**: mappers JPA↔domínio (Quarkus) e persistência
  ActiveRecord real (Rails) contra Postgres de verdade (item 7).
- **Contrato**: cada stack validada contra `spec/openapi.yaml` isoladamente.
- **Schemathesis**: última linha de defesa, gerando requisições a partir do
  contrato e comparando comportamento das duas APIs vivas — a única camada
  que compara Quarkus e Rails diretamente entre si, não cada um contra o
  schema isoladamente.

`mutant` (mutation testing em Ruby) é listado como "se viável" porque, ao
contrário do PIT no ecossistema Java, sua adoção em Rails é menos
padronizada e pode não compensar o custo de configuração dentro do escopo
deste desafio — decisão de bolso, não recusa categórica.

---

## 9. Processo de revisão da spec

`spec/openapi.yaml` passou por 6 rodadas de revisão adversarial via subagente
`spec-reviewer` (contexto isolado, sem viés de quem escreveu a spec), mais
uma varredura final dirigida por checklist. Contagem de achados acionáveis
por rodada: **19 → 16 → 8 → 6 → 4 → 1**, seguida por **2** achados na
varredura final.

**Critério de parada explícito** (aplicado a partir da rodada 5): um achado
só bloqueia se muda comportamento observável entre uma implementação Quarkus
e uma Rails construídas estritamente a partir do texto do spec. Achado que é
só nitpick de documentação (estilo, `instance` opcional presente ou ausente,
cross-reference genérico) é sinalizado como não-bloqueante, não usado como
desculpa para mais uma rodada. Mesmo depois de a fase ter sido declarada
"encerrada" ao fim da rodada 5, uma sexta rodada foi pedida (e achou a
ausência de ordenação de listagem — ver item 6) e uma varredura adicional
depois dessa (timezone e trailing slash — também item 6).

**Lição metodológica, a mais importante deste processo**: revisão adversarial
de texto — humana ou de agente — é boa para achar "isso que o spec diz está
errado, incompleto ou contraditório", mas estruturalmente fraca para achar
"isso que o spec deveria dizer e nunca disse". A primeira categoria (a
maioria dos achados das rodadas 1–5: conflitos entre regras, exemplos
desalinhados, componentes órfãos) aparece porque há algo escrito para
comparar contra o que deveria estar escrito. A segunda categoria
(propriedades transversais como ordenação de listagem, timezone de datetime,
trailing slash) não tem "o que está errado" para apontar — só "o que falta e
nem foi tentado", que é mais fácil de escapar de qualquer revisão dirigida
por releitura, seja ela humana ou de agente. A ordenação de listagem, por
exemplo, sobreviveu a 5 rodadas (49 achados somados) sem que nenhuma delas a
mencionasse — só apareceu na sexta rodada, e mesmo assim por acidente (o
usuário pediu mais uma passada depois da fase já ter sido declarada
encerrada). A resposta estrutural a essa lição foi a varredura final: um
checklist fechado e deliberado de propriedades transversais (ordenação,
timezone, encoding, casing, trailing slash, content-type), rodado à parte de
qualquer rodada adversarial genérica — porque perguntar "o que está errado
no que já foi escrito?" nunca ia gerar a pergunta certa, que era "o que nunca
foi escrito?".
