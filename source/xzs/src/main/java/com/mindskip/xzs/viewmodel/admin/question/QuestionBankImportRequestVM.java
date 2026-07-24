package com.mindskip.xzs.viewmodel.admin.question;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

public class QuestionBankImportRequestVM {
    /** Client supplied batch identifier for audit/idempotency integration. */
    private String batchNo;

    @Valid
    @NotEmpty
    private List<QuestionEditRequestVM> questions;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public List<QuestionEditRequestVM> getQuestions() { return questions; }
    public void setQuestions(List<QuestionEditRequestVM> questions) { this.questions = questions; }
}
