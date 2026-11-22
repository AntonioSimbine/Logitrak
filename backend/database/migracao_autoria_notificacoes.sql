-- Autoria real das acções manuais e caixa de notificações persistida.
-- Compatível com a instalação atual: não apaga nem recria tabelas, não altera
-- histórico existente e pode ser executada mais do que uma vez.
-- O Hibernate (ddl-auto=update) cria as mesmas colunas no arranque; este
-- script existe para bases geridas manualmente e para documentar o contrato.

DROP PROCEDURE IF EXISTS logitrack_add_column;
DELIMITER $$
CREATE PROCEDURE logitrack_add_column(
    IN p_tabela VARCHAR(64), IN p_coluna VARCHAR(64), IN p_definicao TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_tabela
          AND COLUMN_NAME = p_coluna
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_tabela, '` ADD COLUMN `',
                          p_coluna, '` ', p_definicao);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- Autoria da rejeição/conclusão de carregamentos (registos antigos ficam a NULL).
CALL logitrack_add_column('carregamentos', 'autor_id', 'BIGINT NULL');
CALL logitrack_add_column('carregamentos', 'autor_nome', 'VARCHAR(120) NULL');
CALL logitrack_add_column('carregamentos', 'autor_perfil', 'VARCHAR(30) NULL');
CALL logitrack_add_column('carregamentos', 'tipo_accao', 'VARCHAR(60) NULL');
CALL logitrack_add_column('carregamentos', 'data_hora_accao', 'DATETIME NULL');

-- Autoria do tratamento manual de alertas de SLA.
CALL logitrack_add_column('alertas_atraso', 'tratado_por_id', 'BIGINT NULL');
CALL logitrack_add_column('alertas_atraso', 'tratado_por_nome', 'VARCHAR(120) NULL');
CALL logitrack_add_column('alertas_atraso', 'tratado_por_perfil', 'VARCHAR(30) NULL');
CALL logitrack_add_column('alertas_atraso', 'data_hora_tratamento', 'DATETIME NULL');

CREATE TABLE IF NOT EXISTS notificacoes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    destinatario_id BIGINT NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    origem VARCHAR(20) NOT NULL,
    autor_id BIGINT NULL,
    autor_nome VARCHAR(120) NULL,
    autor_perfil VARCHAR(30) NULL,
    acao_tipo VARCHAR(60) NULL,
    data_hora_criacao DATETIME NOT NULL,
    carregamento_id BIGINT NULL,
    guia_numero VARCHAR(60) NULL,
    titulo VARCHAR(120) NULL,
    mensagem VARCHAR(500) NULL,
    lido BIT(1) NOT NULL DEFAULT 0,
    data_hora_leitura DATETIME NULL,
    PRIMARY KEY (id),
    INDEX idx_notificacoes_destinatario_lido (destinatario_id, lido),
    INDEX idx_notificacoes_criacao (data_hora_criacao),
    INDEX idx_notificacoes_carregamento (carregamento_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS logitrack_add_column;
