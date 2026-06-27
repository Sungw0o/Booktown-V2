CREATE TABLE IF NOT EXISTS users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    password          VARCHAR(255),
    nickname          VARCHAR(50)  NOT NULL,
    profile_image_url VARCHAR(500),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_nickname (nickname)
);

CREATE TABLE IF NOT EXISTS oauth_accounts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    provider    VARCHAR(20)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_provider_id (provider, provider_id),
    KEY idx_oauth_user_id (user_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (id)
);
