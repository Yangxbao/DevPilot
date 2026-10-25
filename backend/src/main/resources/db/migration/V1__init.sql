-- =====================================================================
-- DevPilot 初始库表结构 (V1)
-- MySQL 8.0 / utf8mb4 / InnoDB
-- 由 Flyway 在应用启动时自动执行，禁止修改已应用的脚本
-- =====================================================================

-- ---------------------------------------------------------------------
-- 用户：账号与角色（ADMIN / USER）
-- ---------------------------------------------------------------------
CREATE TABLE `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 摘要',
    `nickname`      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '显示名',
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户';

-- ---------------------------------------------------------------------
-- 项目：业务隔离顶层单元（数据归属 = owner）
-- ---------------------------------------------------------------------
CREATE TABLE `project` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_id`    BIGINT       NOT NULL COMMENT '所属用户',
    `name`        VARCHAR(100) NOT NULL COMMENT '项目名',
    `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '描述',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_name` (`owner_id`, `name`),
    KEY `idx_owner` (`owner_id`),
    CONSTRAINT `fk_project_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '项目';

-- ---------------------------------------------------------------------
-- 代码仓库：一次导入的来源（zip 上传或公开 Git URL）
-- ---------------------------------------------------------------------
CREATE TABLE `repository` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`      BIGINT       NOT NULL COMMENT '所属项目',
    `name`            VARCHAR(100) NOT NULL COMMENT '仓库名',
    `source_type`     VARCHAR(20)  NOT NULL COMMENT 'GIT_URL / UPLOAD',
    `git_url`         VARCHAR(500) NULL COMMENT 'Git 地址(仅 https 公开)',
    `git_ref`         VARCHAR(100) NULL COMMENT '分支/标签',
    `file_name`       VARCHAR(255) NULL COMMENT '上传包原始文件名',
    `import_status`   VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
    `error_message`   VARCHAR(1000) NULL COMMENT '导入失败原因',
    `file_count`      INT          NOT NULL DEFAULT 0 COMMENT '入库文件数',
    `total_size`      BIGINT       NOT NULL DEFAULT 0 COMMENT '入库总字节',
    `last_indexed_at` DATETIME     NULL COMMENT '最近成功索引时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    CONSTRAINT `fk_repo_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码仓库导入源';

-- ---------------------------------------------------------------------
-- 代码文件：文件清单与文本内容（被过滤的文件记录原因）
-- ---------------------------------------------------------------------
CREATE TABLE `code_file` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `repository_id` BIGINT       NOT NULL COMMENT '所属仓库',
    `path`          VARCHAR(500) NOT NULL COMMENT '包内相对路径',
    `language`      VARCHAR(30)  NOT NULL DEFAULT '' COMMENT '语言标识/空',
    `size`          BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
    `line_count`    INT          NOT NULL DEFAULT 0 COMMENT '行数',
    `content`       MEDIUMTEXT   NULL COMMENT '文本内容(二进制/超限为 NULL)',
    `skip_reason`   VARCHAR(100) NULL COMMENT '跳过原因: binary/too_large/unsupported/dependency_dir/sensitive',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_repo` (`repository_id`),
    KEY `idx_repo_path` (`repository_id`, `path`(191)),
    CONSTRAINT `fk_file_repo` FOREIGN KEY (`repository_id`) REFERENCES `repository` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码文件';

-- ---------------------------------------------------------------------
-- 代码分块：检索单元，关键词检索依赖 FULLTEXT(ngram)
-- ---------------------------------------------------------------------
CREATE TABLE `code_chunk` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `repository_id` BIGINT      NOT NULL COMMENT '所属仓库(冗余,便于清理)',
    `file_id`       BIGINT      NOT NULL COMMENT '所属文件',
    `chunk_index`   INT         NOT NULL DEFAULT 0 COMMENT '块序号',
    `start_line`    INT         NOT NULL DEFAULT 0 COMMENT '起始行(含)',
    `end_line`      INT         NOT NULL DEFAULT 0 COMMENT '结束行(含)',
    `content`       MEDIUMTEXT  NOT NULL COMMENT '分块文本',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_repo` (`repository_id`),
    KEY `idx_file` (`file_id`),
    FULLTEXT KEY `ft_content` (`content`) WITH PARSER ngram,
    CONSTRAINT `fk_chunk_file` FOREIGN KEY (`file_id`) REFERENCES `code_file` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_chunk_repo` FOREIGN KEY (`repository_id`) REFERENCES `repository` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码分块';

-- ---------------------------------------------------------------------
-- 索引任务：状态机 + 并发防重(active 唯一索引技巧)
-- active: PENDING/RUNNING 时为 1，终态置 NULL —— (repository_id, active)
-- 的唯一索引天然拒绝同一仓库的并发重复任务(NULL 可重复)
-- ---------------------------------------------------------------------
CREATE TABLE `index_task` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `repository_id`   BIGINT        NOT NULL COMMENT '目标仓库',
    `project_id`      BIGINT        NOT NULL COMMENT '所属项目(冗余)',
    `task_no`         VARCHAR(32)   NOT NULL COMMENT '任务号(幂等追踪)',
    `trigger_type`    VARCHAR(20)   NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/REINDEX',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `active`          TINYINT       NULL COMMENT '1=进行中, 终态=NULL(唯一索引防并发)',
    `total_files`     INT           NOT NULL DEFAULT 0 COMMENT '文件总数',
    `processed_files` INT           NOT NULL DEFAULT 0 COMMENT '已处理文件数',
    `total_chunks`    INT           NOT NULL DEFAULT 0 COMMENT '分块总数',
    `error_message`   VARCHAR(1000) NULL COMMENT '失败原因',
    `started_at`      DATETIME      NULL COMMENT '开始时间',
    `finished_at`     DATETIME      NULL COMMENT '结束时间',
    `created_by`      BIGINT        NOT NULL COMMENT '触发人',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    UNIQUE KEY `uk_repo_active` (`repository_id`, `active`),
    KEY `idx_repo_status` (`repository_id`, `status`),
    CONSTRAINT `fk_itask_repo` FOREIGN KEY (`repository_id`) REFERENCES `repository` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '索引任务';

-- ---------------------------------------------------------------------
-- AI 分析记录：问答(QA)与 Bug 分析(BUG_ANALYSIS)统一记录
-- ---------------------------------------------------------------------
CREATE TABLE `ai_analysis` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`        BIGINT        NOT NULL COMMENT '所属项目',
    `repository_id`     BIGINT        NULL COMMENT '分析的目标仓库',
    `type`              VARCHAR(20)   NOT NULL COMMENT 'QA / BUG_ANALYSIS',
    `status`            VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `question`          TEXT          NOT NULL COMMENT '问题或 Bug 输入摘要',
    `input_detail`      MEDIUMTEXT    NULL COMMENT '完整输入(堆栈/复现步骤)',
    `answer`            MEDIUMTEXT    NULL COMMENT '回答/报告正文',
    `structured_result` JSON          NULL COMMENT '结构化结果(原因/证据/建议等)',
    `mode`              VARCHAR(10)   NOT NULL DEFAULT 'REAL' COMMENT 'real=真实模型 / mock=演示',
    `provider`          VARCHAR(50)   NULL COMMENT '模型提供方',
    `model`             VARCHAR(100)  NULL COMMENT '模型标识',
    `confidence`        DECIMAL(4,3)  NULL COMMENT '置信度 0~1',
    `evidence_count`    INT           NULL COMMENT '证据条数',
    `duration_ms`       BIGINT        NULL COMMENT '总耗时',
    `error_message`     VARCHAR(1000) NULL COMMENT '失败原因',
    `created_by`        BIGINT        NOT NULL COMMENT '发起人',
    `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_type` (`project_id`, `type`),
    KEY `idx_creator` (`created_by`),
    CONSTRAINT `fk_ana_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 分析记录';

-- ---------------------------------------------------------------------
-- Agent 执行轨迹：每步名称/状态/耗时/脱敏摘要（可审计）
-- ---------------------------------------------------------------------
CREATE TABLE `analysis_step` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `analysis_id` BIGINT      NOT NULL COMMENT '所属分析',
    `step_order`  INT         NOT NULL DEFAULT 0 COMMENT '步骤序号',
    `name`        VARCHAR(50) NOT NULL COMMENT '步骤名',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'RUNNING/SUCCESS/FAILED/SKIPPED',
    `detail`      MEDIUMTEXT  NULL COMMENT '摘要(截断+脱敏)',
    `duration_ms` BIGINT      NULL COMMENT '耗时',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_analysis` (`analysis_id`),
    CONSTRAINT `fk_step_ana` FOREIGN KEY (`analysis_id`) REFERENCES `ai_analysis` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 执行步骤';

-- ---------------------------------------------------------------------
-- 缺陷任务：状态机由服务端校验（OPEN→IN_PROGRESS→RESOLVED→CLOSED，
-- RESOLVED/IN_PROGRESS 可退回 OPEN）
-- ---------------------------------------------------------------------
CREATE TABLE `defect` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`  BIGINT        NOT NULL COMMENT '所属项目',
    `title`       VARCHAR(200)  NOT NULL COMMENT '标题',
    `description` MEDIUMTEXT    NULL COMMENT '描述/复现步骤',
    `severity`    VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM' COMMENT 'CRITICAL/MAJOR/MEDIUM/MINOR',
    `priority`    VARCHAR(20)   NOT NULL DEFAULT 'P2' COMMENT 'P0/P1/P2/P3',
    `status`      VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/RESOLVED/CLOSED',
    `assignee_id` BIGINT        NULL COMMENT '负责人',
    `reporter_id` BIGINT        NOT NULL COMMENT '报告人',
    `analysis_id` BIGINT        NULL COMMENT '关联的 AI 分析',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_status` (`project_id`, `status`),
    KEY `idx_assignee` (`assignee_id`),
    CONSTRAINT `fk_defect_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_defect_assignee` FOREIGN KEY (`assignee_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_defect_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '缺陷任务';

-- ---------------------------------------------------------------------
-- 缺陷处理记录
-- ---------------------------------------------------------------------
CREATE TABLE `defect_comment` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `defect_id`  BIGINT        NOT NULL COMMENT '所属缺陷',
    `author_id`  BIGINT        NOT NULL COMMENT '作者',
    `content`    VARCHAR(2000) NOT NULL COMMENT '内容',
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_defect` (`defect_id`),
    CONSTRAINT `fk_cmt_defect` FOREIGN KEY (`defect_id`) REFERENCES `defect` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_cmt_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '缺陷处理记录';
