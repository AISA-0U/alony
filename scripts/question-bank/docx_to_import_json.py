#!/usr/bin/env python3
import argparse
import json
import logging
import re
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple

from docx import Document


LOGGER = logging.getLogger("docx-question-import")
OPTION_PATTERN = re.compile(
    r"(?:(✅)\s*)?([A-E])[.．、]\s*(.*?)"
    r"(?=(?:✅\s*)?[A-E][.．、]|$)",
    re.DOTALL,
)
SINGLE_ANSWER_PATTERNS = (
    re.compile(r"（\s*([A-E])\s*）"),
    re.compile(r"_\s*([A-E])\s*_"),
)
EXPECTED_COUNTS = {1: 30, 2: 10, 3: 10, 5: 2}


class ParseError(ValueError):
    pass


@dataclass(frozen=True)
class ImportContext:
    subject_id: int
    position_id: int
    bank_type: int
    difficult: int


def compact_spaces(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def non_empty_paragraphs(document_path: Path) -> List[str]:
    try:
        document = Document(str(document_path))
    except Exception as exc:
        raise ParseError(f"无法读取 Word 文件: {exc}") from exc
    return [compact_spaces(paragraph.text) for paragraph in document.paragraphs if paragraph.text.strip()]


def find_heading(paragraphs: Sequence[str], prefix: str) -> int:
    for index, paragraph in enumerate(paragraphs):
        if paragraph.startswith(prefix):
            return index
    raise ParseError(f"未找到题型标题: {prefix}")


def split_sections(paragraphs: Sequence[str]) -> Dict[int, List[str]]:
    single_index = find_heading(paragraphs, "一、单选题")
    multiple_index = find_heading(paragraphs, "二、多选题")
    true_false_index = find_heading(paragraphs, "判断题")
    short_answer_index = find_heading(paragraphs, "四、简答题")
    if not (single_index < multiple_index < true_false_index < short_answer_index):
        raise ParseError("题型顺序不符合预期")
    return {
        1: list(paragraphs[single_index + 1 : multiple_index]),
        2: list(paragraphs[multiple_index + 1 : true_false_index]),
        3: list(paragraphs[true_false_index + 1 : short_answer_index]),
        5: list(paragraphs[short_answer_index + 1 :]),
    }


def parse_options(raw_text: str, allow_missing_a: bool = False) -> Tuple[List[dict], List[str]]:
    normalized = compact_spaces(raw_text)
    if allow_missing_a and not re.match(r"^A[.．、]", normalized):
        normalized = f"A. {normalized}"
    matches = list(OPTION_PATTERN.finditer(normalized))
    if not matches:
        raise ParseError(f"无法解析选项: {raw_text}")

    items = []
    correct = []
    for match in matches:
        marker, prefix, content = match.groups()
        content = compact_spaces(content)
        if not content:
            raise ParseError(f"选项 {prefix} 内容为空: {raw_text}")
        items.append({"prefix": prefix, "content": content})
        if marker:
            correct.append(prefix)

    labels = [item["prefix"] for item in items]
    expected_labels = [chr(ord("A") + index) for index in range(len(items))]
    if labels != expected_labels:
        raise ParseError(f"选项标签不连续: {labels}, 原文: {raw_text}")
    return items, correct


def base_question(
    context: ImportContext,
    question_type: int,
    title: str,
    items: List[dict],
    correct: str,
    correct_array: List[str],
    analyze: str,
    item_order: int,
) -> dict:
    return {
        "questionType": question_type,
        "subjectId": context.subject_id,
        "bankType": context.bank_type,
        "positionId": context.position_id,
        "title": title,
        "items": items,
        "analyze": analyze,
        "correctArray": correct_array,
        "correct": correct,
        "score": "1",
        "difficult": context.difficult,
        "itemOrder": item_order,
    }


def parse_single_choice(lines: Sequence[str], context: ImportContext, start_order: int) -> List[dict]:
    if len(lines) % 2 != 0:
        raise ParseError(f"单选题题干与选项未成对，共 {len(lines)} 段")
    questions = []
    for offset in range(0, len(lines), 2):
        raw_title = lines[offset]
        answer = None
        title = raw_title
        for pattern in SINGLE_ANSWER_PATTERNS:
            match = pattern.search(raw_title)
            if match:
                answer = match.group(1)
                if pattern.pattern.startswith("（"):
                    title = pattern.sub("（）", raw_title, count=1)
                else:
                    title = pattern.sub("____", raw_title, count=1)
                break
        if answer is None:
            raise ParseError(f"单选题未标注答案: {raw_title}")
        items, _ = parse_options(lines[offset + 1], allow_missing_a=True)
        labels = {item["prefix"] for item in items}
        if answer not in labels:
            raise ParseError(f"答案 {answer} 不在选项中: {raw_title}")
        questions.append(
            base_question(
                context,
                1,
                title,
                items,
                answer,
                [],
                f"正确答案：{answer}",
                start_order + len(questions),
            )
        )
    return questions


def parse_multiple_choice(lines: Sequence[str], context: ImportContext, start_order: int) -> List[dict]:
    questions = []
    index = 0
    while index < len(lines):
        title = lines[index]
        index += 1
        option_lines = []
        while index < len(lines) and not lines[index].endswith("（）"):
            option_lines.append(lines[index])
            index += 1
        if not option_lines:
            raise ParseError(f"多选题缺少选项: {title}")
        items, correct = parse_options(" ".join(option_lines))
        if not correct:
            raise ParseError(f"多选题未使用 ✅ 标注答案: {title}")
        questions.append(
            base_question(
                context,
                2,
                title,
                items,
                "",
                correct,
                f"正确答案：{'、'.join(correct)}",
                start_order + len(questions),
            )
        )
    return questions


def parse_true_false(lines: Sequence[str], context: ImportContext, start_order: int) -> List[dict]:
    questions = []
    pattern = re.compile(r"^(.*?)（\s*([√×])\s*）$")
    for line in lines:
        match = pattern.match(line)
        if not match:
            raise ParseError(f"判断题格式错误: {line}")
        title, symbol = match.groups()
        correct = "A" if symbol == "√" else "B"
        questions.append(
            base_question(
                context,
                3,
                f"{title}（）",
                [
                    {"prefix": "A", "content": "正确"},
                    {"prefix": "B", "content": "错误"},
                ],
                correct,
                [],
                f"正确答案：{'正确' if symbol == '√' else '错误'}",
                start_order + len(questions),
            )
        )
    return questions


def parse_short_answer(lines: Sequence[str], context: ImportContext, start_order: int) -> List[dict]:
    if len(lines) % 2 != 0:
        raise ParseError(f"简答题题干与答案未成对，共 {len(lines)} 段")
    questions = []
    for offset in range(0, len(lines), 2):
        title = lines[offset]
        answer_line = lines[offset + 1]
        if not answer_line.startswith("答："):
            raise ParseError(f"简答题缺少“答：”前缀: {title}")
        answer = answer_line.removeprefix("答：").strip()
        questions.append(
            base_question(
                context,
                5,
                title,
                [],
                answer,
                [],
                answer,
                start_order + len(questions),
            )
        )
    return questions


def validate_questions(questions: Sequence[dict]) -> None:
    counts = {question_type: 0 for question_type in EXPECTED_COUNTS}
    titles = set()
    for index, question in enumerate(questions, start=1):
        question_type = question["questionType"]
        counts[question_type] = counts.get(question_type, 0) + 1
        title = question["title"]
        if title in titles:
            raise ParseError(f"发现重复题干: {title}")
        titles.add(title)
        if question["itemOrder"] != index:
            raise ParseError(f"题目顺序不连续: {question['itemOrder']} != {index}")
        if question_type in (1, 3) and not question["correct"]:
            raise ParseError(f"第 {index} 题缺少唯一答案")
        if question_type == 2 and not question["correctArray"]:
            raise ParseError(f"第 {index} 题缺少多选答案")
        if question_type == 5 and not question["correct"]:
            raise ParseError(f"第 {index} 题缺少参考答案")
    if counts != EXPECTED_COUNTS:
        raise ParseError(f"题型数量不符合预期: {counts}, 预期: {EXPECTED_COUNTS}")


def build_payload(document_path: Path, context: ImportContext, batch_no: str) -> dict:
    paragraphs = non_empty_paragraphs(document_path)
    sections = split_sections(paragraphs)
    questions = []
    questions.extend(parse_single_choice(sections[1], context, len(questions) + 1))
    questions.extend(parse_multiple_choice(sections[2], context, len(questions) + 1))
    questions.extend(parse_true_false(sections[3], context, len(questions) + 1))
    questions.extend(parse_short_answer(sections[5], context, len(questions) + 1))
    validate_questions(questions)
    return {"batchNo": batch_no, "questions": questions}


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="将固定格式的 Word 题库转换为 XZS 批量导入 JSON")
    parser.add_argument("document", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--subject-id", type=int, required=True)
    parser.add_argument("--position-id", type=int, required=True)
    parser.add_argument("--bank-type", type=int, choices=(1, 2), default=1)
    parser.add_argument("--difficult", type=int, choices=range(1, 6), default=2)
    parser.add_argument("--batch-no")
    return parser.parse_args(list(argv))


def main(argv: Iterable[str] = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    args = parse_args(sys.argv[1:] if argv is None else argv)
    if not args.document.is_file():
        LOGGER.error("Word 文件不存在: %s", args.document)
        return 2
    batch_no = args.batch_no or f"docx-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
    context = ImportContext(
        subject_id=args.subject_id,
        position_id=args.position_id,
        bank_type=args.bank_type,
        difficult=args.difficult,
    )
    try:
        payload = build_payload(args.document, context, batch_no)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("w", encoding="utf-8", newline="\n") as output_file:
            json.dump(payload, output_file, ensure_ascii=False, indent=2)
            output_file.write("\n")
    except (OSError, ParseError) as exc:
        LOGGER.error("转换失败: %s", exc)
        return 1
    LOGGER.info("已生成 %s，共 %d 道题", args.output, len(payload["questions"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
