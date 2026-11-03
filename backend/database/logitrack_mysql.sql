-- =====================================================================
--  LOGITRACK — Sistema de Gestão e Rastreabilidade de Carregamentos
--  Script de criação da base de dados para MySQL 8 (MySQL Workbench)
-- =====================================================================
--  Como importar no MySQL Workbench:
--    1. Abrir o Workbench e ligar-se à instância MySQL 8.
--    2. Server > Data Import  →  "Import from Self-Contained File"
--       e escolher este ficheiro; OU abrir uma query tab (Ctrl+T),
--       abrir este ficheiro (File > Open SQL Script) e executar (⚡).
--    3. O script cria a base `logitrack`, as 8 tabelas, os índices,
--       as chaves estrangeiras e os dados de referência.
--
--  ATENÇÃO: o script APAGA as tabelas do Logitrack caso já existam
--  (DROP TABLE). Não executar sobre uma base com dados de produção.
--
--  Os carregamentos de demonstração são criados automaticamente pela
--  aplicação no primeiro arranque (DataSeeder), reutilizando as fábricas,
--  veículos e utilizadores abaixo. Para arrancar sem dados de demo:
--      LOGITRACK_DEMO_SEED=false
--
--  Os utilizadores de demonstração são criados pela aplicação com a
--  palavra-passe definida em LOGITRACK_DEMO_PASSWORD. Nenhuma
--  credencial deve ser guardada neste ficheiro.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `logitrack`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `logitrack`;

SET NAMES utf8mb4;
SET time_zone = '+02:00';            -- Africa/Maputo
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `alertas_atraso`;
DROP TABLE IF EXISTS `localizacoes_gps`;
DROP TABLE IF EXISTS `documentos_guia`;
DROP TABLE IF EXISTS `carregamentos`;
DROP TABLE IF EXISTS `veiculos`;
DROP TABLE IF EXISTS `users`;
DROP TABLE IF EXISTS `fabricas`;
DROP TABLE IF EXISTS `parametros_globais`;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 1. Fábricas / unidades de carregamento (geofence + SLA por origem)
-- ---------------------------------------------------------------------
CREATE TABLE `fabricas` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `nome`                  VARCHAR(160) NOT NULL,
    `endereco`              VARCHAR(255) NOT NULL,
    `latitude`              DOUBLE       NOT NULL,
    `longitude`             DOUBLE       NOT NULL,
    `raio_geofence_metros`  INT          NOT NULL DEFAULT 300,
    `sla_limite_horas`      INT          NOT NULL DEFAULT 24,
    `horario_acostagem`     VARCHAR(50)  NOT NULL DEFAULT '06:00 - 22:00',
    `ativa`                 BIT(1)       NOT NULL DEFAULT b'1',
    PRIMARY KEY (`id`),
    KEY `idx_fabricas_ativa` (`ativa`),
    KEY `idx_fabricas_nome` (`nome`),
    CONSTRAINT `chk_fabricas_latitude`  CHECK (`latitude`  BETWEEN -90  AND 90),
    CONSTRAINT `chk_fabricas_longitude` CHECK (`longitude` BETWEEN -180 AND 180),
    CONSTRAINT `chk_fabricas_raio`      CHECK (`raio_geofence_metros` BETWEEN 10 AND 50000),
    CONSTRAINT `chk_fabricas_sla`       CHECK (`sla_limite_horas` BETWEEN 1 AND 720)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 2. Utilizadores (login unificado + RBAC)
--    `senha_hash` guarda sempre BCrypt (60 caracteres).
-- ---------------------------------------------------------------------
CREATE TABLE `users` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `nome`          VARCHAR(120) NOT NULL,
    `email`         VARCHAR(160) NOT NULL,
    `senha_hash`    VARCHAR(100) NOT NULL,
    `ativo`         BIT(1)       NOT NULL DEFAULT b'1',
    `perfil`        VARCHAR(30)  NOT NULL,
    `data_criacao`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    KEY `idx_users_perfil` (`perfil`),
    KEY `idx_users_ativo` (`ativo`),
    CONSTRAINT `chk_users_perfil`
        CHECK (`perfil` IN ('ADMINISTRADOR', 'OPERADOR_CENTRAL', 'MOTORISTA'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 3. Frota
-- ---------------------------------------------------------------------
CREATE TABLE `veiculos` (
    `id`                        BIGINT       NOT NULL AUTO_INCREMENT,
    `matricula`                 VARCHAR(20)  NOT NULL,
    `modelo`                    VARCHAR(120) NOT NULL,
    `capacidade_carga_toneladas` DOUBLE      NOT NULL,
    `estado`                    VARCHAR(20)  NOT NULL DEFAULT 'DISPONIVEL',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_veiculos_matricula` (`matricula`),
    KEY `idx_veiculos_estado` (`estado`),
    CONSTRAINT `chk_veiculos_estado`
        CHECK (`estado` IN ('DISPONIVEL', 'EM_TRANSITO', 'MANUTENCAO')),
    CONSTRAINT `chk_veiculos_capacidade` CHECK (`capacidade_carga_toneladas` > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 4. Carregamentos
--    `uuid_local` é a chave de idempotência gerada no dispositivo
--    (offline-first) e garante que cada registo é criado uma única vez.
-- ---------------------------------------------------------------------
CREATE TABLE `carregamentos` (
    `id`                   BIGINT      NOT NULL AUTO_INCREMENT,
    `uuid_local`           CHAR(36)    NOT NULL,
    `guia_numero`          VARCHAR(60) NOT NULL,
    `tipo_carga`           VARCHAR(80) NOT NULL,
    `quantidade_toneladas` DOUBLE      NOT NULL,
    `estado`               VARCHAR(30) NOT NULL DEFAULT 'REGISTADO',
    `data_hora_inicio`     DATETIME(6) NULL,
    `data_hora_fim`        DATETIME(6) NULL,
    `motivo_rejeicao`      VARCHAR(500) NULL,
    `fabrica_id`           BIGINT      NULL,
    `veiculo_id`           BIGINT      NULL,
    `motorista_id`         BIGINT      NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_carregamentos_uuid_local` (`uuid_local`),
    KEY `idx_carregamentos_estado` (`estado`),
    KEY `idx_carregamentos_data_inicio` (`data_hora_inicio`),
    KEY `idx_carregamentos_fabrica` (`fabrica_id`),
    KEY `idx_carregamentos_veiculo` (`veiculo_id`),
    KEY `idx_carregamentos_motorista` (`motorista_id`),
    KEY `idx_carregamentos_guia` (`guia_numero`),
    KEY `idx_carregamentos_estado_inicio` (`estado`, `data_hora_inicio`),
    CONSTRAINT `fk_carregamentos_fabrica`
        FOREIGN KEY (`fabrica_id`) REFERENCES `fabricas` (`id`),
    CONSTRAINT `fk_carregamentos_veiculo`
        FOREIGN KEY (`veiculo_id`) REFERENCES `veiculos` (`id`),
    CONSTRAINT `fk_carregamentos_motorista`
        FOREIGN KEY (`motorista_id`) REFERENCES `users` (`id`),
    CONSTRAINT `chk_carregamentos_estado`
        CHECK (`estado` IN ('GUARDADO_LOCAL', 'PENDENTE_SINCRONIZACAO', 'REGISTADO',
                            'EM_TRANSITO', 'ATRASADO', 'CONCLUIDO', 'REJEITADO')),
    CONSTRAINT `chk_carregamentos_quantidade` CHECK (`quantidade_toneladas` > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 5. Fotografia da Guia de Transporte (1:1 com o carregamento)
--    O ficheiro binário reside em disco; a base guarda o caminho,
--    o hash SHA-256 e os metadados de captura.
-- ---------------------------------------------------------------------
CREATE TABLE `documentos_guia` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `caminho_fotografia`  VARCHAR(500) NOT NULL,
    `data_hora_captura`   DATETIME(6)  NOT NULL,
    `tamanho_bytes`       BIGINT       NOT NULL,
    `hash_sha256`         VARCHAR(64)  NULL,
    `mime_type`           VARCHAR(80)  NULL,
    `carregamento_id`     BIGINT       NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_documentos_carregamento` (`carregamento_id`),
    KEY `idx_documentos_captura` (`data_hora_captura`),
    CONSTRAINT `fk_documentos_carregamento`
        FOREIGN KEY (`carregamento_id`) REFERENCES `carregamentos` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `chk_documentos_tamanho` CHECK (`tamanho_bytes` > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 6. Pontos GPS (rastreabilidade e mapa W6)
-- ---------------------------------------------------------------------
CREATE TABLE `localizacoes_gps` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `latitude`         DOUBLE      NOT NULL,
    `longitude`        DOUBLE      NOT NULL,
    `precisao_metros`  DOUBLE      NOT NULL,
    `timestamp`        DATETIME(6) NOT NULL,
    `carregamento_id`  BIGINT      NOT NULL,
    `veiculo_id`       BIGINT      NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_gps_carregamento_ts` (`carregamento_id`, `timestamp`),
    KEY `idx_gps_veiculo_ts` (`veiculo_id`, `timestamp`),
    CONSTRAINT `fk_gps_carregamento`
        FOREIGN KEY (`carregamento_id`) REFERENCES `carregamentos` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_gps_veiculo`
        FOREIGN KEY (`veiculo_id`) REFERENCES `veiculos` (`id`),
    CONSTRAINT `chk_gps_latitude`  CHECK (`latitude`  BETWEEN -90  AND 90),
    CONSTRAINT `chk_gps_longitude` CHECK (`longitude` BETWEEN -180 AND 180),
    CONSTRAINT `chk_gps_precisao`  CHECK (`precisao_metros` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 7. Alertas de atraso emitidos pelo motor de SLA
-- ---------------------------------------------------------------------
CREATE TABLE `alertas_atraso` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `data_hora_emissao` DATETIME(6)  NOT NULL,
    `minutos_excedidos` INT          NOT NULL,
    `mensagem`          VARCHAR(500) NOT NULL,
    `lido`              BIT(1)       NOT NULL DEFAULT b'0',
    `carregamento_id`   BIGINT       NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_alertas_carregamento` (`carregamento_id`),
    KEY `idx_alertas_lido` (`lido`),
    KEY `idx_alertas_emissao` (`data_hora_emissao`),
    CONSTRAINT `fk_alertas_carregamento`
        FOREIGN KEY (`carregamento_id`) REFERENCES `carregamentos` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `chk_alertas_minutos` CHECK (`minutos_excedidos` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 8. Parâmetros globais (W10) — lidos em tempo de execução
-- ---------------------------------------------------------------------
CREATE TABLE `parametros_globais` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `chave`      VARCHAR(80)  NOT NULL,
    `valor`      VARCHAR(255) NOT NULL,
    `descricao`  VARCHAR(500) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parametros_chave` (`chave`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
--  DADOS DE REFERÊNCIA / DEMONSTRAÇÃO
-- =====================================================================

-- Parâmetros globais
INSERT INTO `parametros_globais` (`id`, `chave`, `valor`, `descricao`) VALUES
    (1, 'TOLERANCIA_ATRASO_MINUTOS', '30',
        'Margem de tolerância, em minutos, antes de disparar o alerta de SLA.'),
    (2, 'FREQUENCIA_RECOLHA_GPS_SEGUNDOS', '60',
        'Intervalo, em segundos, de transmissão do GPS pela app móvel.'),
    (3, 'INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS', '5',
        'Intervalo, em minutos, da sincronização automática da fila offline.'),
    (4, 'PRECISAO_MAXIMA_GPS_METROS', '15.0',
        'Precisão máxima aceite para validar a localização GPS (metros).'),
    (5, 'INTERVALO_VERIFICACAO_SLA_MINUTOS', '1',
        'Intervalo, em minutos, entre execuções do motor de verificação de SLA.');

-- Fábricas (Maputo, Sofala e Nampula)
INSERT INTO `fabricas`
    (`id`, `nome`, `endereco`, `latitude`, `longitude`,
     `raio_geofence_metros`, `sla_limite_horas`, `horario_acostagem`, `ativa`)
VALUES
    (1, 'Fábrica da Matola', 'Av. da Indústria, Matola, Maputo',
        -25.9622, 32.4589, 600, 24, '06:00 - 22:00', b'1'),
    (2, 'Fábrica de Dondo', 'EN6, Dondo, Sofala',
        -19.6094, 34.7431, 500, 36, '05:00 - 23:00', b'1'),
    (3, 'Fábrica de Nacala', 'Zona Industrial, Nacala, Nampula',
        -14.5428, 40.6728, 450, 48, '06:00 - 18:00', b'1');

-- Percurso operacional: Marracuene (Guava/Mateque) -> CMC Zimpeto -> UJC.
-- Confirmar as coordenadas dos pontos no local antes da utilização operacional.
INSERT INTO `fabricas`
    (`id`, `nome`, `endereco`, `latitude`, `longitude`,
     `raio_geofence_metros`, `sla_limite_horas`, `horario_acostagem`, `ativa`)
VALUES
    (4, 'Fábrica de Guava', 'Guava, Marracuene, Província de Maputo',
        -25.815556, 32.625000, 1200, 24, '06:00 - 22:00', b'1'),
    (5, 'Fábrica de Mateque', 'Mateque, Marracuene, Província de Maputo',
        -25.790000, 32.650000, 1500, 24, '06:00 - 22:00', b'1'),
    (6, 'Centro Logístico CMC - Zimpeto', 'CMC, Zimpeto, Maputo',
        -25.835000, 32.574000, 1200, 24, '06:00 - 22:00', b'1'),
    (7, 'Unidade de Carga UJC',
        'Campus do Zimpeto, Rua do Grande Maputo, Q.88, Maputo',
        -25.820660, 32.579230, 100, 24, '06:00 - 22:00', b'1');

-- Frota
INSERT INTO `veiculos` (`id`, `matricula`, `modelo`, `capacidade_carga_toneladas`, `estado`) VALUES
    (1, 'ADM-01-MC', 'Volvo FH16', 34.0, 'DISPONIVEL'),
    (2, 'ADM-02-MC', 'Scania R500', 30.0, 'DISPONIVEL'),
    (3, 'ACD-03-MC', 'MAN TGX 33.500', 28.0, 'DISPONIVEL'),
    (4, 'ADM-04-MC', 'Mercedes-Benz Actros', 32.0, 'MANUTENCAO');

-- Utilizadores: criados automaticamente pelo DataSeeder no primeiro arranque.
-- A palavra-passe vem de LOGITRACK_DEMO_PASSWORD e é guardada apenas como BCrypt.
-- Para produção, desative o seed (LOGITRACK_DEMO_SEED=false) e crie os
-- utilizadores reais através da área de administração.

-- =====================================================================
--  Fim do script. No primeiro arranque a aplicação cria os carregamentos
--  de demonstração (guias, GPS e alertas) associados a estes registos.
-- =====================================================================
