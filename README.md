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
