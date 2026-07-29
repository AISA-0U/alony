# Word question-bank import

This converter reads the fixed question-paper layout used by the supplied
`.docx` file and creates the JSON accepted by
`POST /api/admin/question/bank/import`.

## Isolated environment

```powershell
python -m venv .venv-question-import
.\.venv-question-import\Scripts\python.exe -m pip install -r scripts\question-bank\requirements.txt
```

## Convert

Create the subject and job position first, then pass their database IDs:

```powershell
.\.venv-question-import\Scripts\python.exe scripts\question-bank\docx_to_import_json.py `
  "C:\path\question-paper.docx" `
  --output "D:\testvue\question-import\questions.json" `
  --subject-id 1 `
  --position-id 1 `
  --bank-type 1 `
  --difficult 2 `
  --batch-no "job-set-1"
```

The command fails without writing a valid import package when question counts,
answer markers, option labels, answer membership, ordering, or duplicate titles
do not pass validation. It normalizes every imported question to the system
rule of one point per question.

The converter does not infer question-bank categories from wording. Use
`--bank-type 1` for position questions or `--bank-type 2` for safety questions.
Professional-ethics questions require a separate import because that category
must omit `positionId`.
