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

CREATE TABLE IF NOT EXISTS book (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    title           VARCHAR(200) NOT NULL,
    author          VARCHAR(100) NOT NULL,
    description     TEXT,
    cover_image_url VARCHAR(500),
    genre           VARCHAR(20)  NOT NULL,
    country         VARCHAR(20)  NOT NULL,
    bookmark_count  INT          NOT NULL DEFAULT 0,
    has_content     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_book_genre (genre),
    KEY idx_book_bookmark_count (bookmark_count)
);

CREATE TABLE IF NOT EXISTS chapter (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    book_id        BIGINT       NOT NULL,
    chapter_number INT          NOT NULL,
    title          VARCHAR(200) NOT NULL,
    content        LONGTEXT,
    created_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chapter_book_id (book_id),
    CONSTRAINT fk_chapter_book FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE IF NOT EXISTS scene (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    book_id     BIGINT       NOT NULL,
    chapter_id  BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL,
    excerpt     TEXT,
    scene_order INT          NOT NULL,
    created_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_scene_book_id (book_id),
    KEY idx_scene_chapter_id (chapter_id),
    CONSTRAINT fk_scene_book FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_scene_chapter FOREIGN KEY (chapter_id) REFERENCES chapter (id)
);

CREATE TABLE IF NOT EXISTS bookmark (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    book_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_bookmark_user_book (user_id, book_id),
    KEY idx_bookmark_user_id (user_id),
    KEY idx_bookmark_book_id (book_id),
    CONSTRAINT fk_bookmark_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookmark_book FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE IF NOT EXISTS content_job (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    book_id       BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    chapter_count INT,
    error_message TEXT,
    retryable     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_content_job_book_id (book_id),
    CONSTRAINT fk_content_job_book FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE IF NOT EXISTS summary_job (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    user_id             BIGINT      NOT NULL,
    book_id             BIGINT      NOT NULL,
    status              VARCHAR(20) NOT NULL,
    summary_document_id VARCHAR(36),
    error_message       TEXT,
    retryable           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_summary_job_user_book (user_id, book_id),
    KEY idx_summary_job_status (status),
    KEY idx_summary_job_book_id (book_id),
    CONSTRAINT fk_summary_job_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_summary_job_book FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE IF NOT EXISTS illustration_job (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                  BIGINT       NOT NULL,
    scene_id                 BIGINT       NOT NULL,
    style                    VARCHAR(20)  NOT NULL,
    prompt_hint              VARCHAR(500),
    status                   VARCHAR(20)  NOT NULL,
    illustration_document_id VARCHAR(255),
    error_message            TEXT,
    retryable                BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               DATETIME     NOT NULL,
    updated_at               DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_illustration_job_user_id (user_id),
    KEY idx_illustration_job_scene_id (scene_id),
    CONSTRAINT fk_illustration_job_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_illustration_job_scene FOREIGN KEY (scene_id) REFERENCES scene (id)
);

CREATE TABLE IF NOT EXISTS quiz_job (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    book_id        BIGINT      NOT NULL,
    question_count INT         NOT NULL,
    difficulty     VARCHAR(10) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    quiz_id        BIGINT,
    error_message  TEXT,
    retryable      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     DATETIME    NOT NULL,
    updated_at     DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_quiz_job_user_id (user_id),
    KEY idx_quiz_job_book_id (book_id),
    CONSTRAINT fk_quiz_job_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_quiz_job_book FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE IF NOT EXISTS quiz (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    book_id        BIGINT      NOT NULL,
    quiz_job_id    BIGINT,
    difficulty     VARCHAR(10) NOT NULL,
    question_count INT         NOT NULL,
    created_at     DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_quiz_user_id (user_id),
    KEY idx_quiz_book_id (book_id),
    KEY idx_quiz_quiz_job_id (quiz_job_id),
    CONSTRAINT fk_quiz_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_quiz_book FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_quiz_job FOREIGN KEY (quiz_job_id) REFERENCES quiz_job (id)
);

CREATE TABLE IF NOT EXISTS question (
    id                   BIGINT   NOT NULL AUTO_INCREMENT,
    quiz_id              BIGINT   NOT NULL,
    content              TEXT     NOT NULL,
    explanation          TEXT,
    correct_option_order INT      NOT NULL,
    question_order       INT      NOT NULL,
    created_at           DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_question_quiz_id (quiz_id),
    CONSTRAINT fk_question_quiz FOREIGN KEY (quiz_id) REFERENCES quiz (id)
);

CREATE TABLE IF NOT EXISTS question_option (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    question_id    BIGINT   NOT NULL,
    content        TEXT     NOT NULL,
    option_order   INT      NOT NULL,
    created_at     DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_question_option_question_id (question_id),
    CONSTRAINT fk_question_option_question FOREIGN KEY (question_id) REFERENCES question (id)
);

CREATE TABLE IF NOT EXISTS quiz_submission (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    quiz_id       BIGINT   NOT NULL,
    user_id       BIGINT   NOT NULL,
    score         INT      NOT NULL,
    correct_count INT      NOT NULL,
    total_count   INT      NOT NULL,
    submitted_at  DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_quiz_submission_user_submitted_at (user_id, submitted_at DESC),
    KEY idx_quiz_submission_quiz_id (quiz_id),
    CONSTRAINT fk_quiz_submission_quiz FOREIGN KEY (quiz_id) REFERENCES quiz (id),
    CONSTRAINT fk_quiz_submission_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS submission_answer (
    id                    BIGINT   NOT NULL AUTO_INCREMENT,
    submission_id         BIGINT   NOT NULL,
    question_id           BIGINT   NOT NULL,
    selected_option_order INT      NOT NULL,
    is_correct            BOOLEAN  NOT NULL,
    created_at            DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_submission_answer_submission_id (submission_id),
    KEY idx_submission_answer_question_id (question_id),
    CONSTRAINT fk_submission_answer_submission FOREIGN KEY (submission_id) REFERENCES quiz_submission (id),
    CONSTRAINT fk_submission_answer_question FOREIGN KEY (question_id) REFERENCES question (id)
);
