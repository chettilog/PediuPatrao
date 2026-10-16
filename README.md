# PediuPatrão - Atividade Prática M1 (Parte 2)

Aluno: Joao Pedro Marchetti
RGM: 11231102996

Esse projeto é a evolução do sistema de pedidos da pizzaria PediuPatrão. A ideia foi separar o que cada funcionário pode fazer, controlar o caminho do pedido (da entrada até a saída) e guardar um histórico de quem mudou o quê. Mantive a base que veio no projeto: Java 21, Spring Boot 3.4, Spring Security, Spring Data MongoDB e Thymeleaf.

## Como rodar

Precisa do JDK 21 e de um banco MongoDB (usei o MongoDB Atlas).

1. Clone o repositório e entre na pasta do projeto.
2. Informe a conexão do seu MongoDB na variável `SPRING_DATA_MONGODB_URI`. No Windows (cmd):

```
set SPRING_DATA_MONGODB_URI=mongodb+srv://USUARIO:SENHA@SEU-CLUSTER.mongodb.net/
```

No Linux/macOS: `export SPRING_DATA_MONGODB_URI="mongodb+srv://USUARIO:SENHA@SEU-CLUSTER.mongodb.net/"`

3. Rode o sistema:

```
mvnw.cmd spring-boot:run      (Windows)
./mvnw spring-boot:run        (Linux/macOS)
```

4. Abra `http://localhost:8080`.

Os dados ficam no banco `pizzaria_xetti`. Se quiser outro nome, é só usar a variável `PEDIUPATRAO_BANCO`.

Na primeira execução o sistema cria três usuários de teste e um cardápio básico:

| Usuário | Perfil |
|---|---|
| admin | ADMIN |
| gerente01 | GERENTE |
| atendente01 | ATENDENTE |

A senha dos três é `Patrao#2026` (pode ser trocada com `PEDIUPATRAO_SENHA_DEMO`).

Para rodar os testes: `mvnw.cmd test` (não precisa de banco, eles não acessam o Atlas).

## Permissões

| Ação | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Gerenciar usuários e perfis | sim | | |
| Consultar pedidos | sim | sim | sim |
| Registrar pedido e avançar status | | sim | sim |
| Aplicar desconto e cancelar pedido | | sim | |
| Consultar clientes | | sim | sim |
| Cadastrar, alterar e excluir clientes | | | sim |
| Manter o cardápio | | sim | |
| Consultar a auditoria | sim | sim | |

O bloqueio é feito no backend, em dois lugares: nas rotas, em `SecurityConfig`, e com `@PreAuthorize` nos services. Então mesmo que alguém chame a rota direto, sem passar pela tela, o servidor barra. Esconder botão na tela é só para ficar mais limpo.

## O que foi feito em cada requisito

**RF01 - Usuários e permissões**
- Criei o enum `Perfil` (ADMIN, GERENTE, ATENDENTE) no lugar do texto livre. Antes todo mundo entrava como USER; agora o `UsuarioDetailsService` usa o perfil salvo.
- A rota `/api/usuarios` era pública (qualquer pessoa conseguia criar um ADMIN). Agora é só do ADMIN e não devolve mais a senha.
- Só o ADMIN muda perfis. Ele não consegue alterar o próprio perfil nem se excluir, e o último ADMIN não pode ser rebaixado nem apagado.
- Reativei o CSRF. Os formulários mandam o token pelo Thymeleaf e os `fetch` pelo cabeçalho (`static/js/csrf.js`).
- Acesso negado devolve 403 (JSON na API) e a tentativa é registrada. Sem login, a API devolve 401.

**RF02 - Pedidos**
- O pedido agora tem itens (produto, quantidade, preço e subtotal), desconto, total, tipo de atendimento (entrega ou retirada), responsável e data/hora de entrada.
- O preço vem do cardápio e o total é calculado no servidor (`PedidoService.recalcular()`). Se a requisição mandar um `valorTotal`, ele é ignorado.
- Validações: cliente existente, produto ativo, quantidade de 1 a 50, pelo menos um item e endereço do cliente para entrega.

**RF03 - Fluxo do pedido**
- Ordem fixa: Recebido, Em preparação, Pronto, Saiu para entrega (ou Retirado) e Finalizado. Qualquer outra transição é recusada.
- A saída grava data/hora e quem fez. Todas as etapas ficam no histórico do pedido.
- Cancelado não entra como transição normal, só pela operação de cancelamento.

**RF04 - Desconto e cancelamento (só GERENTE)**
- Desconto de 0% a 20%, só antes da saída. Dá para reajustar (por exemplo, de 10% para 15%) e o total é recalculado. A validação do limite está no service, não só no formulário.
- Cancelamento exige justificativa (mínimo de 10 caracteres), só antes da saída, mantém todos os dados do pedido e bloqueia qualquer mudança depois.

**RF05 - Auditoria**
- Criei `EventoAuditoria`, `EventoAuditoriaRepository` e `AuditoriaService`, gravando no MongoDB.
- Cada campo alterado vira uma linha, com usuário, perfil, data/hora, operação, entidade (com id), valor anterior, valor novo e justificativa quando tem.
- São auditados: pedidos (inclusão, alteração, status, saída, desconto, cancelamento), clientes (inclusão, alteração, exclusão), além de produtos, usuários e tentativas de acesso negado.
- Não existe `save` nem `delete` para auditoria nas rotas comuns: a API só tem GET e a tela é somente leitura.
- Senha nunca aparece (só "definida" ou "redefinida") e o telefone é gravado mascarado, tipo `(11) *****-4321`.
- Consulta em `/auditoria` e `GET /api/auditoria`, só para ADMIN e GERENTE, com filtro por usuário, operação, entidade e id.

## Principais mudanças no projeto

- `config/SecurityConfig`: regras por perfil, CSRF e tratamento de 401/403.
- `config/DataInitializer`: cria os usuários de teste e o cardápio inicial.
- `entity/`: `Perfil`, `StatusPedido`, `TipoAtendimento`, `ItemPedido`, `MovimentacaoPedido`, `EventoAuditoria` e `TipoOperacao`.
- `service/`: regras de negócio e `@PreAuthorize`, mais o novo `AuditoriaService`.
- `controller/`: controladores de tela separados por assunto.
- `excecao/` e `seguranca/`: tratamento de erros e do usuário logado.
- `templates/pedidos/` e `templates/auditoria/`: telas novas. Também corrigi a tela de produtos e o escape de HTML na lista de clientes.

## Testando o bloqueio direto na API

Logado como `atendente01`, abra o console do navegador (F12, aba Console) e rode, trocando o id por um pedido existente:

```js
const id = 'ID_DO_PEDIDO';
fetch(`/api/pedidos/${id}/cancelamento`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ justificativa: 'tentativa sem permissão' })
}).then(r => r.json().then(j => console.log(r.status, j)));
```

O esperado é `403` com a mensagem "Seu perfil não tem permissão para esta operação.". Com o `gerente01` o mesmo comando cancela o pedido.
