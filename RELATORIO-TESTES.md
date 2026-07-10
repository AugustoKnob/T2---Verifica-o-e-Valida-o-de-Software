# Relatório de Análise de Testes — Online Shop (Spring Boot 2 + Angular 7)

> Documento parcial (Etapa 1). Cobre as partes de **leitura e análise** do trabalho:
> **Introdução**, **Análise crítica dos testes existentes** e **Resultado dos testes
> (defeitos encontrados)**. As seções *Objetivos (jornada de usuário)*, *Casos de teste* e a
> *implementação dos testes faltantes* (unitário/integração/sistema) ficam para a próxima etapa.

---

## 1. Introdução

### 1.1. Sistema sob análise
O sistema é uma aplicação web *full-stack* de **loja online** (carrinho de compras e gestão de
pedidos). É uma *Single Page Application* dividida em dois projetos:

- **Backend** (`/backend`): API REST em **Java 11 + Spring Boot 2.2**, com Spring Security,
  autenticação **JWT**, Spring Data JPA/Hibernate e PostgreSQL. Expõe endpoints para catálogo,
  carrinho, pedidos, usuários e categorias.
- **Frontend** (`/frontend`): cliente **Angular 7** (Angular CLI + Bootstrap) que consome a API
  em `localhost:8080/api`.

Principais regras de negócio (relevantes para testes que vão além de CRUD):
- **Estoque**: aumentar/diminuir estoque, com regra de "estoque insuficiente".
- **Pedido**: máquina de estados `NEW → FINISHED` / `NEW → CANCELED`, com restauração de estoque
  ao cancelar e bloqueio de transições inválidas.
- **Carrinho**: *merge* do carrinho local (visitante) com o carrinho persistente do usuário,
  somando quantidades de itens repetidos; *checkout* transforma o carrinho em pedido e baixa estoque.
- **Produto**: colocar/retirar de venda (`onSale`/`offSale`) com validação de status.

### 1.2. Onde obter o código-fonte
Projeto original público de Zhu Lin:
- Repositório: <https://github.com/zhulinn/SpringBoot-Angular7-ShoppingCart>
  (branches `backend` e `frontend`).
- Cópia local analisada: `SpringBoot-Angular7-Online-Shopping-Store-master-2/`.

### 1.3. Frameworks e APIs de teste utilizados
**Backend**
- **JUnit 4** (`org.junit`, `@RunWith`, `@Test`, `@Before`) — framework de execução.
- **Mockito** (`@Mock`, `@InjectMocks`, `Mockito.verify`, `when`) — *test doubles* (dublês)
  para isolar a classe sob teste das dependências (repositórios, encoder, etc.).
- **Hamcrest** (`assertThat`, `is`) — *matchers* de asserção.
- **Spring Test** (`SpringRunner`) — integração do ciclo de vida do Spring com o JUnit.
- Tudo trazido pelo *starter* `spring-boot-starter-test`.
- **JUnit Suite** (`@Suite`, `@Suite.SuiteClasses`) usado em `ShopApiApplicationTests`.

**Frontend**
- **Karma + Jasmine** (`ng test`) — configurado por padrão (`src/test.ts`, `karma.conf.js`),
  mas **sem nenhum arquivo `*.spec.ts`** de componente/serviço.
- **Protractor** (`ng e2e`, `e2e/protractor.conf.js`) — apenas o teste-modelo gerado pelo CLI.

### 1.4. Como compilar e rodar os testes

**Backend** (a partir de `/backend`):
```bash
# Toolchain pretendida pelo projeto: Java 11 + Maven
mvn test          # compila e executa os testes
# ou
mvn install       # build completo
```

> ⚠️ **Nota de ambiente (importante para reprodução).** O `pom.xml` fixa
> `spring-boot-starter-parent 2.2.0.BUILD-SNAPSHOT`, que resolve uma versão **antiga do Lombok**,
> incompatível com JDK 16+. Neste ambiente só havia JDK 17/20/21/23/25. A compilação com JDK 17
> falha com `IllegalAccessError ... jdk.compiler does not export com.sun.tools.javac.processing`.
> Contornou-se **sem alterar o código** exportando os módulos internos do compilador ao processo Maven:
> ```bash
> export JAVA_HOME=<caminho-do-jdk-17>
> export MAVEN_OPTS="--add-opens jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED \
>   --add-opens jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
> mvn test
> ```
> O ideal seria usar **JDK 11** (conforme documentado) — aí nenhum *workaround* é necessário.
> Os testes unitários usam apenas Mockito e **não** exigem PostgreSQL rodando.

**Frontend** (a partir de `/frontend`):
```bash
npm install
ng test     # testes unitários (Karma/Jasmine) — não há specs, roda vazio
ng e2e      # testes e2e (Protractor) — exige app no ar; teste-modelo quebrado (ver Análise)
```

---

## 2. Análise crítica da qualidade dos testes existentes

### 2.1. Inventário dos testes

| Arquivo | Nível | Métodos `@Test` | Observação |
|---|---|---|---|
| `service/impl/CartServiceImplTest` | Unitário | 7 | mock de repositórios |
| `service/impl/OrderServiceImplTest` | Unitário | 7 | cobre transições de status |
| `service/impl/ProductServiceImplTest` | Unitário | 15 | estoque / on-off sale |
| `service/impl/UserServiceImplTest` | Unitário | 3 | criação/atualização |
| `service/impl/CategoryServiceImplTest` | Unitário | 2 | busca por tipo |
| `service/impl/ProductInOrderServiceImplTest` | Unitário | 2 | item do carrinho |
| `api/CartControllerTest` | (rotulado "API") | 1 | **vazio, sem asserção** |
| `ShopApiApplicationTests` | Suite | 0 | apenas agrega os 6 acima |
| `frontend/e2e/src/app.e2e-spec.ts` | Sistema/E2E | 1 | *scaffold* do CLI, quebrado |

**Total backend: 36 métodos unitários úteis + 1 vazio.** Frontend: 0 unitários + 1 e2e inválido.

### 2.2. Quais níveis/técnicas da disciplina estão (ou não) presentes

- ✅ **Teste de unidade com dublês (mocks)**: bem representado na camada de *service*. A isolação
  do SUT via Mockito está correta (`@InjectMocks` + `@Mock`).
- ✅ **Teste de estado x teste de interação**: há um pouco dos dois — asserções de estado
  (status do pedido em `OrderServiceImplTest`) e verificação de interação (`verify(...).save()`).
- ⚠️ **Teste de integração**: **ausente**. Não há `@SpringBootTest`, `@WebMvcTest` (controller +
  MockMvc) nem `@DataJpaTest` (repositório/JPA). Nenhum dos 5 controllers é exercitado de verdade,
  nem a camada de persistência, nem a segurança/JWT.
- ⚠️ **Teste de sistema / aceitação (E2E)**: **efetivamente ausente**. Só existe o teste-modelo
  do Angular CLI, que nunca foi adaptado.
- ❌ **Técnicas de projeto de casos de teste** (partição de equivalência, análise de valor-limite,
  tabela de decisão): não há evidência de aplicação sistemática. Os casos parecem escritos "por
  intuição/por método", não a partir de um projeto de casos.
- ❌ **Cobertura estrutural medida**: não há JaCoCo nem relatório de cobertura configurado.

### 2.3. Problemas de qualidade (test smells)

1. **Teste vazio (`CartControllerTest.getCart`)** — método `@Test` sem corpo. Sempre passa e
   **não testa nada**: gera falsa sensação de cobertura da camada de API.

2. **Suite que substitui o *smoke test* de contexto e duplica execução** —
   `ShopApiApplicationTests` foi transformada numa `@Suite` que **re-executa** as 6 classes de
   *service*. Efeitos colaterais:
   - Perdeu-se o `contextLoads()` padrão do Spring Boot (nenhum teste garante que o
     `ApplicationContext` sobe — o *smoke test* mais básico de integração).
   - Todos os testes de *service* rodam **duas vezes** (via Suite e individualmente),
     inflando a contagem (73 execuções para 37 métodos distintos) sem ganho de qualidade.

3. **Asserção tautológica (`UserServiceImplTest.updateTest`)** —
   `assertThat(userResult.getName(), is(oldUser.getName()))`. Como `update()` retorna o próprio
   `oldUser` (mock de `save` devolve o argumento), compara-se o objeto **consigo mesmo**: passa
   independentemente da lógica. O correto seria assertar contra os campos do **`user` de entrada**
   (nome/telefone/endereço efetivamente copiados) e a **codificação da senha**.

4. **Teste verde "pela razão errada" (`UserServiceImplTest.createUserExceptionTest`)** — espera
   `MyException`, mas ela ocorre por um `NullPointerException` incidental (`save` mockado devolve
   `null` → `savedUser.setCart(...)` estoura → capturado no `catch`). Não valida o cenário real de
   erro de validação/integridade que o teste diz cobrir.

5. **Asserções fracas / centradas em interação** — vários testes só fazem
   `verify(repo, times(1)).save(...)` sem checar o **valor resultante**. Exemplos:
   - `ProductServiceImplTest.increaseStock/decreaseStock`: não verificam o novo valor do estoque.
   - `CartServiceImplTest.checkoutTest`: não verifica que o estoque baixou nem o status do pedido.
   - `CartServiceImplTest.mergeLocalCart*`: não assertam que a **quantidade foi somada** (regra de
     negócio central do *merge*), só que `save` foi chamado.

6. **Acoplamento a `times(2)` e detalhes de implementação** —
   `createUserTest` verifica `save(user)` exatamente 2×, prendendo o teste ao número de chamadas
   internas em vez do comportamento observável.

7. **Nomenclatura/typos e valores mágicos** — nomes como `updateProductStatusBiggerThenOne`,
   uso de literais (`"1"`, `10`) sem intenção explícita de valor-limite.

8. **Frontend sem testes reais** — não há nenhum `*.spec.ts`. O único e2e
   (`app.e2e-spec.ts`) é o *scaffold* do CLI: espera `<app-root h1>` com texto
   `'Welcome to shop!'`, mas `app.component.html` **não possui `<h1>` algum**. Ou seja, é um teste
   **morto/quebrado** que falharia se executado.

9. **JUnit 4 (legado)** — a disciplina normalmente adota JUnit 5; o projeto ainda usa JUnit 4 e o
   antigo `SpringRunner`.

### 2.4. Cobertura funcional — lacunas relevantes (além de CRUD)
Mesmo dentro da camada de *service*, faltam cenários de regra de negócio importantes, por exemplo:
- `decreaseStock`: só há o limite "comprar tudo" (que aliás expõe um defeito — ver §3); falta o
  caso normal de fronteira "comprar `estoque-1`" com verificação do valor final.
- `checkout`: fluxo com **estoque insuficiente** (deve propagar `PRODUCT_NOT_ENOUGH`) não é testado.
- `mergeLocalCart`: não valida a **soma de quantidades** para item repetido.
- `OrderService.finish/cancel` com pedido **inexistente** (`ORDER_NOT_FOUND`) não é testado.

---

## 3. Resultado dos testes (defeitos encontrados)

### 3.1. Execução
Execução com Maven (JDK 17 + *workaround* de módulos, ver §1.4):

```
Backend — BUILD SUCCESS
  CartServiceImplTest ............. Tests run: 7,  Failures: 0, Errors: 0
  CategoryServiceImplTest ......... Tests run: 2,  Failures: 0, Errors: 0
  OrderServiceImplTest ............ Tests run: 7,  Failures: 0, Errors: 0
  ProductInOrderServiceImplTest ... Tests run: 2,  Failures: 0, Errors: 0
  ProductServiceImplTest .......... Tests run: 15, Failures: 0, Errors: 0
  UserServiceImplTest ............. Tests run: 3,  Failures: 0, Errors: 0
  CartControllerTest .............. Tests run: 1,  Failures: 0, Errors: 0  (vazio)
  ShopApiApplicationTests (Suite) . Tests run: 36, Failures: 0, Errors: 0  (re-execução)
```

➡️ **Todos os testes automatizados existentes passaram** (nenhuma falha ou erro).

### 3.2. Defeitos e observações
Embora a suíte esteja verde, a *leitura* do código + testes revela defeitos:

| ID | Tipo | Descrição | Onde |
|---|---|---|---|
| **D1** | Defeito de teste | Teste vazio, sem asserção — cobertura ilusória da API | `CartControllerTest.getCart` |
| **D2** | Defeito de teste | Suite remove o *smoke test* de contexto e duplica execução | `ShopApiApplicationTests` |
| **D3** | *Test smell* | Asserção tautológica (objeto comparado consigo mesmo) | `UserServiceImplTest.updateTest` |
| **D4** | *Test smell* | Teste passa por `NullPointerException` incidental, não pelo cenário pretendido | `UserServiceImplTest.createUserExceptionTest` |
| **D5** | **Defeito de negócio** | `decreaseStock` usa `if (update <= 0)`: é **impossível comprar a última unidade** (comprar todo o estoque lança `PRODUCT_NOT_ENOUGH`). Deveria ser `< 0`. O teste `decreaseStockValueLesserEqualTest` **cristaliza o bug** como se fosse regra. | `ProductServiceImpl.decreaseStock:69` |
| **D6** | Defeito de teste | E2E do frontend é *scaffold* do CLI e está quebrado (`<h1>Welcome to shop!</h1>` não existe no HTML); nenhum teste unitário de frontend | `frontend/e2e/src/app.e2e-spec.ts` |
| **D7** | Ambiente/build | Versão *snapshot* do Spring Boot + Lombok antigo não compila em JDK ≥ 16 sem `--add-opens`; toolchain pretendida (JDK 11) não está mais alinhada com o ambiente | `pom.xml` |

**Destaque (D5):** este é o defeito mais relevante do ponto de vista de *regra de negócio* — um
cliente nunca consegue esvaziar o estoque de um produto (a última unidade fica "presa"). É um
forte candidato para a **jornada de usuário** e para os casos de teste de valor-limite da próxima
etapa (fronteira `estoque - quantidade == 0`).

### 3.3. Conclusão da Etapa 1
- Os testes existentes são **exclusivamente unitários** (camada de *service*, com Mockito),
  **passam todos**, mas têm **qualidade média**: asserções fracas/centradas em interação, um teste
  vazio, uma asserção tautológica, um teste verde por engano e ausência de projeto de casos por
  técnicas formais (equivalência/valor-limite/tabela de decisão).
- **Faltam níveis inteiros**: integração (controller/repositório/segurança) e sistema (E2E real).
- O frontend está **sem testes efetivos**.
- Há **1 defeito de negócio concreto (D5)** e **6 defeitos/observações de teste-ambiente**.

Esses pontos alimentam diretamente a próxima etapa (definição da jornada de usuário, projeto de
casos de teste e implementação dos testes unitário/integração/sistema faltantes).
