# Publicar o Logitrack

Este guia cobre a publicação completa:

| Componente | Serviço recomendado | Gratuito? |
|---|---|---|
| Código e frontend | GitHub + GitHub Pages | Sim (repositório público) |
| API Spring Boot | Render (Docker) | Sim, com hibernação |
| MySQL 8 | Aiven, Railway ou Clever Cloud | Sim, com limites |

> O GitHub **não executa** o backend Java nem o MySQL. O GitHub Pages serve apenas
> ficheiros estáticos (o frontend).

---

## 1. Criar o repositório no GitHub

1. Entra em <https://github.com/new>.
2. Nome: `logitrack`.
3. Escolhe **Public** para usar GitHub Pages gratuitamente.
4. **Não** marques “Add a README”, “.gitignore” nem licença.
5. Cria o repositório.

No computador:

```powershell
cd C:\Users\user\IdeaProjects\logitrack

git init
git add .
git status          # confirma que target/, build/, node_modules/, .env não entram
git commit -m "Initial commit: Logitrack"
git branch -M main
git remote add origin https://github.com/SEU-USUARIO/logitrack.git
git push -u origin main
```

---

## 2. Publicar o frontend no GitHub Pages

1. No repositório: **Settings → Pages**.
2. Em **Build and deployment → Source**, escolhe **GitHub Actions**.
3. Faz push para `main`. O workflow `deploy-pages.yml` publica `frontend/`.
4. O endereço fica `https://SEU-USUARIO.github.io/logitrack/`.

O frontend foi preparado para funcionar em subpastas (GitHub Pages project site).

### Apontar o frontend para a API

Depois de a API estar pública, abre o site uma vez com o parâmetro `api`:

```text
https://SEU-USUARIO.github.io/logitrack/?api=https://logitrack-api.onrender.com
```

O valor fica guardado no `localStorage` do browser. Também podes definir
`window.LOGITRACK_API_BASE` em `frontend/js/config.js`.

### Alternativa: Cloudflare Pages

Se o repositório for privado e a conta GitHub for gratuita, o GitHub Pages pode não
estar disponível. Nesse caso:

1. Em <https://pages.cloudflare.com>, liga o GitHub.
2. Framework preset: **None**.
3. Build command: *(vazio)*.
4. Output directory: `frontend`.
5. Define a variável `LOGITRACK_API_BASE` no build, se quiseres fixar a API.

O site fica na raiz do domínio, pelo que também funciona sem ajustes de caminho.

---

## 3. Criar o MySQL gerido

Exemplo com Aiven:

1. Cria um serviço **MySQL 8**.
2. Copia o host, porta, utilizador, palavra-passe e nome da base.
3. Constrói a `DB_URL`:

```text
jdbc:mysql://HOST:PORTA/logitrack?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=Africa/Maputo&characterEncoding=UTF-8
```

4. Executa `backend/database/logitrack_mysql.sql` nessa base (MySQL Workbench ou CLI).

> O script cria o schema e os dados de referência. Os utilizadores demo são criados
> pela aplicação no primeiro arranque, com a palavra-passe de `LOGITRACK_DEMO_PASSWORD`.

---

## 4. Publicar o backend no Render

1. Cria conta em <https://render.com> e liga o GitHub.
2. **New → Blueprint** e escolhe o repositório (ou **New → Web Service**).
3. Configuração:
   - **Root Directory:** `backend`
   - **Runtime:** Docker
   - **Dockerfile Path:** `./Dockerfile`
   - **Health Check Path:** `/api/v1/health`
4. Variáveis de ambiente:

| Variável | Valor |
|---|---|
| `DB_URL` | a URL JDBC do MySQL |
| `DB_USERNAME` | utilizador da base |
| `DB_PASSWORD` | palavra-passe da base |
| `JWT_SECRET` | chave aleatória com ≥ 32 caracteres |
| `CORS_ALLOWED_ORIGINS` | `https://SEU-USUARIO.github.io` |
| `LOGITRACK_DEMO_SEED` | `false` em produção |
| `LOGITRACK_UPLOAD_DIR` | `/tmp/logitrack-uploads` |

5. Faz deploy. Testa:

```powershell
curl https://logitrack-api.onrender.com/api/v1/health
```

> No plano gratuito do Render o serviço hiberna após inatividade e o disco é efémero.
> As guias em `uploads/` perdem-se num redeploy. Para produção, usa disco persistente
> (plano pago) ou armazenamento S3.

### Alternativa: Railway

1. **New Project → Deploy from GitHub repo**.
2. Root directory: `backend`.
3. O Railway deteta o `Dockerfile`.
4. Adiciona um plugin **MySQL** e usa as variáveis `MYSQLHOST`, `MYSQLPORT`, etc. para
   construir `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.

---

## 5. Configurar o CORS

A API tem de autorizar o domínio do frontend. Exemplo:

```text
CORS_ALLOWED_ORIGINS=https://SEU-USUARIO.github.io
```

Se usares um domínio próprio:

```text
CORS_ALLOWED_ORIGINS=https://app.logitrack.co.mz
```

Não uses `*` em produção.

---

## 6. Domínio próprio (opcional)

### GitHub Pages

1. **Settings → Pages → Custom domain**.
2. Cria um registo `CNAME` no teu DNS a apontar para `SEU-USUARIO.github.io`.
3. Ativa **Enforce HTTPS**.

O site passa a ser servido na raiz do domínio.

### API

No Render: **Settings → Custom Domain** e cria o `CNAME` indicado.

---

## 7. Verificação final

- [ ] `https://SEU-USUARIO.github.io/logitrack/` abre com estilos e o login.
- [ ] `https://logitrack-api.onrender.com/api/v1/health` responde `UP`.
- [ ] O login devolve um token JWT.
- [ ] `CORS_ALLOWED_ORIGINS` inclui o domínio do frontend.
- [ ] `JWT_SECRET` tem pelo menos 32 caracteres e não está no repositório.
- [ ] `LOGITRACK_DEMO_SEED=false` em produção.
- [ ] As guias enviadas ficam acessíveis após reinício (disco persistente/S3).
- [ ] O MySQL tem backups configurados.

---

## 8. Problemas frequentes

| Sintoma | Causa / solução |
|---|---|
| `Failed to fetch` no login | API indisponível ou URL errada; testa `/api/v1/health` |
| Erro de CORS | Adiciona o domínio do frontend a `CORS_ALLOWED_ORIGINS` |
| Página sem estilos | O site tem de ser servido a partir da raiz publicada; confirma o workflow |
| Câmara não abre no telemóvel | `getUserMedia` exige HTTPS ou `localhost` |
| PWA não instala | Service Worker só regista em HTTPS/localhost |
| `JWT_SECRET` inválido | Define uma chave com pelo menos 32 caracteres |
| Seed falha no arranque | Define `LOGITRACK_DEMO_PASSWORD` ou `LOGITRACK_DEMO_SEED=false` |
| Guias desaparecem após deploy | O disco do Render é efémero; usa disco persistente/S3 |
