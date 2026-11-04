# Logitrack Motorista - App Android Nativa (Kotlin)

App nativa em Kotlin para o motorista. Mesma funcionalidade do sistema, rodando fora do navegador.

## Funcionalidades
- Login apenas para MOTORISTA
- Painel com contadores
- Novo carregamento com GPS, veículo, tipo de carga, foto da guia (CameraX)
- Fila offline com Room (SQLite)
- Sincronização automática com WorkManager quando houver rede
- Sincronização manual na tela Pendentes
- Interface em dark mode igual ao estilo da app web

## Como abrir no Android Studio

1. Abra o Android Studio
2. `File > Open` e selecione a pasta `driver-app-android/`
3. Aguarde o Gradle Sync
4. Rode no emulador ou dispositivo físico

## Gerar APK

No Android Studio: `Build > Build Bundle(s) / APK(s) > Build APK(s)`

Ou por linha de comandos:
```bash
cd driver-app-android
./gradlew assembleDebug
```
APK em: `app/build/outputs/apk/debug/app-debug.apk`

Para já deixar o endereço da API definido no build:
```bash
./gradlew assembleDebug -PapiBase=https://a-tua-api.exemplo.com
```

## Configurar API

A app lê o endereço inicial de `BuildConfig.API_BASE_PADRAO` (definido em
`app/build.gradle`, por omissão `http://192.168.137.1:8080`, usado no hotspot do
Windows). O endereço pode ser alterado **sem reinstalar**:

1. Abre o ecrã de **login**.
2. Toca no cartão **“Servidor da API”**.
3. Escreve o endereço (`https://…` ou `http://…`) e guarda.

O valor fica na preferência `api_base` e o cliente Retrofit é reconstruído de
imediato. Para um servidor público, usa sempre `https://` — o Android bloqueia
tráfego em claro por omissão.

> Se usares um túnel HTTPS (Cloudflare) para a API local, o telemóvel pode estar
> em qualquer rede, desde que o computador e o túnel estejam ativos. O endereço
> do túnel muda em cada arranque — atualiza-o na app.
