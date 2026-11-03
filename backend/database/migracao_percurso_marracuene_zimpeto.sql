-- Atualiza os nomes operacionais e o geofence da unidade UJC nas bases existentes.
-- Executar uma única vez na base de dados Logitrack.
START TRANSACTION;

UPDATE fabricas SET nome = 'Fábrica de Guava',
    endereco = 'Guava, Marracuene, Província de Maputo'
WHERE nome LIKE '%Guava%' OR nome LIKE '%Rota Demo%Guava%';

UPDATE fabricas SET nome = 'Fábrica de Mateque',
    endereco = 'Mateque, Marracuene, Província de Maputo'
WHERE nome LIKE '%Mateque%';

UPDATE fabricas SET nome = 'Centro Logístico CMC - Zimpeto',
    endereco = 'CMC, Zimpeto, Maputo'
WHERE nome LIKE '%CMC%';

UPDATE fabricas SET nome = 'Unidade de Carga UJC',
    endereco = 'Campus do Zimpeto, Rua do Grande Maputo, Q.88, Maputo',
    latitude = -25.820660, longitude = 32.579230, raio_geofence_metros = 100
WHERE nome LIKE '%Universidade Joaquim Chissano%' OR nome LIKE '%UJC%';

COMMIT;
