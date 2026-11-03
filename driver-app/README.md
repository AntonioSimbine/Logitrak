# Logitrack - App nativa do Motorista

Este projeto cria uma **app Android nativa** a partir da app móvel web existente (`frontend/m/`), mantendo exatamente o mesmo design, funcionalidades offline-first e sincronização.

## Pré-requisitos

- [Node.js 18+](https://nodejs.org/)
- [Android Studio](https://developer.android.com/studio) (com Android SDK)
- [Java JDK 17](https://adoptium.net/)
- A API backend a correr (ou acessível pela rede)

## Como gerar a app

Abra um terminal na pasta `driver-app/`:

```bash
# 1) Instalar dependências
npm install

# 2) Copiar a app web para www/ e sincronizar com Android
npm run android
```

O Android Studio irá abrir. A partir daí pode:
- Executar num emulador
- Gerar APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`
- Ou gerar diretamente por linha de comandos

## Gerar APK direto (sem abrir Android Studio)

```bash
npm run build:apk
```

O APK ficará em: `driver-app/android/app/build/outputs/apk/debug/app-debug.apk`

## Configurar URL da API

Por padrão, a app usa `http://localhost:8080`. 

Para usar com o servidor real:
1. Abra a app
2. Na tela de login, adicione `?api=http://IP_DO_SERVIDOR:8080` à URL ou configure via `localStorage` (`logitrack.apiBase`)
3. Ou edite `www/js/config.js` antes de gerar

## Diferenças vs PWA web

- Roda como app nativa (sem barra do browser)
- Usa Capacitor para câmera, GPS, rede
- Botão "voltar" do Android tratado corretamente
- Pode ser instalada como APK
- Mantém todo o código offline-first original

## Estrutura

A pasta `www/` é gerada automaticamente a partir de `frontend/m/`. **Não edite `www/` diretamente** - edite `frontend/m/` e rode `npm run build:www`.
