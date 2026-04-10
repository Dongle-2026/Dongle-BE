-- ============================================
-- 유스레터 PostgreSQL 초기화 스크립트
-- 파일 위치: docker/postgres/init.sql
-- Docker 컨테이너 최초 실행 시 자동 적용
-- ============================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS ltree;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================
-- 1. User/Auth 도메인
-- ============================================
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255),
    name            VARCHAR(50)  NOT NULL,
    nickname        VARCHAR(30)  NOT NULL UNIQUE,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    birth_date      DATE,
    gender          VARCHAR(10),
    region_code     VARCHAR(10),
    occupation      VARCHAR(50),
    income_level    VARCHAR(20),
    interests       JSONB DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE oauth_accounts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider    VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_id)
);

CREATE TABLE user_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    employment_status   VARCHAR(20),
    annual_income       INTEGER,
    is_married          BOOLEAN DEFAULT FALSE,
    household_members   INTEGER DEFAULT 1,
    education_level     VARCHAR(20),
    additional_criteria JSONB DEFAULT '{}',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- 2. News 도메인
-- ============================================
CREATE TABLE news (
    id           BIGSERIAL PRIMARY KEY,
    title        TEXT NOT NULL,
    content      TEXT NOT NULL,
    summary      TEXT,
    original_url TEXT NOT NULL UNIQUE,
    source       VARCHAR(100),
    category     VARCHAR(100),
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    embedding    VECTOR(1536)
);

CREATE INDEX idx_news_embedding ON news USING ivfflat (embedding vector_cosine_ops);

CREATE TABLE news_articles (
    id            BIGSERIAL PRIMARY KEY,
    source_url    VARCHAR(500) NOT NULL UNIQUE,
    source_name   VARCHAR(100) NOT NULL,
    title         VARCHAR(300) NOT NULL,
    content       TEXT NOT NULL,
    category      VARCHAR(30) NOT NULL,
    published_at  TIMESTAMPTZ,
    crawled_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE news_summaries (
    id              BIGSERIAL PRIMARY KEY,
    article_id      BIGINT NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    summary_text    TEXT NOT NULL,
    summary_version INTEGER NOT NULL DEFAULT 1,
    model_version   VARCHAR(50) NOT NULL,
    valid_from      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_to        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- 3. Term Dictionary 도메인
-- ============================================
CREATE TABLE financial_terms (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL UNIQUE,
    path              LTREE NOT NULL,
    definition        TEXT NOT NULL,
    simple_definition TEXT NOT NULL,
    search_vector     TSVECTOR,
    embedding         VECTOR(1536),
    difficulty_level  INTEGER DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE term_relations (
    id               BIGSERIAL PRIMARY KEY,
    term_id          BIGINT NOT NULL REFERENCES financial_terms(id) ON DELETE CASCADE,
    related_term_id  BIGINT NOT NULL REFERENCES financial_terms(id) ON DELETE CASCADE,
    relation_type    VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(term_id, related_term_id, relation_type)
);

CREATE TABLE article_term_highlights (
    id              BIGSERIAL PRIMARY KEY,
    article_id      BIGINT NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    term_id         BIGINT NOT NULL REFERENCES financial_terms(id),
    position_start  INTEGER NOT NULL,
    position_end    INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- 4. Policy 도메인
-- ============================================
CREATE TABLE policies (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(300) NOT NULL,
    description         TEXT NOT NULL,
    summary             TEXT,
    organization        VARCHAR(100) NOT NULL,
    region_code         VARCHAR(10),
    age_min             INTEGER,
    age_max             INTEGER,
    income_criteria     VARCHAR(50),
    occupation_criteria JSONB DEFAULT '[]',
    start_date          DATE,
    end_date            DATE,
    application_url     VARCHAR(500),
    source_url          VARCHAR(500),
    embedding           VECTOR(1536),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE news_bookmarks (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    article_id  BIGINT NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, article_id)
);

CREATE TABLE policy_bookmarks (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    policy_id   BIGINT NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, policy_id)
);

CREATE TABLE policy_notifications (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    policy_id          BIGINT NOT NULL REFERENCES policies(id) ON DELETE CASCADE,
    notify_before_days INTEGER NOT NULL DEFAULT 7,
    is_sent            BOOLEAN NOT NULL DEFAULT FALSE,
    notified_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- 5. Spending 도메인 (Event Sourcing)
-- ============================================
CREATE TABLE spending_events (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type   VARCHAR(20) NOT NULL,
    spending_id  BIGINT NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    category     VARCHAR(30) NOT NULL,
    description  VARCHAR(200),
    spent_at     DATE NOT NULL,
    metadata     JSONB DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE spending_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    snapshot_month  VARCHAR(7) NOT NULL,
    category_totals JSONB NOT NULL,
    total_amount    DECIMAL(12,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, snapshot_month)
);

CREATE TABLE spending_insights (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    analysis_start  DATE NOT NULL,
    analysis_end    DATE NOT NULL,
    insight_text    TEXT NOT NULL,
    insight_data    JSONB NOT NULL,
    model_version   VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- 6. Guides 도메인
-- ============================================
CREATE TABLE guides (
    id               BIGSERIAL PRIMARY KEY,
    guide_type       VARCHAR(20) NOT NULL,
    topic            VARCHAR(100) NOT NULL,
    title            VARCHAR(300) NOT NULL,
    content          TEXT NOT NULL,
    difficulty_level INTEGER DEFAULT 1,
    embedding        VECTOR(1536),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- 7. AI Agent 도메인
-- ============================================
CREATE TABLE chat_sessions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    tool_calls  JSONB,
    sources     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE agent_feedback (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(message_id, user_id)
);

-- ============================================
-- 8. Activity Tracking 도메인
-- ============================================
CREATE TABLE reading_history (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    article_id            BIGINT NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    read_duration_seconds INTEGER,
    read_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE term_lookup_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    term_id      BIGINT NOT NULL REFERENCES financial_terms(id),
    article_id   BIGINT REFERENCES news_articles(id),
    looked_up_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- 인덱스
-- ============================================
CREATE INDEX idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_oauth_provider ON oauth_accounts(provider, provider_id);

CREATE INDEX idx_articles_category_published ON news_articles(category, published_at DESC);
CREATE INDEX idx_articles_crawled ON news_articles(crawled_at DESC);
CREATE INDEX idx_summaries_article_valid ON news_summaries(article_id) WHERE valid_to IS NULL;
CREATE INDEX idx_highlights_article ON article_term_highlights(article_id);

CREATE INDEX idx_terms_path_gist ON financial_terms USING GIST(path);
CREATE INDEX idx_terms_search_gin ON financial_terms USING GIN(search_vector);
CREATE INDEX idx_terms_name_trgm ON financial_terms USING GIN(name gin_trgm_ops);
CREATE INDEX idx_terms_embedding_hnsw ON financial_terms
    USING hnsw(embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_policies_age_range ON policies(age_min, age_max);
CREATE INDEX idx_policies_region ON policies(region_code);
CREATE INDEX idx_policies_end_date ON policies(end_date) WHERE end_date IS NOT NULL;
CREATE INDEX idx_policies_embedding_hnsw ON policies
    USING hnsw(embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_spending_events_user_date ON spending_events(user_id, spent_at DESC);
CREATE INDEX idx_spending_events_spending_id ON spending_events(spending_id, created_at);
CREATE INDEX idx_snapshots_user_month ON spending_snapshots(user_id, snapshot_month DESC);

CREATE INDEX idx_sessions_user ON chat_sessions(user_id, updated_at DESC);
CREATE INDEX idx_messages_session ON chat_messages(session_id, created_at);

CREATE INDEX idx_reading_user_date ON reading_history(user_id, read_at DESC);
CREATE INDEX idx_termlookup_user ON term_lookup_log(user_id, looked_up_at DESC);

CREATE INDEX idx_guides_type ON guides(guide_type);
CREATE INDEX idx_guides_embedding_hnsw ON guides
    USING hnsw(embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- ============================================
-- tsvector 자동 업데이트 트리거
-- ============================================
CREATE OR REPLACE FUNCTION update_term_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('simple', COALESCE(NEW.name, '')) ||
        to_tsvector('simple', COALESCE(NEW.definition, '')) ||
        to_tsvector('simple', COALESCE(NEW.simple_definition, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_terms_search_vector
    BEFORE INSERT OR UPDATE OF name, definition, simple_definition
    ON financial_terms
    FOR EACH ROW
    EXECUTE FUNCTION update_term_search_vector();

-- ============================================
-- updated_at 자동 갱신 트리거
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_user_profiles_updated_at
    BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_financial_terms_updated_at
    BEFORE UPDATE ON financial_terms FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_policies_updated_at
    BEFORE UPDATE ON policies FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_guides_updated_at
    BEFORE UPDATE ON guides FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_chat_sessions_updated_at
    BEFORE UPDATE ON chat_sessions FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ============================================
-- Row-Level Security
-- ============================================
ALTER TABLE spending_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE spending_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE spending_insights ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE reading_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE term_lookup_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE policy_bookmarks ENABLE ROW LEVEL SECURITY;
ALTER TABLE policy_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE news_bookmarks ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'youthletter_app') THEN
        CREATE ROLE youthletter_app LOGIN PASSWORD 'youthletter_app_1234';
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO youthletter_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO youthletter_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO youthletter_app;

CREATE POLICY user_isolation_spending_events ON spending_events
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);
CREATE POLICY user_isolation_spending_snapshots ON spending_snapshots
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);
CREATE POLICY user_isolation_spending_insights ON spending_insights
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);
CREATE POLICY user_isolation_chat_sessions ON chat_sessions
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);
CREATE POLICY user_isolation_chat_messages ON chat_messages
    TO youthletter_app
    USING (session_id IN (
        SELECT id FROM chat_sessions
        WHERE user_id = current_setting('app.current_user_id', true)::bigint
    ));
CREATE POLICY user_isolation_reading_history ON reading_history
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);
CREATE POLICY user_isolation_term_lookup ON term_lookup_log
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);
CREATE POLICY user_isolation_policy_bookmarks ON policy_bookmarks
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);
CREATE POLICY user_isolation_news_bookmarks ON news_bookmarks
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);
CREATE POLICY user_isolation_policy_notifications ON policy_notifications
    TO youthletter_app
    USING (user_id = current_setting('app.current_user_id', true)::bigint);

-- ============================================
-- 시드 데이터 (개발용)
-- ============================================
INSERT INTO users (email, password_hash, name, nickname, role, birth_date, gender, region_code, occupation, income_level, interests)
VALUES
    ('test@youthletter.com',  '$2a$10$dummyhashfordev000000000000000000000000000000', '테스트유저',  '테스트닉네임', 'USER',  '2000-01-15', 'MALE', '26', 'STUDENT', 'LOW', '["주식", "청년정책"]'),
    ('admin@youthletter.com', '$2a$10$dummyhashfordev000000000000000000000000000000', '관리자',      '관리자닉네임', 'ADMIN', '1990-01-01', 'MALE', '11', 'EMPLOYED', 'ABOVE_MEDIAN', '[]');

INSERT INTO user_profiles (user_id, employment_status, annual_income, is_married, household_members, education_level)
VALUES
    (1, 'STUDENT', 0, FALSE, 1, 'UNIVERSITY');

INSERT INTO financial_terms (name, path, definition, simple_definition, difficulty_level) VALUES
    ('주식', 'finance.stock', '기업의 자본을 구성하는 균등한 비율의 단위로, 주주의 지분을 나타내는 유가증권', '회사의 일부를 사는 거예요. 회사가 잘되면 내 주식 가치도 올라가요!', 1),
    ('ETF', 'finance.stock.etf', 'Exchange Traded Fund의 약자로, 특정 지수를 추종하는 인덱스 펀드를 거래소에 상장시켜 주식처럼 거래할 수 있게 한 상품', '여러 주식을 한 번에 묶어서 사고파는 장바구니 같은 상품이에요', 2),
    ('배당', 'finance.stock.dividend', '기업이 영업활동을 통해 벌어들인 이익의 일부를 주주에게 나누어 주는 것', '회사가 번 돈 중 일부를 주식 가진 사람에게 나눠주는 거예요', 2),
    ('생명보험', 'finance.insurance.life', '사람의 생존 또는 사망에 관하여 약정한 급여를 제공하는 보험', '내가 다치거나 아프거나 사망했을 때 돈을 받을 수 있는 보험이에요', 1),
    ('실손보험', 'finance.insurance.health', '실제 부담한 의료비를 보장하는 보험', '병원비를 실제로 낸 만큼 돌려받을 수 있는 보험이에요', 1),
    ('신용대출', 'finance.loan.credit', '담보 없이 개인의 신용을 기반으로 제공되는 대출', '집이나 차 같은 담보 없이, 내 신용점수만으로 빌리는 대출이에요', 2),
    ('전세대출', 'finance.loan.mortgage', '전세보증금을 마련하기 위해 받는 주택 관련 대출', '전셋집 보증금이 부족할 때 은행에서 빌리는 대출이에요. 청년 전세대출은 금리가 낮아요!', 2),
    ('금리', 'finance.rate', '자금의 사용에 대한 대가로 지급하는 이자의 비율', '돈을 빌리거나 맡길 때 붙는 이자의 비율이에요. 금리가 오르면 대출이자도 올라가요', 1),
    ('인플레이션', 'finance.economy.inflation', '물가 수준이 지속적으로 상승하는 현상', '물건 값이 계속 오르는 현상이에요. 같은 돈으로 살 수 있는 게 줄어들어요', 2),
    ('적금', 'finance.savings', '매월 일정 금액을 정해진 기간 동안 납입하고 만기 시 원금과 이자를 받는 금융 상품', '매달 정해진 돈을 꼬박꼬박 넣으면, 나중에 이자까지 붙여서 돌려받는 통장이에요', 1);

INSERT INTO term_relations (term_id, related_term_id, relation_type) VALUES
    (2, 1, 'PARENT'),
    (3, 1, 'SEE_ALSO'),
    (4, 5, 'SEE_ALSO'),
    (6, 7, 'SEE_ALSO');

INSERT INTO policies (title, description, summary, organization, region_code, age_min, age_max, income_criteria, occupation_criteria, start_date, end_date, application_url)
VALUES
    ('청년 월세 지원', '청년의 주거비 부담 경감을 위해 월 최대 20만원의 월세를 지원합니다.', '만 19~34세 무주택 청년에게 월 최대 20만원 월세 지원', '국토교통부', NULL, 19, 34, 'BELOW_MEDIAN', '["EMPLOYED", "UNEMPLOYED", "STUDENT"]', '2026-01-01', '2026-12-31', 'https://www.myhome.go.kr'),
    ('청년내일저축계좌', '매월 10만원을 저축하면 정부가 10~30만원을 추가 적립하는 자산형성 지원사업입니다.', '매월 10만원 저축 시 정부가 최대 30만원 추가 적립', '보건복지부', NULL, 19, 34, 'BELOW_MEDIAN', '["EMPLOYED"]', '2026-03-01', '2026-06-30', 'https://www.bokjiro.go.kr'),
    ('부산 청년 취업지원금', '부산 거주 미취업 청년에게 구직활동 지원금 월 50만원을 3개월간 지급합니다.', '부산 거주 미취업 청년 대상 월 50만원 x 3개월 구직지원금', '부산광역시', '26', 19, 34, 'BELOW_200PCT', '["UNEMPLOYED"]', '2026-04-01', '2026-09-30', 'https://www.busan.go.kr');
