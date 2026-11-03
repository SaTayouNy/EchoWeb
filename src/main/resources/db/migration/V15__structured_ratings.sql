CREATE TABLE rating_schemes (
    scheme_version VARCHAR(64) PRIMARY KEY,
    formula_code VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_multiplier NUMERIC(12,8) NOT NULL,
    maximum_multiplier NUMERIC(12,8) NOT NULL,
    rounding_mode VARCHAR(16) NOT NULL,
    CONSTRAINT ck_rating_scheme_multiplier CHECK (minimum_multiplier >= 0 AND maximum_multiplier <= 1 AND minimum_multiplier <= maximum_multiplier)
);

CREATE TABLE rating_parameters (
    parameter_id VARCHAR(128) PRIMARY KEY,
    scheme_version VARCHAR(64) NOT NULL,
    parameter_code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    parameter_role VARCHAR(16) NOT NULL,
    display_order INTEGER NOT NULL,
    minimum_value INTEGER NOT NULL DEFAULT 1,
    maximum_value INTEGER NOT NULL DEFAULT 10,
    CONSTRAINT uk_rating_parameter_scheme_code UNIQUE (scheme_version, parameter_code),
    CONSTRAINT fk_rating_parameter_scheme FOREIGN KEY (scheme_version) REFERENCES rating_schemes(scheme_version),
    CONSTRAINT ck_rating_parameter_role CHECK (parameter_role IN ('FORM', 'IMPRESSION')),
    CONSTRAINT ck_rating_parameter_range CHECK (minimum_value = 1 AND maximum_value = 10)
);

INSERT INTO rating_schemes (scheme_version, formula_code, active, minimum_multiplier, maximum_multiplier, rounding_mode)
VALUES ('echo-form-impression-v1', 'FORM_IMPRESSION_LINEAR_V1', TRUE, 0.4, 1.0, 'HALF_UP');

INSERT INTO rating_parameters (parameter_id, scheme_version, parameter_code, name, description, parameter_role, display_order) VALUES
('echo-form-impression-v1:dramaturgy_structure', 'echo-form-impression-v1', 'dramaturgy_structure', 'Драматургия и структура', 'Оцените развитие истории, связность событий и построение повествования.', 'FORM', 1),
('echo-form-impression-v1:characters_performance', 'echo-form-impression-v1', 'characters_performance', 'Персонажи и исполнение', 'Оцените проработку персонажей и убедительность их исполнения.', 'FORM', 2),
('echo-form-impression-v1:visual_language', 'echo-form-impression-v1', 'visual_language', 'Визуальный язык', 'Оцените визуальное решение фильма: изображение, композицию и выразительность кадра.', 'FORM', 3),
('echo-form-impression-v1:sound_music', 'echo-form-impression-v1', 'sound_music', 'Звук и музыка', 'Оцените звуковое оформление, музыку и их вклад в восприятие фильма.', 'FORM', 4),
('echo-form-impression-v1:impression', 'echo-form-impression-v1', 'impression', 'Впечатление', 'Оцените личное впечатление и эмоциональный отклик после просмотра фильма.', 'IMPRESSION', 5);

ALTER TABLE user_ratings ALTER COLUMN rating DROP NOT NULL;
ALTER TABLE user_ratings ADD COLUMN scheme_version VARCHAR(64);
ALTER TABLE user_ratings ADD COLUMN final_score INTEGER;
ALTER TABLE user_ratings ADD CONSTRAINT fk_user_rating_scheme FOREIGN KEY (scheme_version) REFERENCES rating_schemes(scheme_version);
ALTER TABLE user_ratings ADD CONSTRAINT ck_user_rating_structured CHECK (
    (scheme_version IS NULL AND final_score IS NULL AND rating IS NOT NULL)
    OR (scheme_version IS NOT NULL AND final_score BETWEEN 0 AND 100 AND final_score IS NOT NULL)
);
CREATE INDEX idx_user_rating_media_scheme ON user_ratings(media_type, tmdb_id, scheme_version);

CREATE TABLE user_rating_values (
    rating_id UUID NOT NULL,
    parameter_code VARCHAR(64) NOT NULL,
    raw_value INTEGER NOT NULL,
    PRIMARY KEY (rating_id, parameter_code),
    CONSTRAINT fk_rating_value_rating FOREIGN KEY (rating_id) REFERENCES user_ratings(rating_id) ON DELETE CASCADE,
    CONSTRAINT ck_rating_value_range CHECK (raw_value BETWEEN 1 AND 10)
);

-- Historical scalar scores are intentionally preserved without invented parameter values.
-- Published scheme/formula/parameter definitions are immutable. Add a new scheme for future changes.
