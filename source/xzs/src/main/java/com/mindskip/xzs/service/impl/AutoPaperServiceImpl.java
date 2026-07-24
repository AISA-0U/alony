package com.mindskip.xzs.service.impl;

import com.mindskip.xzs.domain.ExamPaper;
import com.mindskip.xzs.domain.Question;
import com.mindskip.xzs.domain.User;
import com.mindskip.xzs.domain.enums.QuestionBankTypeEnum;
import com.mindskip.xzs.domain.enums.QuestionTypeEnum;
import com.mindskip.xzs.exception.BusinessException;
import com.mindskip.xzs.repository.QuestionMapper;
import com.mindskip.xzs.service.AutoPaperService;
import com.mindskip.xzs.service.ExamPaperService;
import com.mindskip.xzs.service.QuestionService;
import com.mindskip.xzs.viewmodel.admin.exam.AutoPaperGenerateRequestVM;
import com.mindskip.xzs.viewmodel.admin.exam.ExamPaperEditRequestVM;
import com.mindskip.xzs.viewmodel.admin.exam.ExamPaperTitleItemVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditRequestVM;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AutoPaperServiceImpl implements AutoPaperService {
    private final QuestionMapper questionMapper;
    private final QuestionService questionService;
    private final ExamPaperService examPaperService;

    public AutoPaperServiceImpl(QuestionMapper questionMapper, QuestionService questionService,
                                ExamPaperService examPaperService) {
        this.questionMapper = questionMapper;
        this.questionService = questionService;
        this.examPaperService = examPaperService;
    }

    @Override
    @Transactional
    public ExamPaperEditRequestVM generate(AutoPaperGenerateRequestVM request, User user) {
        validateRatios(request);
        ExamPaperEditRequestVM paper = new ExamPaperEditRequestVM();
        paper.setName(request.getName());
        paper.setSubjectId(request.getSubjectId());
        paper.setPaperType(request.getPaperType());
        paper.setSuggestTime(request.getSuggestTime());
        paper.setTitleItems(new ArrayList<>());

        int[][] allocation = AutoPaperAllocation.allocate(
                new int[]{request.getPositionCount(), request.getSafetyCount(), request.getEthicsCount()},
                new int[]{request.getChoiceCount(), request.getGapCount(), request.getTrueFalseCount(),
                        request.getShortAnswerCount()}, request.getTotalCount());
        addBank(paper, "Position questions", QuestionBankTypeEnum.POSITION, request.getPositionId(),
                request.getSubjectId(), allocation[0]);
        addBank(paper, "Safety questions", QuestionBankTypeEnum.SAFETY, request.getPositionId(),
                request.getSubjectId(), allocation[1]);
        addBank(paper, "Professional ethics questions", QuestionBankTypeEnum.PROFESSIONAL_ETHICS, null,
                request.getSubjectId(), allocation[2]);

        // The generated model uses the existing save path, so it remains manually editable.
        ExamPaper saved = examPaperService.savePaperFromVM(paper, user);
        return examPaperService.examPaperToVM(saved.getId());
    }

    private void addBank(ExamPaperEditRequestVM paper, String bankName, QuestionBankTypeEnum bankType,
                         Integer positionId, Integer subjectId, int[] counts) {
        addGroup(paper, bankName + " - Choice", bankType, positionId, subjectId,
                Arrays.asList(QuestionTypeEnum.SingleChoice.getCode(), QuestionTypeEnum.MultipleChoice.getCode()), counts[0]);
        addGroup(paper, bankName + " - Gap filling", bankType, positionId, subjectId,
                Arrays.asList(QuestionTypeEnum.GapFilling.getCode()), counts[1]);
        addGroup(paper, bankName + " - True/false", bankType, positionId, subjectId,
                Arrays.asList(QuestionTypeEnum.TrueFalse.getCode()), counts[2]);
        addGroup(paper, bankName + " - Short answer", bankType, positionId, subjectId,
                Arrays.asList(QuestionTypeEnum.ShortAnswer.getCode()), counts[3]);
    }

    private void addGroup(ExamPaperEditRequestVM paper, String title, QuestionBankTypeEnum bankType,
                          Integer positionId, Integer subjectId, List<Integer> types, int required) {
        if (required == 0) return;
        List<Question> questions = questionMapper.selectRandomForPaper(
                bankType.getCode(), positionId, subjectId, types, required);
        if (questions.size() < required) {
            throw new BusinessException("Question bank shortage: " + title + ", required=" + required
                    + ", available=" + questions.size() + ". TODO(EXAM-BANK): import missing questions.");
        }
        ExamPaperTitleItemVM titleItem = new ExamPaperTitleItemVM();
        titleItem.setName(title);
        List<QuestionEditRequestVM> items = new ArrayList<>();
        for (Question question : questions) {
            QuestionEditRequestVM item = questionService.getQuestionEditRequestVM(question);
            item.setScore("1");
            items.add(item);
        }
        titleItem.setQuestionItems(items);
        paper.getTitleItems().add(titleItem);
    }

    private void validateRatios(AutoPaperGenerateRequestVM request) {
        if (request.getTotalCount() != 100) {
            throw new BusinessException("An exam paper must contain exactly 100 questions");
        }
        if (request.getPositionCount() + request.getSafetyCount() + request.getEthicsCount()
                != request.getTotalCount()) {
            throw new BusinessException("Bank counts must equal totalCount");
        }
        if (request.getChoiceCount() + request.getGapCount() + request.getTrueFalseCount()
                + request.getShortAnswerCount() != request.getTotalCount()) {
            throw new BusinessException("Question type counts must equal totalCount");
        }
    }
}
