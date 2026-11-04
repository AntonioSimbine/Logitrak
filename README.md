# Logitrack

**Gestão e rastreabilidade de carregamentos para operações industriais em Moçambique.**

O Logitrack liga a central, as fábricas e os motoristas num único fluxo digital: o
motorista registra a guia e o GPS no telemóvel, mesmo sem rede, e a central acompanha
o carregamento em tempo real, recebe alertas de atraso e exporta relatórios.

> Projeto demonstrativo, preparado para evoluir para produção. Não inclui credenciais
> no repositório: o acesso é configurado por variáveis de ambiente.

---

## 🌐 Demo online

| O quê | Endereço |
|---|---|
| **Frontend publicado** | <https://antoniosimbine.github.io/Logitrak/> |
| Repositório | <https://github.com/AntonioSimbine/Logitrak> |
| API Spring Boot | local + túnel HTTPS (ver abaixo) ou Render — [`docs/DEPLOY.md`](docs/DEPLOY.md) |

O **frontend** é servido pelo GitHub Pages. A **API** precisa de um servidor com
JVM: o GitHub não executa Java nem MySQL.

### Ligar o site à API

O frontend usa `http://localhost:8080` por omissão. Para apontar para outra API,
abre o site **uma vez** com o parâmetro `api`:

```text
https://antoniosimbine.github.io/Logitrak/?api=https://a-tua-api.exemplo.com
```

O valor fica guardado no `localStorage` do browser e passa a ser usado por todas
as páginas, incluindo a app do motorista em `/m/`.

### Publicar a API a partir do teu PC (túnel temporário)

Enquanto não houver um servidor dedicado, é possível expor a API local com um
túnel HTTPS gratuito. Num único comando:

```powershell
.\start-online.ps1
```

> Se o Windows bloquear scripts, usa **`start-online.cmd`** (duplo clique).

O script arranca a API (se necessário), abre o túnel Cloudflare e imprime/copia o
link pronto, por exemplo:

```text
https://antoniosimbine.github.io/Logitrak/?api=https://algo-aleatorio.trycloudflare.com
```

> O link só funciona enquanto o teu computador estiver ligado, com a API e o
> `cloudflared` a correr. O endereço muda em cada arranque. Para um serviço
> permanente (24/7), segue o deploy no Render em [`docs/DEPLOY.md`](docs/DEPLOY.md).

![Logitrack](frontend/img/favicon.svg)

---

## 1. O que resolve

| Problema operacional | Como o Logitrack resolve |
|---|---|
| Guias em papel e informação dispersa | Guia digitalizada, com fotografia, hash SHA-256 e histórico |
| Motorista sem rede no local de carga | App **offline-first** com fila local e sincronização automática |
| Falta de visibilidade sobre a frota | Mapa, posições GPS, dashboard e KPIs em tempo real |
| Atrasos detetados tarde | Motor de SLA com alertas e notificações WebSocket |
| Relatórios manuais | Exportação em **PDF** e **Excel** a partir dos mesmos filtros |
| Acessos sem controlo | Perfis `ADMINISTRADOR`, `OPERADOR_CENTRAL` e `MOTORISTA` com JWT |

---

## 2. Perfis de utilização

| Perfil | Onde entra | O que faz |
|---|---|---|
| **Administrador** | Painel web | Configura fábricas, veículos, utilizadores e parâmetros; consulta tudo |
| **Operador da central** | Painel web | Acompanha carregamentos, alertas, mapa, KPIs e relatórios |
| **Motorista** | App móvel / PWA | Regista cargas, tira foto da guia, recolhe GPS e sincroniza a fila offline |

As credenciais de demonstração **não são publicadas**. Para obter acesso, o
administrador cria o utilizador no painel ou define a palavra-passe do seed em
`LOGITRACK_DEMO_PASSWORD` (ver secção 6).

---

## 3. Funcionalidades

### Painel web (W1–W10)

| Módulo | Página | Descrição |
|---|---|---|
| W1 Login | `/login.html` | Login unificado por JWT para os três perfis |
| W2 Dashboard | `/app/dashboard.html` | Indicadores em tempo real, mapa e últimos carregamentos |
| W3 Carregamentos | `/app/carregamentos.html` | Pesquisa, filtros, detalhe, timeline, GPS e guia digitalizada |
| W4 Alertas | `/app/alertas.html` | Alertas de atraso, leitura e contadores |
| W5 Relatórios | `/app/relatorios.html` | Filtros por período/fábrica/veículo/motorista/estado e exportação PDF/Excel |
| W6 KPIs | `/app/kpis.html` | Volume por fábrica, tempos médios e cumprimento de prazos |
| W7 Fábricas | `/app/admin/fabricas.html` | CRUD com geofence, SLA e horário de acostagem |
| W8 Utilizadores | `/app/admin/utilizadores.html` | CRUD, perfis, ativação e reposição de palavra-passe |
| W9 Veículos | `/app/admin/veiculos.html` | CRUD da frota, capacidade e estado |
| W10 Parâmetros | `/app/admin/parametros.html` | SLA, GPS e sincronização editáveis em tempo de execução |

### App do motorista (M1–M6)

| Ecrã | Descrição |
|---|---|
| M1 Login | Login online com JWT e cache seguro para login offline |
| M2 Dashboard | Resumo pessoal, carregamentos recentes e estado da ligação |
| M3 Novo registo | Guia, tipo de carga, toneladas, veículo, GPS e fotografia |
| M4 Fotografia | Câmara com moldura, compressão automática e pré-visualização |
| M5 Fila offline | Registos guardados no dispositivo, contagem de pendentes e reenvio |
| M6 Detalhe | Timeline, GPS, guia e estado de sincronização |

**Offline-first, na prática:**

- A fila e os caches ficam no **IndexedDB** (com fallback para `localStorage`).
- Barra de ligação: verde = servidor disponível; amarela = modo offline.
- Sincronização automática e manual; cada registo tem `uuidLocal` idempotente.
- O login offline usa um hash **PBKDF2-SHA256** guardado no dispositivo.
- A fotografia é comprimida para JPEG (lado máximo 1600 px) e validada no servidor.

---

## 4. Como usar a app do motorista

### Opção A — Instalar como PWA (recomendado)

Não precisa de loja de aplicações nem de APK.

1. Abre o endereço do frontend no **Chrome/Edge (Android)** ou **Safari (iPhone)**.
2. Entra em `/m/` (a app do motorista).
3. No menu do browser, escolhe **“Instalar aplicação”** / **“Adicionar ao ecrã inicial”**.
4. Abre o ícone **Logitrack** que aparece no telemóvel.
5. Inicia sessão com a conta de motorista fornecida pelo administrador.
6. Na primeira utilização, permite o acesso à **localização** e à **câmara**.
7. Regista a carga; se não houver rede, o registo fica na fila e sincroniza depois.

> A câmara, o GPS e a instalação PWA exigem **HTTPS** ou `localhost`. Num telemóvel
> na rede local, usa um proxy HTTPS (Cloudflare Tunnel, ngrok, Caddy) ou o site
> publicado.

### Opção B — APK Android nativo

Existem duas variantes no repositório:

| Pasta | Tecnologia | Como gerar |
|---|---|---|
| `driver-app-android/` | Kotlin nativo (Jetpack, Room, CameraX) | Abrir no Android Studio ou `gradlew assembleDebug` |
| `driver-app/` | Capacitor (reutiliza a PWA) | `npm install` e `npm run build:apk` |

O APK de debug fica em `driver-app-android/app/build/outputs/apk/debug/app-debug.apk`.

**Endereço da API na app Android** — duas formas:

1. **Na própria app (sem reinstalar):** no ecrã de login, toca no cartão
   **“Servidor da API”** e escreve o endereço (ex.: `https://a-tua-api.onrender.com`).
   Fica guardado no dispositivo e a app passa a usá-lo.
2. **No build, como valor inicial:**

```powershell
cd driver-app-android
.\gradlew.bat assembleDebug -PapiBase=https://a-tua-api.onrender.com
```

> O telemóvel tem de conseguir chegar à API. Se usares um túnel HTTPS para a API
> local, o telemóvel pode estar em qualquer rede — desde que o computador e o
> túnel estejam ativos.

Instalar o APK:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Endereço da API

O frontend usa `http://localhost:8080` por omissão. Para apontar para outra API:

1. Abre a aplicação com `?api=https://a-tua-api.exemplo.com` (fica guardado no browser); ou
2. Edita `frontend/js/config.js` (`apiBase`); ou
3. Define `window.LOGITRACK_API_BASE` antes de carregar `config.js`.

---

## 5. Arquitetura

```text
┌──────────────────────────┐        HTTPS / JSON         ┌──────────────────────────┐
│  Frontend estático       │  ───────────────────────►   │  API Spring Boot         │
│  Painel web + PWA        │  ◄───────────────────────   │  JWT · REST · WebSocket  │
│  HTML/CSS/JS             │        STOMP/SockJS         │  Regras de negócio       │
└──────────────────────────┘                             └────────────┬─────────────┘
                                                                      │ JDBC
                                                                      ▼
                                                         ┌──────────────────────────┐
                                                         │  MySQL 8                 │
                                                         │  carregamentos, guias,   │
                                                         │  GPS, alertas, users     │
                                                         └──────────────────────────┘
```

- **API REST stateless** autenticada por JWT.
- **WebSocket/STOMP** para alertas, carregamentos e mapa.
- **Frontend estático** (sem Thymeleaf) consumido por CORS.
- **Guias** guardadas em disco, com hash SHA-256 na base de dados.

---

## 6. Arranque local

### Pré-requisitos

| Ferramenta | Versão |
|---|---|
| JDK | 17 ou superior |
| MySQL | 8.x |
| Node.js | 18+ (apenas para servir o frontend) |
| Maven | não é necessário — usar o wrapper em `backend/` |

### 6.1. Base de dados

1. Abre o MySQL Workbench e liga-te à instância MySQL 8.
2. Executa [`backend/database/logitrack_mysql.sql`](backend/database/logitrack_mysql.sql).
3. Confirma que a base `logitrack` foi criada.

> O script faz `DROP TABLE` das tabelas do Logitrack. Não o executes sobre dados de
> produção. Os utilizadores de demonstração são criados pela aplicação no primeiro
> arranque, com a palavra-passe definida em `LOGITRACK_DEMO_PASSWORD`.

### 6.2. Backend

Cria um ficheiro `.env` a partir de [`.env.example`](.env.example) e define os valores.
No PowerShell:

```powershell
cd backend

$env:DB_URL      = "jdbc:mysql://localhost:3306/logitrack?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Maputo&characterEncoding=UTF-8"
$env:DB_USERNAME = "logitrack"
$env:DB_PASSWORD = "<a-tua-palavra-passe>"
$env:JWT_SECRET  = "<chave-aleatoria-com-pelo-menos-32-caracteres>"
$env:LOGITRACK_DEMO_PASSWORD = "<palavra-passe-dos-utilizadores-demo>"

.\mvnw.cmd spring-boot:run
```

**Atalho (recomendado):** guarda esses valores num `backend/.env` (já ignorado pelo
Git) e arranca com um único comando. O script valida as variáveis obrigatórias:

```powershell
cd backend
.\run-local.ps1            # compila e arranca (mvnw spring-boot:run)
.\run-local.ps1 -Jar       # usa o JAR já compilado
```

Se o Windows bloquear scripts (*"running scripts is disabled"*), usa o atalho:

```text
backend\run-local.cmd      # duplo clique ou executar no terminal
```

A API fica em **http://localhost:8080**. Confirma com:

```powershell
curl http://localhost:8080/api/v1/health
```

Para empacotar e executar o JAR:

```powershell
cd backend
.\mvnw.cmd clean package
java -jar target\logitrack-1.0.0.jar
```

### 6.3. Frontend

Numa segunda consola:

```powershell
cd frontend
npx serve -l 3000
```

> No Windows, se aparecer *"running scripts is disabled on this system"*, usa
> `npx.cmd` em vez de `npx`.

Abre **http://localhost:3000**. A porta é livre; o CORS predefinido aceita
`http://localhost:*`.

### 6.4. Testes

```powershell
cd backend
.\mvnw.cmd test
```

Os testes usam **H2 em modo MySQL** e não precisam de MySQL. O relatório fica em
`backend/target/surefire-reports/`.

---

## 7. Configuração do backend

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DB_URL` | sim | URL JDBC do MySQL |
| `DB_USERNAME` | sim | Utilizador da base |
| `DB_PASSWORD` | sim | Palavra-passe da base |
| `JWT_SECRET` | sim | Chave HMAC com **≥ 32 caracteres** |
| `JWT_EXPIRATION_MINUTES` | não | Validade do token (padrão `720`) |
| `PORT` | não | Porta HTTP da API (padrão `8080`) |
| `CORS_ALLOWED_ORIGINS` | não | Origens autorizadas, separadas por vírgulas |
| `LOGITRACK_UPLOAD_DIR` | não | Diretório das guias digitalizadas (padrão `uploads`) |
| `LOGITRACK_DEMO_SEED` | não | Cria dados/ utilizadores demo no arranque (padrão `true`) |
| `LOGITRACK_DEMO_PASSWORD` | se o seed estiver ativo | Palavra-passe dos utilizadores demo |
| `SLA_INTERVALO_MS` | não | Intervalo do agendador de SLA (padrão `60000`) |

> Em produção: define `JWT_SECRET` forte, `LOGITRACK_DEMO_SEED=false`, cria os
> utilizadores reais e restringe `CORS_ALLOWED_ORIGINS` ao domínio do frontend.

---

## 8. Estrutura do projeto

```text
logitrack/
├── .github/
│   └── workflows/
│       ├── backend-ci.yml          # build + testes do backend
│       └── deploy-pages.yml        # publicação do frontend no GitHub Pages
├── backend/                        # API Spring Boot
│   ├── Dockerfile
│   ├── database/
│   │   ├── logitrack_mysql.sql
│   │   └── migracao_percurso_marracuene_zimpeto.sql
│   ├── src/
│   │   ├── main/java/mz/co/logitrack/
│   │   │   ├── config/             # segurança, WebSocket, SLA, seed
│   │   │   ├── controller/api/     # endpoints REST
│   │   │   ├── domain/             # entidades JPA + enums
│   │   │   ├── dto/ · mapper/      # requests/responses
│   │   │   ├── exception/          # erros uniformes
│   │   │   ├── repository/         # Spring Data JPA
│   │   │   ├── security/           # JWT e UserDetails
│   │   │   └── service/            # regras de negócio, SLA, exportações
│   │   ├── main/resources/application.yml
│   │   └── test/
│   ├── mvnw · mvnw.cmd · .mvn/
│   ├── run-local.ps1               # arranca a API com um comando
│   └── pom.xml
├── docs/
│   ├── DEPLOY.md                   # guia de publicação
│   ├── IMPLEMENTACAO-UI-ANDROID-MOTORISTA.md
│   └── PERCURSO-MARRACUENE-ZIMPETO.md
├── driver-app/                     # wrapper Capacitor da PWA
├── driver-app-android/             # app Kotlin nativa
├── frontend/                       # aplicação estática
│   ├── app/                        # painel W2–W10
│   ├── css/ · img/
│   ├── js/                         # config, api, layout e scripts
│   ├── m/                          # PWA do motorista M1–M6
│   ├── index.html · login.html
│   └── package.json
├── .env.example
├── .gitignore
├── render.yaml                     # deploy do backend no Render
├── start-online.ps1                # API + túnel HTTPS + link pronto
└── README.md
```

---

## 9. Publicação (GitHub Pages + API)

O GitHub hospeda o **código** e o **frontend estático**. A API e o MySQL precisam de
um serviço com JVM (Render, Railway, Fly.io) e de uma base gerida (Aiven, Railway).

**Estado atual:**

| Passo | Estado |
|---|---|
| Código no GitHub | ✅ `AntonioSimbine/Logitrak` |
| Frontend no GitHub Pages | ✅ <https://antoniosimbine.github.io/Logitrak/> |
| CI do backend (build + testes) | ✅ a passar |
| API + MySQL publicados | ⚠️ local + túnel temporário (`.\start-online.ps1`); permanente no Render por fazer |

O passo a passo completo está em [`docs/DEPLOY.md`](docs/DEPLOY.md). Resumo:

1. **Frontend:** o workflow `deploy-pages.yml` publica `frontend/` no GitHub Pages.
2. **Backend:** cria um Web Service no Render a partir de `backend/Dockerfile`.
3. **Base de dados:** cria um MySQL gerido e define `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.
4. **Ligação:** abre o site com `?api=https://a-tua-api.onrender.com` ou define
   `window.LOGITRACK_API_BASE` em `frontend/js/config.js`.
5. **CORS:** define `CORS_ALLOWED_ORIGINS` com o domínio do frontend.

---

## 10. API REST

Autenticação: `Authorization: Bearer <token>` em todos os endpoints exceto o login.

```bash
curl -X POST https://a-tua-api.exemplo.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"<email>","senha":"<palavra-passe>"}'
```

| Método | Endpoint | Perfis | Descrição |
|---|---|---|---|
| GET | `/api/v1/health` | público | Estado da API |
| POST | `/api/v1/auth/login` | público | Login e emissão de JWT |
| GET | `/api/v1/auth/me` | todos | Perfil autenticado |
| GET | `/api/v1/dashboard` | admin/operador | Indicadores do painel |
| GET | `/api/v1/mapa` | admin/operador | Pontos para o mapa |
| GET | `/api/v1/kpis` | admin/operador | Séries e resumos de KPI |
| GET | `/api/v1/carregamentos` | todos | Lista paginada e filtrável |
| GET | `/api/v1/carregamentos/{id}` | todos¹ | Detalhe do carregamento |
| PATCH | `/api/v1/carregamentos/{id}/estado` | admin/operador | Transição de estado |
| POST | `/api/v1/carregamentos/{id}/posicoes` | motorista | Registo de posição GPS |
| POST | `/api/v1/carregamentos` | motorista/admin | Sincronização idempotente |
| GET | `/api/v1/alertas` | admin/operador | Alertas de atraso |
| GET | `/api/v1/alertas/nao-lidos` | admin/operador | Contador/lista de não lidos |
| PATCH | `/api/v1/alertas/{id}` | admin/operador | Marcar alerta como lido |
| GET/POST | `/api/v1/relatorios` | admin/operador | Relatório operacional |
| GET/POST | `/api/v1/relatorios/exportar` | admin/operador | Exportação PDF/XLSX |
| GET | `/api/v1/fabricas` | admin | CRUD de fábricas |
| GET | `/api/v1/veiculos` | admin | CRUD de veículos |
| GET | `/api/v1/utilizadores` | admin | CRUD de utilizadores |
| GET | `/api/v1/utilizadores/motoristas` | admin/operador | Lista de motoristas |
| GET | `/api/v1/parametros` | admin | Parâmetros globais |
| PUT | `/api/v1/parametros/{chave}` | admin | Atualizar parâmetro |
| POST | `/api/v1/sla/verificar` | admin | Forçar verificação de SLA |
| GET | `/api/v1/documentos/{id}/ficheiro` | todos¹ | Ficheiro da guia (requer Bearer) |
| GET | `/api/v1/mobile/dashboard` | motorista | Resumo móvel |
| GET | `/api/v1/mobile/referencias` | motorista | Veículos e fábricas |
| GET | `/api/v1/mobile/carregamentos` | motorista | Carregamentos do motorista |
| GET | `/api/v1/mobile/carregamentos/{id}` | motorista | Detalhe móvel |

¹ O motorista só pode consultar os seus próprios carregamentos e guias.

### WebSocket (STOMP/SockJS)

| Tópico | Evento |
|---|---|
| `/topic/alertas-atraso` | Novo alerta de atraso |
| `/topic/carregamentos` | Carregamento criado/atualizado |
| `/topic/mapa` | Atualização de posições no mapa |

Ligação: `https://a-tua-api.exemplo.com/ws` (SockJS), com o JWT no cabeçalho
`Authorization` do `CONNECT`.

---

## 11. Regras de negócio

| Regra | Implementação |
|---|---|
| Login unificado | `POST /api/v1/auth/login`; falha devolve **401** genérico |
| RBAC | `ADMINISTRADOR`, `OPERADOR_CENTRAL`, `MOTORISTA` (Spring Security + `@PreAuthorize`) |
| Precisão GPS | Máximo **15 m** (parametrizável); mensagem exata de erro |
| Geofence | Haversine; exige confirmação fora do raio |
| Idempotência | `uuidLocal` único; duplicado devolve **200** com `X-Idempotente: true` |
| Transições | `REGISTADO → EM_TRANSITO → ATRASADO/CONCLUIDO`, com estados finais imutáveis |
| SLA | Agendador periódico, tolerância configurável e alerta com minutos excedidos |
| Guia digitalizada | Ficheiro em disco, SHA-256 na base, formato e dimensões validados |
| Auditoria | Registo de utilizador/documento/data em cada leitura de guia |

**Parâmetros globais (W10):**

| Chave | Valor inicial |
|---|---|
| `PRECISAO_MAXIMA_GPS_METROS` | `15.0` |
| `TOLERANCIA_ATRASO_MINUTOS` | `30` |
| `FREQUENCIA_RECOLHA_GPS_SEGUNDOS` | `60` |
| `INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS` | `5` |
| `INTERVALO_VERIFICACAO_SLA_MINUTOS` | `1` |

---

## 12. Segurança

- Nenhuma credencial, palavra-passe ou segredo é versionado neste repositório.
- Palavras-passe apenas em **BCrypt**; nunca são devolvidas pela API.
- JWT assinado com HMAC-SHA e expiração configurável.
- API **stateless**: sem sessão, sem CSRF e com filtro JWT.
- CORS explícito; `allowCredentials=false`.
- JWT guardado no `localStorage` e removido no logout ou em **401**.
- Autorização por perfil e verificação de propriedade nos recursos do motorista.
- Validação de entrada e mensagens de erro sem detalhes internos.
- Guias fora da base, com hash SHA-256 e leitura protegida por Bearer.

---

## 13. Documentação adicional

- [`docs/DEPLOY.md`](docs/DEPLOY.md) — publicação no GitHub Pages, Render e MySQL.
- [`docs/IMPLEMENTACAO-UI-ANDROID-MOTORISTA.md`](docs/IMPLEMENTACAO-UI-ANDROID-MOTORISTA.md) — decisões da UI Android.
- [`docs/PERCURSO-MARRACUENE-ZIMPETO.md`](docs/PERCURSO-MARRACUENE-ZIMPETO.md) — percurso operacional e geofence.

---

## 14. Licença e contacto

Projeto demonstrativo desenvolvido para operações industriais em Moçambique.
Para acesso, suporte ou criação de utilizadores, contacta o administrador do sistema.
