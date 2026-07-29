package com.mindskip.xzs.service;

import com.mindskip.xzs.viewmodel.admin.question.QuestionEditItemVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditRequestVM;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class QuestionDuplicateChecker {
    private QuestionDuplicateChecker() {
    }

    public static String fingerprint(QuestionEditRequestVM question) {
        StringBuilder fingerprint = new StringBuilder();
        append(fingerprint, question.getSubjectId());
        append(fingerprint, question.getBankType());
        append(fingerprint, question.getPositionId());
        append(fingerprint, question.getQuestionType());
        append(fingerprint, question.getTitle());
        append(fingerprint, question.getAnalyze());
        append(fingerprint, question.getCorrect());

        List<String> correctAnswers = question.getCorrectArray() == null
                ? new ArrayList<>() : new ArrayList<>(question.getCorrectArray());
        correctAnswers.replaceAll(QuestionDuplicateChecker::normalize);
        Collections.sort(correctAnswers);
        for (String correctAnswer : correctAnswers) {
            append(fingerprint, correctAnswer);
        }

        if (question.getItems() != null) {
            for (QuestionEditItemVM item : question.getItems()) {
                append(fingerprint, item.getPrefix());
                append(fingerprint, item.getContent());
            }
        }
        return fingerprint.toString();
    }

    private static void append(StringBuilder fingerprint, Object value) {
        String normalized = normalize(value == null ? "" : String.valueOf(value));
        fingerprint.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
