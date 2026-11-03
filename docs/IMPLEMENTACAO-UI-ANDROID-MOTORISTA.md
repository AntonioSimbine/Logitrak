# Logitrack Motorista — atualização da interface Android

## Referência visual
A interface foi atualizada com base no conceito visual aprovado: azul-marinho, branco, cinza-claro e laranja, com botões de maior dimensão, textos diretos e ações principais mais evidentes.

## Alterações realizadas
- Página inicial Android reconstruída com marca Logitrack, saudação do motorista, estado de ligação/sincronização, ação principal de novo carregamento e acesso destacado à fila de sincronização.
- Aumentadas as áreas de toque dos controlos principais e o tamanho dos textos de orientação.
- Mantidos os IDs e os pontos de integração utilizados pelo `HomeFragment.kt` para preservar navegação, dashboard, fila offline, sincronização, sessão e carregamentos recentes.
- Simplificada a linguagem do cartão de pontos de operação no formulário de novo carregamento, removendo referências textuais a “rota de demonstração”.
- Mantidos os componentes de captura de fotografia, localização, validação de geofence, confirmação e gravação já existentes no fluxo.

## Ficheiros principais alterados
- `driver-app-android/app/src/main/res/layout/fragment_home.xml`
- `driver-app-android/app/src/main/res/layout/fragment_novo.xml`

## Validação
- Os 123 ficheiros XML do módulo Android foram analisados sintaticamente sem erros.
- Foram verificados os IDs usados pelo `HomeFragment.kt`; todos continuam presentes no novo layout.
- Os drawables referenciados nos dois layouts alterados existem no projeto.
- A compilação APK não foi concluída neste ambiente: o Gradle Wrapper tentou obter Gradle 9.5.1 de `services.gradle.org`, mas o ambiente não tinha acesso à Internet (`UnknownHostException`). Portanto, esta entrega contém código-fonte atualizado, não um APK validado.

## Como compilar no Windows
Na pasta `logitrack\driver-app-android`, executar:

```bat
gradlew.bat assembleDebug
```

É necessário acesso à Internet na primeira compilação para descarregar Gradle e dependências, ou ter essas versões já em cache local. O APK de debug será normalmente gerado em `app\build\outputs\apk\debug\app-debug.apk`.

## Nota de preservação
Não foram alterados os serviços Java, API, base de dados, autenticação, Room, WorkManager, GPS ou lógica de sincronização. A lógica de negócio existente foi mantida; esta entrega concentra-se nos layouts Android.
