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

Ou por linha de comandos (se tiver Gradle/Android SDK configurados):
```bash
cd driver-app-android
./gradlew assembleDebug
```
APK em: `app/build/outputs/apk/debug/app-debug.apk`

## Configurar API

Na configuração atual de testes pelo hotspot do Windows, a app aponta para `http://192.168.137.1:8080`. 
Para dispositivo físico na mesma rede, altere em `Prefs.kt` ou adicione configuração.

O backend precisa estar acessível. Para esta configuração, o computador Windows partilha o hotspot e o telemóvel acede ao servidor em `http://192.168.137.1:8080`. Se mudar a rede, atualize o endereço em `Prefs.kt` ou na preferência `api_base`.
