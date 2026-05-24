CREATE TABLE IF NOT EXISTS vocabulary
(
    id       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT       NOT NULL,
    word     VARCHAR(100) NOT NULL,
    pinyin   VARCHAR(200) DEFAULT NULL,
    category VARCHAR(50)  DEFAULT NULL,
    frequency INT         NOT NULL DEFAULT 1,
    version  BIGINT       NOT NULL DEFAULT 1,
    created_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_id (user_id),
    KEY idx_user_id_version (user_id, version),
    CONSTRAINT fk_vocabulary_user FOREIGN KEY (user_id) REFERENCES `user` (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
