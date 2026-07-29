package com.mindskip.xzs.service;

import com.mindskip.xzs.viewmodel.admin.question.QuestionEditItemVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditRequestVM;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class QuestionDuplicateCheckerTest {
    @Test
    public void treatsEquivalentQuestionContentAsDuplicate() {
        QuestionEditRequestVM first = createQuestion("安全操作前应做什么？", "A");
        QuestionEditRequestVM second = createQuestion("  安全操作前应做什么？ ", "a");

        assertEquals(QuestionDuplicateChecker.fingerprint(first),
                QuestionDuplicateChecker.fingerprint(second));
    }

    @Test
    public void keepsQuestionsWithDifferentAnswers() {
        QuestionEditRequestVM first = createQuestion("安全操作前应做什么？", "A");
        QuestionEditRequestVM second = createQuestion("安全操作前应做什么？", "B");

        assertNotEquals(QuestionDuplicateChecker.fingerprint(first),
                QuestionDuplicateChecker.fingerprint(second));
    }

    private QuestionEditRequestVM createQuestion(String title, String correct) {
        QuestionEditRequestVM question = new QuestionEditRequestVM();
        question.setSubjectId(1);
        question.setBankType(1);
        question.setPositionId(2);
        question.setQuestionType(1);
        question.setTitle(title);
        question.setAnalyze("正确答案：" + correct.toUpperCase());
        question.setCorrect(correct);
        question.setItems(Arrays.asList(createItem("A", "验电"), createItem("B", "直接操作")));
        return question;
    }

    private QuestionEditItemVM createItem(String prefix, String content) {
        QuestionEditItemVM item = new QuestionEditItemVM();
        item.setPrefix(prefix);
        item.setContent(content);
        return item;
    }
}
