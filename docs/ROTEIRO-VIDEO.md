# Roteiro do vídeo (até 3 minutos)

Antes de gravar: rode uma vez com `PEDIUPATRAO_GERAR_PEDIDOS=true`, deixe o sistema aberto em `localhost:8080` e um pedido novo em andamento (crie com o `atendente01`).

| Tempo | Tela | Fala (resumo) |
|---|---|---|
| 0:00–0:20 | Login com `gerente01` → menu **Dashboard** | "Este é o dashboard administrativo do Pediu Patrão. Só gerente e administrador acessam. Os números vêm de uma API própria que lê os pedidos no MongoDB." |
| 0:20–0:45 | Indicadores no topo | "Faturamento, pedidos, ticket médio, cancelamentos, descontos e tempo até a saída. As setas comparam com o período anterior do mesmo tamanho." |
| 0:45–1:15 | Gráfico de vendas: trocar Faturamento/Pedidos, Por dia → Por semana; horário de pico e dia da semana | "Aqui dá para ver a tendência e os horários de pico, que ajudam a decidir escala de funcionários e promoções." |
| 1:15–1:40 | Filtros: 7 dias, Entrega; ranking por Faturamento e categoria Pizza | "Os filtros valem para o painel inteiro e ficam na URL. O ranking mostra os produtos mais vendidos por quantidade ou faturamento." |
| 1:40–2:00 | Fila agora + Pedidos do período | "A fila mostra o que ainda está na pizzaria e marca atraso acima de 40 minutos. Embaixo, a lista paginada." |
| 2:00–2:30 | Navegador em `/api/dashboard/resumo?inicio=2026-11-10&fim=2026-11-01` (400) e logado como `atendente01` em `/dashboard` (403) | "A API valida os parâmetros e devolve erro claro. Atendente não acessa, e a tentativa vai para a auditoria." |
| 2:30–3:00 | GitHub: branches (gitflow), `docs/DASHBOARD.md`, Actions verde | "Decisões: o banco faz o recorte por período com índice; os cálculos ficam numa classe separada e testada; a API é só leitura. Tudo documentado no repositório." |
