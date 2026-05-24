CREATE TABLE IF NOT EXISTS correction_history
(
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    original_text  TEXT         NOT NULL,
    corrected_text TEXT         NOT NULL,
    source         VARCHAR(50)  NOT NULL DEFAULT 'manual',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_id (user_id),
    CONSTRAINT fk_correction_user FOREIGN KEY (user_id) REFERENCES `user` (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
