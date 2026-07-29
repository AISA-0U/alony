package com.mindskip.xzs.service;

import com.mindskip.xzs.viewmodel.admin.question.QuestionDocxPreviewVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditRequestVM;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QuestionDocxParserTest {
    private final QuestionDocxParser parser = new QuestionDocxParser();

    @Test
    public void parsesSupportedQuestionTypesFromMemory() throws IOException {
        byte[] document = createDocument(true);

        QuestionDocxPreviewVM preview = parser.parse(document, "题库.docx", 1, 1, 2, 3);

        assertEquals(4, preview.getTotalCount());
        assertEquals(1, preview.getSingleChoiceCount());
        assertEquals(1, preview.getMultipleChoiceCount());
        assertEquals(1, preview.getTrueFalseCount());
        assertEquals(1, preview.getShortAnswerCount());

        QuestionEditRequestVM singleChoice = preview.getQuestions().get(0);
        assertEquals("低压电器是指交流电压在（）及以下的电器。", singleChoice.getTitle());
        assertEquals("C", singleChoice.getCorrect());
        assertEquals(4, singleChoice.getItems().size());

        QuestionEditRequestVM multipleChoice = preview.getQuestions().get(1);
        assertEquals(2, multipleChoice.getCorrectArray().size());
        assertEquals("A", multipleChoice.getCorrectArray().get(0));
        assertEquals("C", multipleChoice.getCorrectArray().get(1));

        assertEquals("A", preview.getQuestions().get(2).getCorrect());
        assertEquals("先验电，再操作。", preview.getQuestions().get(3).getCorrect());
    }

    @Test
    public void rejectsMultipleChoiceWithoutMarkedAnswer() throws IOException {
        byte[] document = createDocument(false);

        try {
            parser.parse(document, "题库.docx", 1, 1, 2, 3);
            fail("Expected QuestionDocxParseException");
        } catch (QuestionDocxParseException expected) {
            assertEquals(true, expected.getMessage().contains("✅"));
        }
    }

    @Test
    public void parsesDocumentWhenMiddleQuestionTypeIsMissing() throws IOException {
        byte[] document = createDocument(
                "一、单选题",
                "1. 安全电压为（A）。",
                "A. 36V B. 220V",
                "三、判断题",
                "1. 操作前应验电（√）",
                "四、简答题",
                "1. 简述操作步骤。",
                "答：先验电，再操作。"
        );

        QuestionDocxPreviewVM preview = parser.parse(document, "缺少多选题.docx", 1, 1, 2, 3);

        assertEquals(3, preview.getTotalCount());
        assertEquals(1, preview.getSingleChoiceCount());
        assertEquals(0, preview.getMultipleChoiceCount());
        assertEquals(1, preview.getTrueFalseCount());
        assertEquals(1, preview.getShortAnswerCount());
    }

    @Test
    public void parsesDocumentWithOnlyOneQuestionType() throws IOException {
        byte[] document = createDocument(
                "三、判断题",
                "1. 操作前应验电（√）"
        );

        QuestionDocxPreviewVM preview = parser.parse(document, "只有判断题.docx", 1, 1, 2, 3);

        assertEquals(1, preview.getTotalCount());
        assertEquals(0, preview.getSingleChoiceCount());
        assertEquals(0, preview.getMultipleChoiceCount());
        assertEquals(1, preview.getTrueFalseCount());
        assertEquals(0, preview.getShortAnswerCount());
    }

    private byte[] createDocument(boolean markMultipleAnswers) throws IOException {
        return createDocument(
                "一、单选题",
                "1. 低压电器是指交流电压在（C）及以下的电器。",
                "A. 400V B. 690V C. 1000V D. 1500V",
                "二、多选题",
                "1. 下列属于安全用具的是（ ）",
                (markMultipleAnswers ? "✅" : "")
                        + "A. 绝缘手套 B. 普通棉布 "
                        + (markMultipleAnswers ? "✅" : "") + "C. 验电器 D. 纸箱",
                "三、判断题",
                "1. 装配前应先验电（√）",
                "四、简答题",
                "1. 简述安全操作步骤。",
                "答：先验电，再操作。"
        );
    }

    private byte[] createDocument(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                addParagraph(document, paragraph);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private void addParagraph(XWPFDocument document, String text) {
        document.createParagraph().createRun().setText(text);
    }
}
