# XZS Exam System

Position-based examination system built with Spring Boot, MySQL, Vue 2, and a WeChat mini program.

## Automatic paper rules

- 100 questions and 100 points per paper.
- Every question is worth 1 point.
- Question banks: 80 position questions, 10 safety questions, and 10 common professional ethics questions.
- Question types: 30 choice, 20 gap-filling, 40 true/false, and 10 short-answer questions.
- Papers are generated randomly for the selected position and subject. Generation fails atomically when a required bucket is short.

## Setup

1. Apply `xzs-mysql.sql` to create the base database.
2. Apply `database/migration/V4_0_0__job_question_bank.sql`.
3. Configure database, RSA, WeChat, and Qiniu values with environment variables documented in `source/xzs/src/main/resources/application.yml`.
4. See `docs/question-bank-api.md` for position management, question import, and automatic paper generation APIs.

## Development

- Backend: Java 8 and Maven.
- Admin frontend: `source/vue/xzs-admin`.
- Student frontend: `source/vue/xzs-student`.
- WeChat mini program: `source/wx/xzs-student`.

### Local Windows environment

The repository includes repeatable PowerShell scripts for an isolated D-drive setup:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/local/setup-local.ps1
powershell -ExecutionPolicy Bypass -File scripts/local/start-local.ps1
```

The setup uses portable MySQL 8.0.42, Java 8, Maven 3.9, and Node 16.20.2. It creates `.env.local`, imports both SQL files,
generates a local RSA key pair, and resets the demo account passwords. Runtime data and
secrets are stored outside the repository under `D:\testvue\.runtime\xzs-exam` and are not
committed. Stop all local services with:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/local/stop-local.ps1
```

Local URLs:

- Backend: `http://localhost:8000`
- Student: `http://localhost:8001`
- Admin: `http://localhost:8002`

Question data and real position records are intentionally not included. Search for `TODO(EXAM-BANK)` for pending data-integration points.

## License

AGPL-3.0. See `LICENSE`.

---

# XZS 考试系统

基于 Spring Boot、MySQL、Vue 2 及微信小程序构建的岗位考试系统。

## 自动组卷规则

- 每张试卷 100 道题，满分 100 分。
- 每题 1 分。
- 题库构成：80 道岗位题、10 道安全题、10 道通用职业道德题。
- 题型分布：30 道选择题、20 道填空题、40 道判断题、10 道简答题。
- 试卷按所选岗位和科目随机生成。当某一题库数量不足时，组卷将原子性失败。

## 环境搭建

1. 执行 `xzs-mysql.sql` 创建基础数据库。
2. 执行 `database/migration/V4_0_0__job_question_bank.sql`。
3. 根据 `source/xzs/src/main/resources/application.yml` 中记录的环境变量，配置数据库、RSA、微信及七牛相关参数。
4. 参见 `docs/question-bank-api.md` 了解岗位管理、题目导入及自动组卷 API。

## 开发

- 后端：Java 8 + Maven。
- 管理端前端：`source/vue/xzs-admin`。
- 学生端前端：`source/vue/xzs-student`。
- 微信小程序：`source/wx/xzs-student`。

### 本地 Windows 环境

仓库提供了可复用的 PowerShell 脚本，用于在 D 盘搭建隔离环境：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/local/setup-local.ps1
powershell -ExecutionPolicy Bypass -File scripts/local/start-local.ps1
```

该环境使用便携版 MySQL 8.0.42、Java 8、Maven 3.9 及 Node 16.20.2。脚本会创建 `.env.local`、导入两个 SQL 文件、生成本地 RSA 密钥对并重置演示账号密码。运行时数据和密钥存储在仓库外的 `D:\testvue\.runtime\xzs-exam` 目录下，不会纳入版本控制。使用以下命令停止所有本地服务：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/local/stop-local.ps1
```

本地访问地址：

- 后端：`http://localhost:8000`
- 学生端：`http://localhost:8001`
- 管理端：`http://localhost:8002`

题目数据和真实岗位信息有意未包含在内。搜索 `TODO(EXAM-BANK)` 可查看待完成的数据集成点。

## 许可证

AGPL-3.0。详见 `LICENSE`。
