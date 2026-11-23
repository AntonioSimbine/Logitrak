# Publicar o Logitrack

> **Estado atual:** todo o sistema está publicado no **Railway** — API, MySQL,
> site, PWA do motorista e APK. O GitHub Pages mantém um espelho do frontend.

| Componente | Onde está | Endereço |
|---|---|---|
| Sistema web | Railway (`web`) | <https://web-production-f93e366a.up.railway.app/> |
| App do motorista (PWA) | Railway (`web`) | <https://web-production-f93e366a.up.railway.app/m/> |
| APK Android | Railway (`web`) | <https://web-production-f93e366a.up.railway.app/downloads/Logitrack-Motorista.apk> |
| API Spring Boot | Railway (`api`) | <https://api-production-938e6a7a.up.railway.app/api/v1/health> |
| MySQL 8 | Railway (`MySQL`) | rede privada do projeto |
| Frontend (espelho) | GitHub Pages | <https://antoniosimbine.github.io/Logitrak/> |
| Código | GitHub | <https://github.com/AntonioSimbine/Logitrak> |

O repositório não contém segredos: tudo o que é sensível vem de variáveis de
ambiente do Railway.

---

## 1. Repositório no GitHub

1. Cria o repositório em <https://github.com/new> (público, sem README inicial).
2. No computador:

```powershell
cd C:\Users\user\IdeaProjects\logitrack
git init
git add .
git status          # target/, build/, node_modules/ e .env não devem entrar
git commit -m "Initial commit: Logitrack"
git branch -M main
git remote add origin https://github.com/AntonioSimbine/Logitrak.git
git push -u origin main
```

---

## 2. Publicar no Railway

### 2.1 Criar o projeto e a base de dados

```powershell
railway login
railway init --name logitrack        # ou usa um projeto existente
railway add --database mysql
```

O plugin MySQL expõe, entre outras, as variáveis `MYSQLHOST`, `MYSQLPORT`,
`MYSQLUSER`, `MYSQLPASSWORD` e `MYSQLDATABASE`. Não é preciso copiar valores: a
API referencia-as diretamente.

### 2.2 Serviço `api`

1. Cria o serviço `api` (`railway add -s api`).
2. Em **Settings → Source**, define **Root Directory** = `backend`.
3. O Railway deteta o `backend/Dockerfile`; não é preciso comando de build.
4. **Health check path:** `/api/v1/health`.
5. **Volume:** cria um volume montado em `/data` (guarda as fotografias das guias).
6. Variáveis de ambiente:

| Variável | Valor |
|---|---|
| `DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/logitrack?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Maputo&characterEncoding=UTF-8` |
| `DB_USERNAME` | `${{MySQL.MYSQLUSER}}` |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `JWT_SECRET` | chave aleatória com ≥ 32 caracteres |
| `JWT_EXPIRATION_MINUTES` | `720` |
| `CORS_ALLOWED_ORIGINS` | `https://web-production-f93e366a.up.railway.app,https://antoniosimbine.github.io` |
| `LOGITRACK_DEMO_SEED` | `true` (demonstração) ou `false` (produção) |
| `LOGITRACK_DEMO_PASSWORD` | palavra-passe das contas demo (não versionar) |
| `LOGITRACK_UPLOAD_DIR` | `/data/uploads` |

7. Gera o domínio público: **Settings → Networking → Generate Domain**.

A base `logitrack` é criada automaticamente no primeiro arranque
(`createDatabaseIfNotExist=true`) e o esquema é gerido pelo Hibernate
(`ddl-auto: update`). O `DataSeeder` cria fábricas, veículos, utilizadores e
carregamentos de demonstração.

### 2.3 Serviço `web`

1. Cria o serviço `web` (`railway add -s web`).
2. **Root Directory** = `frontend`.
3. Build pelo `frontend/Dockerfile` (Nginx).
4. **Health check path:** `/healthz`.
5. Gera o domínio público.

O `Dockerfile` usa a cópia local em `frontend/downloads/Logitrack-Motorista.apk`.
Se essa cópia não existir, tenta descarregar a release estável do GitHub
(`/releases/latest/download/Logitrack-Motorista.apk`). Assim, o ficheiro passa
sempre a ser servido pelo próprio Railway, no link oficial.

### 2.4 Deploy a partir do computador

Enquanto o serviço não estiver ligado ao GitHub, publica pela CLI:

```powershell
railway link -p <PROJECT_ID> -e production
railway up -s api --ci      # raiz do repositório; root directory = backend
railway up -s web --ci      # root directory = frontend
```

> A CLI envia o repositório; o campo **Root Directory** de cada serviço decide o
> que é construído. Sem esse campo, o Railpack tenta construir a raiz e falha.

### 2.5 Ligar o repositório ao Railway (opcional, para deploys automáticos)

Em **Settings → Source → Connect Repo**, escolhe `AntonioSimbine/Logitrak` e o
branch `main`. A partir daí, cada `git push` publica os dois serviços.

### 2.6 Contas de demonstração

Criadas no arranque com a palavra-passe de `LOGITRACK_DEMO_PASSWORD`:

| Perfil | E-mail |
|---|---|
| Administrador | `admin@logitrack.co.mz` |
| Operador da central | `operador@logitrack.co.mz` |
| Motorista | `motorista@logitrack.co.mz` |

---

## 3. Frontend no GitHub Pages (espelho)

1. **Settings → Pages → Source:** GitHub Actions.
2. O workflow `deploy-pages.yml` publica `frontend/` a cada push em `main`.
3. Endereço: <https://antoniosimbine.github.io/Logitrak/>.

O frontend usa a API do Railway por omissão fora de `localhost`. Para forçar
outra API, abre o site uma vez com `?api=https://a-tua-api.exemplo.com` (fica no
`localStorage`) ou define `window.LOGITRACK_API_BASE`.

---

## 4. App Android

O APK é gerado a partir de `driver-app-android/`:

```powershell
cd driver-app-android
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug -PapiBase=https://api-production-938e6a7a.up.railway.app
```

O endereço da API pode ser alterado sem reinstalar: ecrã de login → cartão
**Servidor da API**.

Para publicar uma nova versão:

1. Aumenta `versionCode` e `versionName` em `app/build.gradle`.
2. Compila e copia o APK para `frontend/downloads/Logitrack-Motorista.apk`.
3. Atualiza `frontend/downloads/versao.json` com o novo `versionCode`,
   `versionName`, mensagem, `sha256` do APK e `obrigatoria` (true/false).
4. Aumenta `APK_CACHE_BUST` no `frontend/Dockerfile`.
5. Publica o serviço `web` com `railway up -s web --ci`.

A aplicação lê `versao.json` ao abrir. Se houver versão nova, mostra o aviso e
descarrega o APK dentro da app; o instalador do Android pede a confirmação final.
Também há **Perfil → Verificar atualização** para verificar a qualquer momento.
O `versionName` e o código da versão aparecem no Perfil.

Links oficiais:

```text
https://web-production-f93e366a.up.railway.app/downloads/versao.json
https://web-production-f93e366a.up.railway.app/downloads/Logitrack-Motorista.apk
```

---

## 5. CORS

A API tem de autorizar o domínio do site:

```text
CORS_ALLOWED_ORIGINS=https://web-production-f93e366a.up.railway.app,https://antoniosimbine.github.io
```

Com domínio próprio:

```text
CORS_ALLOWED_ORIGINS=https://app.logitrack.co.mz
```

Não uses `*` em produção.

---

## 6. Domínio próprio (opcional)

1. **Railway → serviço → Settings → Networking → Custom Domain.**
2. Cria o registo `CNAME` indicado no teu DNS.
3. Atualiza `CORS_ALLOWED_ORIGINS` com o novo domínio.

---

## 7. Verificação final

- [ ] `https://api-production-938e6a7a.up.railway.app/api/v1/health` responde `UP`.
- [ ] O login devolve um token JWT.
- [ ] `https://web-production-f93e366a.up.railway.app/` abre com estilos.
- [ ] `.../m/` instala como PWA (HTTPS).
- [ ] `.../downloads/Logitrack-Motorista.apk` descarrega o APK.
- [ ] `.../downloads/versao.json` devolve o `versionCode` da última versão.
- [ ] A app antiga avisa e instala a nova versão sem abrir o browser.
- [ ] `CORS_ALLOWED_ORIGINS` inclui o domínio do site.
- [ ] `JWT_SECRET` tem ≥ 32 caracteres e não está no repositório.
- [ ] `LOGITRACK_DEMO_SEED=false` numa instalação real.
- [ ] As fotografias sobrevivem a um redeploy (volume em `/data`).
- [ ] O MySQL tem backups/configuração de retenção adequada.

---

## 8. Problemas frequentes

| Sintoma | Causa / solução |
|---|---|
| `Failed to fetch` no login | API indisponível ou URL errada; testa `/api/v1/health` |
| Erro de CORS | Adiciona o domínio do site a `CORS_ALLOWED_ORIGINS` |
| Build falha com `Railpack could not determine how to build` | Falta o **Root Directory** (`backend`/`frontend`) no serviço |
| Build falha com `default.conf.template: not found` | O `.dockerignore` do frontend não pode excluir o template do Nginx |
| Health check falha no arranque | A API pode demorar ~1 min na primeira subida; confirma os logs |
| Câmara não abre no telemóvel | `getUserMedia` exige HTTPS |
| PWA não instala | Service Worker só regista em HTTPS |
| `Seed falha` | Define `LOGITRACK_DEMO_PASSWORD` ou `LOGITRACK_DEMO_SEED=false` |
| Fotografias desaparecem | Confirma o volume montado em `/data` e `LOGITRACK_UPLOAD_DIR=/data/uploads` |

---

## 9. Alternativas

O repositório inclui `render.yaml` e o mesmo `backend/Dockerfile` funciona no
**Render** (root directory `backend`, runtime Docker). Para a base de dados,
qualquer MySQL 8 gerido serve (Aiven, Clever Cloud, Railway). O procedimento é o
mesmo; só mudam os nomes das variáveis de ligação.
