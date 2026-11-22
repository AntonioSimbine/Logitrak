# LogiTrack — Linguagem simples para utilizadores

Regra geral: frases curtas, palavras do dia a dia em Moçambique, uma acção por mensagem.
Depois de dizer o que aconteceu, dizer o que fazer a seguir.

## 1. Vocabulário obrigatório

| Evitar | Usar sempre |
|---|---|
| Sincronizar / sincronização | Enviar registos à central / envio à central |
| Pendente de sincronização | Guardado no telemóvel. Aguarda envio. |
| Sincronizado | Enviado à central |
| Em sincronização | Envio em curso |
| Fora do geofence / Fora do raio | Está fora da área da fábrica. |
| Dentro do geofence | Está na área da fábrica. |
| Precisão GPS insuficiente | A localização ainda não é precisa. Vá para um local aberto e tente novamente. |
| SLA excedido / Limite SLA | O prazo definido para a viagem foi ultrapassado. / Prazo da viagem |
| KPIs | Resultados da operação |
| Erro de autenticação / Credenciais inválidas | Não foi possível entrar. Verifique o e-mail e a palavra-passe. |
| Sessão expirada | A sessão terminou. Entre novamente com ligação à Internet. |
| Fila offline | Registos guardados no telemóvel |
| Dispositivo (quando o utilizador sabe que é o telemóvel) | telemóvel |
| Cache / dados em cache | dados guardados no telemóvel |

Nomes de acções (usar sempre os mesmos):
- **Enviar registos à central** (botão principal que inicia o envio).
- **Enviar à central** (forma curta, em botões e cartões pequenos).
- **Envio em curso…** (enquanto decorre).
- **Tentar novamente** (depois de uma falha).
- **Guardar carregamento** (guardar no telemóvel).
- **Voltar**, **Continuar**, **Cancelar**, **Confirmar**.

## 2. Estados dos registos — não misturar

| Estado real | Texto para o motorista |
|---|---|
| Guardado só no telemóvel, ainda não enviado | Guardado no telemóvel. Aguarda envio. |
| A tentar enviar neste momento | Envio em curso… |
| O servidor confirmou que recebeu | Enviado à central |
| O servidor não aceitou | Não aceite: <motivo>. Corrija os dados e tente novamente. |
| Sem rede | Sem ligação. O registo ficou guardado no telemóvel. Será enviado à central quando a ligação voltar. |

**Nunca** mostrar “Enviado à central” antes da confirmação do servidor.
Nos cartões e chips usar as formas curtas: **Aguarda envio**, **Envio em curso**, **Enviado à central**, **Não aceite**.

## 3. Falhas — dizer a causa e a acção

| Causa | Mensagem |
|---|---|
| Sem Internet | Sem ligação. O registo ficou guardado no telemóvel. Será enviado à central quando a ligação voltar. |
| Central indisponível | A central não está a responder. Os registos ficam guardados no telemóvel. Tente novamente dentro de instantes. |
| Localização desactivada | A localização está desactivada. Abra as definições do telemóvel, active a localização e tente novamente. |
| Localização pouco precisa | A localização ainda não é precisa. Vá para um local aberto e tente novamente. |
| Fotografia em falta | Falta a fotografia da guia. Fotografe a guia e tente novamente. |
| Fotografia inválida | A fotografia não serve. Tire outra com boa luz e o documento inteiro dentro da moldura. |
| Campos por preencher | Faltam dados obrigatórios. Preencha os campos assinalados com *. |
| Sessão terminada | A sessão terminou. Entre novamente com ligação à Internet. |
| Carregamento não aceite | A central não aceitou o carregamento. Motivo: <motivo>. Corrija e tente novamente. |
| Sem espaço no telemóvel | Não há espaço livre no telemóvel. Envie os registos à central ou apague registos antigos. |

Detalhes técnicos (códigos HTTP, excepções, UUIDs, tempos limite) continuam só nos registos internos de apoio.

## 4. Linguagem por perfil

- **Motorista:** instruções curtas. Sempre: o que aconteceu, se os dados ficaram guardados, se a central recebeu, o que fazer a seguir.
- **Operador:** nomes claros para ocorrências, motivos e acções necessárias (“Abrir”, “Marcar lido”, “Rejeitar”, “Motivo da rejeição”).
- **Administrador:** explicar para que serve cada parâmetro e o efeito da mudança. Termos técnicos só quando ajudam, sempre com explicação curta (ex.: “Raio da área da fábrica (geofence): distância máxima permitida a partir da fábrica.”).

## 5. Apresentação

- Botões com texto novo e mais longo podem usar duas linhas ou tamanho de letra ligeiramente menor; nunca cortar palavras.
- Chips e cartões mostram a informação necessária para decidir; evitar ellipsis no estado e no motivo.
- Manter cores, ícones e espaçamento da identidade visual.

## 6. Verificação

### Verificação técnica — realizada em 23/11/2026

- Textos revistos em Android (`strings.xml`, `ApiErrors.kt`, `SyncManager.kt`, `EstadoUi.kt`), PWA (`frontend/m/`), web (`frontend/`) e mensagens da API.
- Greps por termos técnicos visíveis concluídos. Nomes de variáveis, chaves, comentários e registos internos mantidos (não aparecem ao utilizador).
- `node --check`: 17 ficheiros JavaScript alterados, sem erros de sintaxe.
- Android: `.\gradlew assembleDebug --no-configuration-cache --console=plain` com JDK 17 — **BUILD SUCCESSFUL** (42 tarefas).
  - Usar `$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot'`. O JDK 26 do ambiente quebra o `JdkImageTransform` (`jlink`), não é problema do código.
- Backend: `.\mvnw.cmd -B -q test` — **34 testes, 0 falhas, 0 erros**. As asserções antigas que esperavam textos técnicos foram atualizadas para as mensagens simples, sem alterar regras de negócio nem códigos HTTP.
- Apresentação: revisão estática dos cartões e mensagens. Acrescentado `ellipsize="end"` ao texto das notificações (`item_notificacao.xml`) para não cortar frases longas sem indicação. Não foi feito teste visual em ecrã real.

### Ainda pendente

- Publicar as alterações: rebuild do frontend/PWA, da API e da APK, mantendo os endereços do Railway em `docs/DEPLOY.md`.
- **Compreensão com utilizadores: pendente** até sessões reais de teste com motoristas, operadores e administradores serem realizadas. Nenhuma afirmação de compreensão foi validada com pessoas até agora.
- Ficheiro acidental `0` (untracked, com `24404` em UTF-16 LE) continua no repositório local; removê-lo só com confirmação.
