# Decisões de Design — TaskFlow

Este documento consolida as decisões de design já tomadas e defendidas ao
longo do projeto. Não é um changelog: é o racional por trás de cada decisão,
incluindo alternativas rejeitadas e seus trade-offs. Fontes: `spec/openapi.yaml`
(congelado), `CLAUDE.md`, e o histórico de revisão em `ai/revisoes.md` e
`ai/prompts.md`.

---

## 1. Por que spec-first, e como prevenimos drift

`spec/openapi.yaml` é a fonte única da verdade: a implementação nunca decide
comportamento por conta própria. O risco real de implementar a partir de um
contrato é drift silencioso — sob pressão de prazo, a implementação tende a
resolver ambiguidade do jeito que seu framework favorece por padrão, e essa
escolha se cristaliza sem ninguém perceber até um cliente real bater na
diferença entre o que a spec promete e o que o código faz.

Prevenção de drift, em três camadas:

1. **Testes de contrato contra o próprio schema.** RestAssured combinado com
   um validador de schema OpenAPI valida requisição e resposta reais contra
   `spec/openapi.yaml` — não contra uma cópia reescrita ou um schema
   gerado a partir do próprio código.
2. **Schemathesis em CI, contra a API rodando.** Testes baseados em
   propriedade gerados a partir do próprio `openapi.yaml`. Isso pega
   divergência comportamental que testes unitários não pegam, porque explora
   o espaço de entradas a partir do contrato, não a partir do que o
   desenvolvedor lembrou de testar manualmente.
3. **Diff entre a spec gerada pelo `smallrye-openapi` (extensão padrão do
   Quarkus, que introspecciona as anotações JAX-RS/Jakarta do código e produz
   um OpenAPI derivado) e a spec manual congelada.** A hierarquia nunca
   inverte: divergência encontrada nesse diff é sempre bug de implementação a
   corrigir no código Quarkus, nunca motivo para atualizar `spec/openapi.yaml`
   a partir do gerado. Esse diff existe só para detectar quando a
   implementação Quarkus silenciosamente expôs um campo, endpoint ou status
   code que a spec não previu.

**Alternativa rejeitada — code-first (spec gerada a partir das anotações do
código).** Rejeitada porque contradiz a metodologia SDD que o próprio
`CLAUDE.md` declara: um contrato derivado de anotações herda as decisões
default do framework em vez de decisões revisadas, e não passa pela revisão
adversarial centralizada que a spec manual recebeu (ver item 9) — o contrato
deixaria de ser independente de implementação, que é exatamente a
propriedade que este projeto defende (ver item 10).

---

## 2. Arquitetura hexagonal com modelo de domínio rico

A implementação usa arquitetura hexagonal: a camada de domínio tem zero
imports de framework — nenhum `jakarta.*`/JPA/Quarkus; entidades JPA existem
só nos adapters, com mappers de tradução entre entidade JPA e objeto de
domínio. Toda regra de negócio vive na entidade de domínio, nunca em
resource/controller — requisito não-negociável do `CLAUDE.md`, não uma
preferência de estilo.

Hexagonal no Quarkus não é esforço extra: o ecossistema tem, nativamente, os
elementos que tornam a arquitetura barata — CDI para inversão de
dependência, interfaces de porta injetáveis, e uma separação natural entre a
anotação JAX-RS/JPA (infraestrutura) e uma classe de domínio pura (POJO). É
o caminho que o próprio ecossistema já pavimenta.

**Onde cada uma das 6 regras de negócio vive** (todas na camada de domínio):

| Regra | Onde vive |
|---|---|
| 1. Arquivar bloqueado por tarefa `in_progress` | Entidade `Project`, verificada na transição de status |
| 2. Só tarefa `pending` pode ser deletada | Entidade `Task`, guarda de exclusão |
| 3. `completedAt` setado internamente | Entidade `Task`, método de domínio que transiciona pra `done` e atribui o timestamp |
| 4. Projeto arquivado não aceita tarefa nova | Entidade `Project`/`Task`, checado na criação |
| 5. Transição de status forward-only | Entidade `Task`, guarda de transição |
| 6. Status travado se projeto arquivado | Entidade `Task`, checado antes da regra 5 (precedência — ver item 4) |

**Alternativa rejeitada — modelo anêmico com regras em services/resources.**
Rejeitada porque espalha os invariantes por camadas que o mutation testing
(item 8) não cerca com a mesma força, e porque cada ponto de entrada novo
teria que *relembrar* as regras em vez de esbarrar nelas na entidade — com
as guardas no domínio, o estado inválido é inconstruível, independente de
quem chama.

**Nota histórica**: até o corte de escopo (item 10), o plano previa uma
segunda implementação Rails deliberadamente *assimétrica* — Rails-way
idiomático, modelos ActiveRecord ricos, sem hexagonal, porque forçar uma
camada de domínio isolada de ActiveRecord seria lutar contra o framework. O
princípio que sustentava a assimetria permanece na entrega: a arquitetura
serve ao idioma do ecossistema (hexagonal porque é barato *no Quarkus*), não
a uma estética universal; e a regra de negócio vive no modelo/entidade em
qualquer idioma.

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

### Interpretações fixadas pela implementação (promovidas à spec)

Duas perguntas que a spec original deixava em aberto foram respondidas
durante a implementação Quarkus, flagradas pela matriz de cobertura do
`contract-tester` como asserções sem respaldo em texto normativo, e então
**promovidas a texto da spec** — eram lacunas do contrato descobertas pela
implementação, não drift do código:

**JSON imparseável / shape não-vinculável → `invalid-request-body`.** A
spec descrevia os `400` de corpo como violações de schema de um corpo *já
parseado*; não dizia o que acontece com `{invalid` (sintaxe quebrada) ou
`{"name": {"nested": true}}` (shape que não vincula ao tipo declarado).
Leitura fixada: mesma falha de estágio de corpo — `400` com o mesmo type
URI `invalid-request-body`, RFC 7807 — com payload **determinístico**, como
exceção explícita à política de "todos os campos violados de uma vez"
(que vale para validação de schema de corpo já vinculado): corpo
sintaticamente imparseável reporta sempre a entrada única `field: body`;
corpo parseável com valor não-vinculável é fail-fast em ordem de documento
e reporta só o primeiro campo — o binding aborta antes de a validação de
schema ver o resto. Racional: *least surprise* — para o cliente, corpo que
não parseia e corpo que viola schema são o mesmo defeito ("meu corpo está
errado"), e a alternativa (resposta nativa de parse do framework) vazaria
formato fora da taxonomia RFC 7807, diferente em cada framework. O
determinismo do payload veio da revisão adversarial da promoção (ver nota
de processo): a primeira redação ("campo nomeado quando a posição do parse
permite") deixava o `errors[]` dependente do parser — Jackson expõe path,
`JSON.parse` do Ruby só linha/coluna — e duas implementações conformes
divergiriam.

**`format: uuid` = forma textual canônica do RFC 4122.** A spec só dizia
`type: string, format: uuid`; `format` em OpenAPI é frouxo, e parsers de
plataforma divergem exatamente na borda: `1-1-1-1-1` é aceito pelo
`UUID.fromString` leniente do Java e rejeitado por um constraint de regex
típico do Rails. Leitura fixada: canônico 8-4-4-4-12 hexadecimal, dígitos
hex aceitos sem distinção de caixa (regra de input case-insensitive do
próprio RFC 4122) — só o shape de agrupamento/comprimento é imposto; forma
não-canônica é `400 invalid-path-parameter` (exemplo `1-1-1-1-1` agora na
spec). Racional: a fronteira do contrato precisa ser independente de
plataforma — "o que o parser da linguagem tolera" não é contrato. A
cláusula de caixa também veio da revisão adversarial: "canônico" sozinho é
autocontraditório (RFC 4122 emite minúsculas mas aceita input em qualquer
caixa), e cada implementação leria de um jeito.

**Nota de processo.** As duas lacunas foram encontradas pela suíte de
contrato (que as asseria sem que a spec as prometesse) e promovidas a
texto da spec em vez de permanecerem convenção implícita dos testes Java —
assim qualquer implementação futura (à época do achado, o plano previa uma
segunda, em Rails — ver item 10) implementa as duas respostas a partir do
contrato, não por engenharia reversa dos testes existentes. A promoção
passou pelo mesmo processo de qualquer mudança de spec: revisão adversarial
do `spec-reviewer`, que derrubou a primeira redação por indeterminismo de
payload (3 bloqueadores, todos "o *que* está fixado, mas o payload ainda
deixa implementações conformes divergirem") — cada exceção de determinismo acima é
resposta a um deles, e cada uma ganhou teste de contrato pinando o
comportamento. Fluxo SDD na direção correta: lacuna descoberta → spec
atualizada (com revisão) → código conforme.

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
essa ordem já é o comportamento nativo dos frameworks HTTP típicos (JAX-RS
resolve path antes de invocar validação de corpo; roteamento do Rails resolve
segmentos de path antes do controller processar params). Documentar isso não
força a implementação a se comportar diferente do que já faria por padrão — é
esse fato, aliás, que torna a escolha barata: nenhuma configuração extra é
necessária pra convergir.

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

**Por que isso garante resposta idêntica entre implementações
independentes:** cada ponto de possível ambiguidade — 400 vs 404 vs 422,
path vs query vs body dentro do 400, precondição vs transição dentro do 422
— tem exatamente uma ordem declarada e uma única resposta possível. Não
sobra nenhum ramo de decisão deixado para o framework resolver "do jeito que
ele resolve por padrão". Essa precedência não surgiu de uma vez: foi fechada em camadas
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

Um conjunto de decisões de baixo custo (não exigem configuração extra na
implementação) que fecham divergência que, de outro modo, ficaria a cargo
do default do framework — e variaria entre frameworks:

- **Ordenação determinística nas listagens**: `createdAt` ascendente, `id`
  ascendente como desempate. `createdAt` sozinho não é ordem total — dois
  registros podem ser criados no mesmo milissegundo (comum em fixtures/seeds
  de teste rodando em lote), e a ordem entre eles ficaria indefinida de novo
  sem o desempate. Sem essa declaração, o `ORDER BY` implícito do ORM
  (JPA/Panache, ActiveRecord, qualquer outro) herdaria a ordem que o próprio
  query planner do banco decidisse — não é garantido que seja estável entre
  execuções, nem igual entre implementações.
- **Datetimes sempre UTC, sufixo `Z` literal**: nunca offset numérico
  (`-03:00`). Sem essa decisão, o formato ficaria a cargo do serializer de
  cada ecossistema — Jackson tende a emitir `Z` por padrão, mas
  `ActiveSupport::TimeWithZone` (Rails), por exemplo, serializa no fuso
  horário configurado da aplicação, com offset — mesmo instante, string
  diferente no corpo da resposta, dependendo de quem implementa.
- **Trailing slash fora do contrato**: paths canônicos não têm barra final
  (`/projetos`, nunca `/projetos/`). Requisição pra forma não-canônica tem
  comportamento não especificado, excluída de teste de conformidade — mais
  barato que obrigar toda implementação a normalizar ou rejeitar de forma
  idêntica, já que frameworks (RESTEasy/JAX-RS, roteamento do Rails) não
  normalizam isso da mesma forma por padrão.
- **Query params desconhecidos são ignorados** (tolerant reader): `?foo=bar`
  não é rejeitado. Decisão deliberada, não omissão — casa com o
  comportamento nativo padrão dos frameworks HTTP típicos, e OpenAPI 3.0 não tem,
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
tratadas como texto) e Postgres (tipo `timestamptz` nativo, o banco real de
produção). Rodar os testes de integração contra o mesmo
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
unit (domínio)
        ↓
mutation (PIT em domínio + aplicação)
        ↓
integração de adapters (Testcontainers PostgreSQL)
        ↓
contrato (RestAssured + validador de schema OpenAPI)
        ↓
Schemathesis em CI, contra a API rodando
```

Cada camada testa uma classe de erro diferente, subindo em custo/tempo de
execução e descendo em granularidade:

- **Unit**: regra de negócio isolada, sem I/O — a camada mais barata e mais
  numerosa.
- **Mutation (PIT)**: garante que os testes unitários realmente matam
  mutantes nas camadas de domínio e aplicação, não só alcançam cobertura de
  linha sem asserção efetiva. Resultado da entrega: 100% de kill rate.
- **Integração de adapters**: mappers JPA↔domínio e persistência real contra
  Postgres de verdade (item 7).
- **Contrato**: requisições e respostas reais validadas contra
  `spec/openapi.yaml` — não contra um schema derivado do código.
- **Schemathesis**: última linha de defesa, gerando requisições a partir do
  próprio contrato — explora o espaço de entradas que o contrato declara,
  não os cenários que alguém lembrou de escrever.

---

## 9. Processo de revisão da spec

`spec/openapi.yaml` passou por 6 rodadas de revisão adversarial via subagente
`spec-reviewer` (contexto isolado, sem viés de quem escreveu a spec), mais
uma varredura final dirigida por checklist. Contagem de achados acionáveis
por rodada: **19 → 16 → 8 → 6 → 4 → 1**, seguida por **2** achados na
varredura final.

**Critério de parada explícito** (aplicado a partir da rodada 5): um achado
só bloqueia se muda comportamento observável entre duas implementações
independentes construídas estritamente a partir do texto da spec. Achado que é
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

---

## 10. Decisão de escopo — entrega single-stack (corte da implementação Rails)

**Decisão**: o projeto entrega apenas a implementação Quarkus. A implementação
Rails, planejada como segunda stack sobre o mesmo contrato, foi cortada.

**Contexto**: o enunciado do desafio pede *uma* API. A dupla implementação
(Quarkus + Rails) era meta adicional autoimposta — um instrumento para provar,
na prática, que o contrato era implementável de forma independente por dois
ecossistemas distintos.

**Por quê (trade-off tempo vs. profundidade)**: com o tempo disponível, a
escolha real era entre duas implementações rasas ou uma funda. Optou-se por
profundidade na stack única, e foi isso que o tempo recuperado comprou:

- **Mutation testing (PIT)** nas camadas de domínio e aplicação, com 100% de
  kill rate — os testes unitários provam as regras de negócio, não apenas as
  alcançam;
- **Suíte de contrato validada por schema** contra `spec/openapi.yaml`, com
  matriz de cobertura auditada pelo `contract-tester`;
- **Emendas de spec descobertas pela implementação** e promovidas a texto
  normativo com revisão adversarial (mapeamento de falha de parse, forma
  canônica de UUID — ver item 3).

**O que o corte NÃO invalida**: todo o trabalho de fechamento de divergência
cross-stack feito na spec — ordenação determinística de listagens, datetimes
UTC com `Z` literal, forma canônica de UUID, mapeamento de falha de parse para
RFC 7807, o modelo de precedência 400 → 404 → 422 — permanece na spec e
permanece valioso. Essas decisões nunca foram "custo do Rails": são o que
torna o contrato **independente de implementação**, que é exatamente a
alegação central de SDD. Uma segunda implementação (Rails ou qualquer outra)
continua construível a partir do contrato sozinho, sem engenharia reversa da
implementação Java — o corte remove a *demonstração* da propriedade, não a
propriedade.

---

## 11. TRACE excluído da varredura de contrato (Schemathesis)

**Decisão**: o job `contract` do CI roda Schemathesis com
`--exclude-checks unsupported_method`, desligando o check que fuzza métodos
HTTP fora dos definidos na spec (na prática, TRACE em toda rota).

**Contexto**: a primeira execução real do pipeline reportou 4 falhas com
`TRACE`: em três rotas o framework responde `405` sem o header `Allow`
(exigido pela RFC 9110), e em `/projetos/{id}/tarefas` responde `404` em vez
de `405`. Esse comportamento vem do dispatch HTTP padrão do quarkus-rest, não
de código de aplicação — nenhuma rota deste projeto declara ou trata `TRACE`.

**Por quê**: `spec/openapi.yaml` nunca definiu contrato para `TRACE` em
nenhuma operação — não há requisito nosso sendo violado. É uma lacuna real de
conformidade com a RFC 9110, mas do framework, não do contrato desta API;
corrigi-la exigiria interceptar `TRACE` manualmente em toda rota só para
adicionar um header `Allow`, sem nenhum requisito do enunciado ou da spec
pedindo isso. Excluir o check mantém o job fiel ao que **este contrato**
promete, em vez de fazer o CI fiscalizar RFC HTTP genérica não pactuada.

**O que isso não cobre**: os outros dois achados da mesma execução —
`status=` vazio sendo aceito como se ausente, e bytes de controle
alcançando o Postgres cru como 500 — eram violações reais do próprio
contrato (`invalid-query-parameter` e `invalid-request-body`, respectivamente)
e foram corrigidos no código, não silenciados no CI.

---

## 12. Fechamento prosa → schema: `pattern` para UUID canônico e nome/título não-vazio

**Decisão**: `spec/openapi.yaml` ganhou `pattern` em quatro pontos que já
tinham a regra certa em prosa/exemplo, mas não em JSON Schema formal:

- `ProjectIdParam` e `TaskIdParam`: `pattern:
  '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'`
  — hex case-insensitive, só o shape de agrupamento é imposto.
- `CreateProjectRequest.name`, `UpdateProjectRequest.name`,
  `CreateTaskRequest.title`, `UpdateTaskRequest.title`: `pattern: '.*\S.*'`
  somado (não substituindo) a `minLength`/`maxLength` já existentes —
  exige ao menos um caractere não-whitespace em algum ponto da string.

**Contexto**: a suíte de contrato (Schemathesis) reportou "schema validation
mismatch" em 6 operações — 3 por causa do `id` de path, 3 por causa de
`name`/`title`. Investigação (ver `ai/prompt-log.md` da sessão) mostrou que,
nos dois casos, a implementação já fazia a coisa certa e a spec já *dizia*
a coisa certa em prosa/exemplo — só não em JSON Schema:

- O item 3 deste documento (`format: uuid` = forma textual canônica) já
  fixava case-insensitividade em prosa (`info.description`) — mas
  `format: uuid` não é validado por engines de JSON Schema (é só anotação),
  então geradores baseados em schema (Schemathesis) não sabiam do shape
  8-4-4-4-12 nem da regra de caixa. `UuidPathParamFilter` já implementa
  exatamente esse regex; nenhuma mudança de código.
- O exemplo `ProjectNameInvalid`/`TaskTitleInvalid` já promete a mensagem
  "não pode estar em branco e deve ter no máximo N caracteres" — mas
  `minLength: 1` sozinho aceita uma string de um único espaço.
  `BodyValidation.invalidText` já rejeita via `isBlank()`; nenhuma mudança
  de código.

**Por quê não é uma decisão nova**: em ambos os casos a regra já existia e
já era testada (`uppercaseHexUuidPassesPathValidation`,
`emptyNameOnProjectCreate` e os exemplos citados) — só não estava expressa
de um jeito que uma ferramenta schema-only (Schemathesis, ou uma futura
segunda implementação lendo só o JSON Schema, sem ler a prosa) pudesse
derivar sozinha. Fechar esse gap é o próprio objetivo de SDD: o contrato
machine-readable deve bastar, sem depender de prosa que só humanos leem.

**Cuidado verificado antes de aplicar**: a leitura "UUID canônico" foi
cogitada como *lowercase-only* durante a triagem deste achado — rejeitada
porque contradiz a decisão já fixada no item 3 (case-insensitive é
deliberado, não descuido) e quebraria `uppercaseHexUuidPassesPathValidation`.
O `pattern` aplicado usa `[0-9a-fA-F]`, não `[0-9a-f]`, exatamente para não
reabrir essa decisão.
