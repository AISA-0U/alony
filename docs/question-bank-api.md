# Question bank integration contract

Apply `database/migration/V4_0_0__job_question_bank.sql` before using these APIs.

## Positions

- `POST /api/admin/job-position/list`
- `POST /api/admin/job-position/save`

Save body example: `{"code":"electrician","name":"Electrician","itemOrder":10,"enabled":true}`.

## Bulk question import

`POST /api/admin/question/bank/import`

The request body contains `batchNo` and a non-empty `questions` array. Each item uses the same
contract as `/api/admin/question/edit`, plus:

- `bankType`: `1` position, `2` safety, `3` common professional ethics.
- `positionId`: required for types `1` and `2`; omitted for type `3`.
- `questionType`: `1` single choice, `2` multiple choice, `3` true/false,
  `4` gap filling, `5` short answer.

The whole batch is transactional. Any invalid item rolls back the batch.
The backend enforces a score of `1` for every imported or edited question.

TODO(EXAM-BANK): implement an Excel/CSV adapter after the source column layout is known. The adapter
must map rows to this JSON contract instead of bypassing service validation.

TODO(EXAM-BANK): seed real job positions and subjects before the first production import. No sample
question records are included in this repository.

## Automatic paper generation

`POST /api/admin/exam/paper/auto-generate`

Default body:

```json
{
  "name": "Electrician assessment",
  "positionId": 1,
  "subjectId": 1,
  "paperType": 1,
  "suggestTime": 60,
  "totalCount": 100,
  "positionCount": 80,
  "safetyCount": 10,
  "ethicsCount": 10,
  "choiceCount": 30,
  "gapCount": 20,
  "trueFalseCount": 40,
  "shortAnswerCount": 10
}
```

The endpoint creates a normal editable paper. If any bank/type bucket is short, the transaction is
rolled back and the response identifies the missing bucket.

Current mandatory business rule: each paper has exactly `100` questions, each question is worth
`1` point, and the default type counts are choice `30`, gap filling `20`, true/false `40`, and
short answer `10`. These are exact question counts, not score percentages.
