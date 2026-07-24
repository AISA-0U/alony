package com.mindskip.xzs.service;

import com.mindskip.xzs.domain.User;
import com.mindskip.xzs.viewmodel.admin.exam.AutoPaperGenerateRequestVM;
import com.mindskip.xzs.viewmodel.admin.exam.ExamPaperEditRequestVM;

public interface AutoPaperService {
    ExamPaperEditRequestVM generate(AutoPaperGenerateRequestVM request, User user);
}
