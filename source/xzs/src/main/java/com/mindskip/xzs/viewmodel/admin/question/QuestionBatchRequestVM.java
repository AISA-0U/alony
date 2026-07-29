package com.mindskip.xzs.viewmodel.admin.question;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

public class QuestionBatchRequestVM {
    @NotEmpty
    @Size(max = 500)
    private List<Integer> ids;

    public List<Integer> getIds() {
        return ids;
    }

    public void setIds(List<Integer> ids) {
        this.ids = ids;
    }
}
