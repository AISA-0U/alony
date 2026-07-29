package com.mindskip.xzs.viewmodel.admin.question;

import java.util.ArrayList;
import java.util.List;

public class QuestionDocxPreviewVM {
    private String fileName;
    private String sourceHash;
    private String batchNo;
    private int totalCount;
    private int singleChoiceCount;
    private int multipleChoiceCount;
    private int trueFalseCount;
    private int shortAnswerCount;
    private List<String> warnings = new ArrayList<>();
    private List<QuestionEditRequestVM> questions = new ArrayList<>();

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getSingleChoiceCount() {
        return singleChoiceCount;
    }

    public void setSingleChoiceCount(int singleChoiceCount) {
        this.singleChoiceCount = singleChoiceCount;
    }

    public int getMultipleChoiceCount() {
        return multipleChoiceCount;
    }

    public void setMultipleChoiceCount(int multipleChoiceCount) {
        this.multipleChoiceCount = multipleChoiceCount;
    }

    public int getTrueFalseCount() {
        return trueFalseCount;
    }

    public void setTrueFalseCount(int trueFalseCount) {
        this.trueFalseCount = trueFalseCount;
    }

    public int getShortAnswerCount() {
        return shortAnswerCount;
    }

    public void setShortAnswerCount(int shortAnswerCount) {
        this.shortAnswerCount = shortAnswerCount;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<QuestionEditRequestVM> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionEditRequestVM> questions) {
        this.questions = questions;
    }
}
