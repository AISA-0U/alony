package com.mindskip.xzs.service;

import com.mindskip.xzs.domain.enums.QuestionTypeEnum;
import com.mindskip.xzs.viewmodel.admin.question.QuestionDocxPreviewVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditItemVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditRequestVM;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionDocxParser {
    private static final int MAX_QUESTION_COUNT = 500;
    private static final Pattern SINGLE_ANSWER = Pattern.compile("[（(]\\s*([A-E])\\s*[）)]|_+\\s*([A-E])\\s*_+");
    private static final Pattern TRUE_FALSE_ANSWER = Pattern.compile("^(.*?)[（(]\\s*([√×])\\s*[）)]\\s*$");
    private static final Pattern OPTION = Pattern.compile("(?:(✅)\\s*)?([A-E])[.．、]\\s*(.*?)(?=(?:✅\\s*)?[A-E][.．、]|$)");
    private static final Pattern BLANK_ANSWER = Pattern.compile("[（(]\\s*[）)]");
    private static final Pattern ANSWER_PREFIX = Pattern.compile("^答[：:]\\s*");
    private static final Pattern QUESTION_NUMBER = Pattern.compile("^\\s*\\d+[.．、]\\s*");

    public QuestionDocxPreviewVM parse(byte[] content, String fileName, Integer subjectId,
                                       Integer bankType, Integer positionId, Integer difficult) {
        if (content == null || content.length == 0) {
            throw new QuestionDocxParseException("Word 文件内容为空");
        }

        List<String> paragraphs = readParagraphs(content);
        List<QuestionEditRequestVM> questions = new ArrayList<>();
        List<QuestionSection> sections = findSections(paragraphs);
        for (int index = 0; index < sections.size(); index++) {
            QuestionSection section = sections.get(index);
            int end = index + 1 < sections.size() ? sections.get(index + 1).getHeadingIndex() : paragraphs.size();
            List<String> lines = paragraphs.subList(section.getHeadingIndex() + 1, end);
            questions.addAll(parseSection(section.getQuestionType(), lines,
                    subjectId, bankType, positionId, difficult));
        }
        validateQuestions(questions);

        String sourceHash = sha256(content);
        QuestionDocxPreviewVM preview = new QuestionDocxPreviewVM();
        preview.setFileName(fileName);
        preview.setSourceHash(sourceHash);
        preview.setBatchNo("docx-" + sourceHash.substring(0, 12));
        preview.setQuestions(questions);
        preview.setTotalCount(questions.size());
        preview.setSingleChoiceCount(countType(questions, QuestionTypeEnum.SingleChoice.getCode()));
        preview.setMultipleChoiceCount(countType(questions, QuestionTypeEnum.MultipleChoice.getCode()));
        preview.setTrueFalseCount(countType(questions, QuestionTypeEnum.TrueFalse.getCode()));
        preview.setShortAnswerCount(countType(questions, QuestionTypeEnum.ShortAnswer.getCode()));
        return preview;
    }

    private List<String> readParagraphs(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<String> paragraphs = new ArrayList<>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = compact(paragraph.getText());
                if (!text.isEmpty()) {
                    paragraphs.add(text);
                }
            }
            if (paragraphs.isEmpty()) {
                throw new QuestionDocxParseException("Word 文件中没有可读取的段落");
            }
            return paragraphs;
        } catch (QuestionDocxParseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new QuestionDocxParseException("无法读取 Word 文件，请确认文件为有效的 .docx 格式", exception);
        }
    }

    private List<QuestionSection> findSections(List<String> paragraphs) {
        List<QuestionSection> sections = new ArrayList<>();
        addSectionIfPresent(sections, paragraphs, "单选题", QuestionTypeEnum.SingleChoice.getCode());
        addSectionIfPresent(sections, paragraphs, "多选题", QuestionTypeEnum.MultipleChoice.getCode());
        addSectionIfPresent(sections, paragraphs, "判断题", QuestionTypeEnum.TrueFalse.getCode());
        addSectionIfPresent(sections, paragraphs, "简答题", QuestionTypeEnum.ShortAnswer.getCode());
        sections.sort(Comparator.comparingInt(QuestionSection::getHeadingIndex));
        return sections;
    }

    private void addSectionIfPresent(List<QuestionSection> sections, List<String> paragraphs,
                                     String heading, int questionType) {
        for (int index = 0; index < paragraphs.size(); index++) {
            if (paragraphs.get(index).contains(heading)) {
                sections.add(new QuestionSection(questionType, index));
                return;
            }
        }
    }

    private List<QuestionEditRequestVM> parseSection(int questionType, List<String> lines,
                                                      Integer subjectId, Integer bankType,
                                                      Integer positionId, Integer difficult) {
        if (questionType == QuestionTypeEnum.SingleChoice.getCode()) {
            return parseSingleChoice(lines, subjectId, bankType, positionId, difficult);
        }
        if (questionType == QuestionTypeEnum.MultipleChoice.getCode()) {
            return parseMultipleChoice(lines, subjectId, bankType, positionId, difficult);
        }
        if (questionType == QuestionTypeEnum.TrueFalse.getCode()) {
            return parseTrueFalse(lines, subjectId, bankType, positionId, difficult);
        }
        if (questionType == QuestionTypeEnum.ShortAnswer.getCode()) {
            return parseShortAnswer(lines, subjectId, bankType, positionId, difficult);
        }
        throw new IllegalArgumentException("不支持的题型：" + questionType);
    }

    private List<QuestionEditRequestVM> parseSingleChoice(List<String> lines, Integer subjectId,
                                                           Integer bankType, Integer positionId,
                                                           Integer difficult) {
        List<QuestionEditRequestVM> questions = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            String rawTitle = lines.get(index++);
            Matcher answerMatcher = SINGLE_ANSWER.matcher(rawTitle);
            if (!answerMatcher.find()) {
                throw new QuestionDocxParseException("单选题未标注答案：“" + rawTitle + "”");
            }
            String answer = answerMatcher.group(1) == null ? answerMatcher.group(2) : answerMatcher.group(1);
            String replacement = answerMatcher.group(1) == null ? "____" : "（）";
            String title = stripQuestionNumber(answerMatcher.replaceFirst(replacement));

            List<String> optionLines = new ArrayList<>();
            while (index < lines.size() && !SINGLE_ANSWER.matcher(lines.get(index)).find()) {
                optionLines.add(lines.get(index++));
            }
            List<QuestionEditItemVM> items = parseOptions(optionLines, true, false, title);
            ensureAnswerExists(answer, items, title);
            questions.add(buildQuestion(QuestionTypeEnum.SingleChoice.getCode(), title, items,
                    answer, new ArrayList<>(), "正确答案：" + answer,
                    subjectId, bankType, positionId, difficult));
        }
        return questions;
    }

    private List<QuestionEditRequestVM> parseMultipleChoice(List<String> lines, Integer subjectId,
                                                             Integer bankType, Integer positionId,
                                                             Integer difficult) {
        List<QuestionEditRequestVM> questions = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            String title = stripQuestionNumber(lines.get(index++));
            List<String> optionLines = new ArrayList<>();
            while (index < lines.size() && !isMultipleChoiceTitle(lines.get(index))) {
                optionLines.add(lines.get(index++));
            }
            List<String> correct = new ArrayList<>();
            List<QuestionEditItemVM> items = parseOptions(optionLines, false, true, title, correct);
            if (correct.isEmpty()) {
                throw new QuestionDocxParseException("多选题未使用“✅”标注答案：“" + title + "”");
            }
            questions.add(buildQuestion(QuestionTypeEnum.MultipleChoice.getCode(), title, items,
                    "", correct, "正确答案：" + String.join("、", correct),
                    subjectId, bankType, positionId, difficult));
        }
        return questions;
    }

    private boolean isMultipleChoiceTitle(String line) {
        return !startsWithOption(line) && BLANK_ANSWER.matcher(line).find();
    }

    private List<QuestionEditRequestVM> parseTrueFalse(List<String> lines, Integer subjectId,
                                                        Integer bankType, Integer positionId,
                                                        Integer difficult) {
        List<QuestionEditRequestVM> questions = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = TRUE_FALSE_ANSWER.matcher(line);
            if (!matcher.matches()) {
                throw new QuestionDocxParseException("判断题格式错误：“" + line + "”");
            }
            String symbol = matcher.group(2);
            String correct = "√".equals(symbol) ? "A" : "B";
            List<QuestionEditItemVM> items = Arrays.asList(
                    buildItem("A", "正确"),
                    buildItem("B", "错误")
            );
            questions.add(buildQuestion(QuestionTypeEnum.TrueFalse.getCode(),
                    stripQuestionNumber(matcher.group(1)) + "（）", items, correct,
                    new ArrayList<>(), "正确答案：" + ("A".equals(correct) ? "正确" : "错误"),
                    subjectId, bankType, positionId, difficult));
        }
        return questions;
    }

    private List<QuestionEditRequestVM> parseShortAnswer(List<String> lines, Integer subjectId,
                                                          Integer bankType, Integer positionId,
                                                          Integer difficult) {
        List<QuestionEditRequestVM> questions = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            String title = stripQuestionNumber(lines.get(index++));
            if (index >= lines.size() || !ANSWER_PREFIX.matcher(lines.get(index)).find()) {
                throw new QuestionDocxParseException("简答题缺少“答：”参考答案：“" + title + "”");
            }
            String answer = ANSWER_PREFIX.matcher(lines.get(index++)).replaceFirst("").trim();
            if (answer.isEmpty()) {
                throw new QuestionDocxParseException("简答题参考答案为空：“" + title + "”");
            }
            questions.add(buildQuestion(QuestionTypeEnum.ShortAnswer.getCode(), title,
                    new ArrayList<>(), answer, new ArrayList<>(), answer,
                    subjectId, bankType, positionId, difficult));
        }
        return questions;
    }

    private List<QuestionEditItemVM> parseOptions(List<String> optionLines, boolean allowMissingA,
                                                   boolean collectCorrect, String title) {
        return parseOptions(optionLines, allowMissingA, collectCorrect, title, new ArrayList<>());
    }

    private List<QuestionEditItemVM> parseOptions(List<String> optionLines, boolean allowMissingA,
                                                   boolean collectCorrect, String title,
                                                   List<String> correct) {
        if (optionLines.isEmpty()) {
            throw new QuestionDocxParseException("题目缺少选项：“" + title + "”");
        }
        String optionsText = compact(String.join(" ", optionLines));
        if (allowMissingA && !optionsText.matches("^(?:✅\\s*)?A[.．、].*")) {
            optionsText = "A. " + optionsText;
        }

        Matcher matcher = OPTION.matcher(optionsText);
        List<QuestionEditItemVM> items = new ArrayList<>();
        while (matcher.find()) {
            String prefix = matcher.group(2);
            String content = compact(matcher.group(3));
            boolean marked = matcher.group(1) != null || content.contains("✅");
            content = compact(content.replace("✅", ""));
            if (content.isEmpty()) {
                throw new QuestionDocxParseException("选项 " + prefix + " 内容为空：“" + title + "”");
            }
            items.add(buildItem(prefix, content));
            if (collectCorrect && marked) {
                correct.add(prefix);
            }
        }
        if (items.size() < 2) {
            throw new QuestionDocxParseException("无法解析题目选项：“" + title + "”");
        }
        for (int index = 0; index < items.size(); index++) {
            String expected = String.valueOf((char) ('A' + index));
            if (!expected.equals(items.get(index).getPrefix())) {
                throw new QuestionDocxParseException("选项标签不连续：“" + title + "”");
            }
        }
        return items;
    }

    private void ensureAnswerExists(String answer, List<QuestionEditItemVM> items, String title) {
        for (QuestionEditItemVM item : items) {
            if (answer.equals(item.getPrefix())) {
                return;
            }
        }
        throw new QuestionDocxParseException("答案 " + answer + " 不在选项中：“" + title + "”");
    }

    private QuestionEditRequestVM buildQuestion(Integer questionType, String title,
                                                 List<QuestionEditItemVM> items, String correct,
                                                 List<String> correctArray, String analyze,
                                                 Integer subjectId, Integer bankType,
                                                 Integer positionId, Integer difficult) {
        QuestionEditRequestVM question = new QuestionEditRequestVM();
        question.setQuestionType(questionType);
        question.setSubjectId(subjectId);
        question.setBankType(bankType);
        question.setPositionId(positionId);
        question.setTitle(title);
        question.setItems(items);
        question.setCorrect(correct);
        question.setCorrectArray(correctArray);
        question.setAnalyze(analyze);
        question.setScore("1");
        question.setDifficult(difficult);
        return question;
    }

    private QuestionEditItemVM buildItem(String prefix, String content) {
        QuestionEditItemVM item = new QuestionEditItemVM();
        item.setPrefix(prefix);
        item.setContent(content);
        item.setScore("0");
        item.setItemUuid(UUID.randomUUID().toString());
        return item;
    }

    private void validateQuestions(List<QuestionEditRequestVM> questions) {
        if (questions.isEmpty()) {
            throw new QuestionDocxParseException("Word 文件中没有解析到试题");
        }
        if (questions.size() > MAX_QUESTION_COUNT) {
            throw new QuestionDocxParseException("单次最多导入 " + MAX_QUESTION_COUNT + " 道题");
        }
    }

    private int countType(List<QuestionEditRequestVM> questions, int type) {
        int count = 0;
        for (QuestionEditRequestVM question : questions) {
            if (question.getQuestionType() == type) {
                count++;
            }
        }
        return count;
    }

    private boolean startsWithOption(String value) {
        return value.matches("^(?:✅\\s*)?[A-E][.．、].*");
    }

    private String stripQuestionNumber(String value) {
        return QUESTION_NUMBER.matcher(compact(value)).replaceFirst("");
    }

    private String compact(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class QuestionSection {
        private final int questionType;
        private final int headingIndex;

        private QuestionSection(int questionType, int headingIndex) {
            this.questionType = questionType;
            this.headingIndex = headingIndex;
        }

        private int getQuestionType() {
            return questionType;
        }

        private int getHeadingIndex() {
            return headingIndex;
        }
    }
}
