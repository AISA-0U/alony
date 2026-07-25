-- Incremental migration for position-based question banks and automatic paper generation.
-- Safe for existing data: legacy questions default to the common professional ethics bank.

CREATE TABLE IF NOT EXISTS `t_job_position` (
  `id` int NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `item_order` int NOT NULL DEFAULT 0,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_position_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

ALTER TABLE `t_question`
  ADD COLUMN `bank_type` int NOT NULL DEFAULT 3 COMMENT '1 position, 2 safety, 3 professional ethics' AFTER `subject_id`,
  ADD COLUMN `position_id` int NULL COMMENT 'required for bank_type 1 or 2' AFTER `bank_type`,
  ADD KEY `idx_question_bank_position_type` (`bank_type`, `position_id`, `question_type`, `deleted`, `status`);

-- A 2048-bit RSA ciphertext requires 344 Base64 characters.
ALTER TABLE `t_user`
  MODIFY COLUMN `password` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL;

-- TODO(EXAM-BANK): add actual positions before importing questions. Examples only:
-- INSERT INTO t_job_position(code, name, item_order) VALUES ('electrician', 'Electrician', 10);
-- INSERT INTO t_job_position(code, name, item_order) VALUES ('welder', 'Welder', 20);
-- INSERT INTO t_job_position(code, name, item_order) VALUES ('sheet-metal', 'Sheet metal worker', 30);
