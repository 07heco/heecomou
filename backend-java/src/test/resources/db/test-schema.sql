CREATE TABLE IF NOT EXISTS `user`
(
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    nickname      VARCHAR(50)  DEFAULT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(100) DEFAULT NULL,
    phone         VARCHAR(20)  DEFAULT NULL,
    avatar_url    VARCHAR(500) DEFAULT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_username UNIQUE (username)
);
