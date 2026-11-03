# Unidades operacionais — Marracuene a Zimpeto

A aplicação apresenta os pontos como unidades operacionais, sem rótulos de demonstração:

1. Fábrica de Guava — Marracuene
2. Fábrica de Mateque — Marracuene
3. Centro Logístico CMC — Zimpeto
4. Unidade de Carga UJC — Campus do Zimpeto

As coordenadas de Guava, Mateque e CMC são referências iniciais e devem ser confirmadas no local antes de operação real. A opção **Abrir rota no mapa** abre o itinerário no Google Maps.

## Geofence da UJC

O ponto central configurado é `-25.820660, 32.579230`, com raio de **100 metros**. A aplicação compara a posição GPS real do dispositivo com esse centro. Estar dentro do campus não garante automaticamente que a posição esteja dentro do raio configurado; a validação depende da distância GPS ao ponto central. O raio e as coordenadas podem ser ajustados no painel de administração.

## Remoção de unidades

O botão **Eliminar** no painel do administrador realiza uma desativação lógica. A unidade deixa de ser enviada às referências da aplicação móvel e deixa de participar na deteção de geofence. O histórico de carregamentos é preservado. O administrador pode voltar a disponibilizá-la através do botão **Reativar**. A aplicação móvel atualiza a lista ao abrir o formulário de novo carregamento quando existe ligação ao servidor.
