package com.mindskip.xzs.controller.admin;

import com.mindskip.xzs.base.BaseApiController;
import com.mindskip.xzs.base.RestResponse;
import com.mindskip.xzs.base.SystemCode;
import com.mindskip.xzs.domain.Question;
import com.mindskip.xzs.domain.TextContent;
import com.mindskip.xzs.domain.enums.QuestionTypeEnum;
import com.mindskip.xzs.domain.enums.QuestionBankTypeEnum;
import com.mindskip.xzs.domain.question.QuestionObject;
import com.mindskip.xzs.service.QuestionService;
import com.mindskip.xzs.service.QuestionDocxParseException;
import com.mindskip.xzs.service.QuestionDocxParser;
import com.mindskip.xzs.service.TextContentService;
import com.mindskip.xzs.utility.*;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditRequestVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionPageRequestVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionResponseVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionBankImportRequestVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionBatchRequestVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionDocxPreviewVM;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@RestController("AdminQuestionController")
@RequestMapping(value = "/api/admin/question")
public class QuestionController extends BaseApiController {

    private final QuestionService questionService;
    private final TextContentService textContentService;
    private final QuestionDocxParser questionDocxParser;

    @Autowired
    public QuestionController(QuestionService questionService, TextContentService textContentService,
                              QuestionDocxParser questionDocxParser) {
        this.questionService = questionService;
        this.textContentService = textContentService;
        this.questionDocxParser = questionDocxParser;
    }

    @RequestMapping(value = "/page", method = RequestMethod.POST)
    public RestResponse<PageInfo<QuestionResponseVM>> pageList(@RequestBody QuestionPageRequestVM model) {
        PageInfo<Question> pageInfo = questionService.page(model);
        PageInfo<QuestionResponseVM> page = PageInfoHelper.copyMap(pageInfo, q -> {
            QuestionResponseVM vm = modelMapper.map(q, QuestionResponseVM.class);
            vm.setCreateTime(DateTimeUtil.dateFormat(q.getCreateTime()));
            vm.setScore(ExamUtil.scoreToVM(q.getScore()));
            TextContent textContent = textContentService.selectById(q.getInfoTextContentId());
            QuestionObject questionObject = JsonUtil.toJsonObject(textContent.getContent(), QuestionObject.class);
            String clearHtml = HtmlUtil.clear(questionObject.getTitleContent());
            vm.setShortTitle(clearHtml);
            return vm;
        });
        return RestResponse.ok(page);
    }

    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public RestResponse edit(@RequestBody @Valid QuestionEditRequestVM model) {
        RestResponse validQuestionEditRequestResult = validQuestionEditRequestVM(model);
        if (validQuestionEditRequestResult.getCode() != SystemCode.OK.getCode()) {
            return validQuestionEditRequestResult;
        }

        if (null == model.getId()) {
            questionService.insertFullQuestion(model, getCurrentUser().getId());
        } else {
            questionService.updateFullQuestion(model);
        }

        return RestResponse.ok();
    }

    /**
     * Standard JSON import contract. TODO(EXAM-BANK): connect the future Excel/CSV adapter
     * to this endpoint after mapping each row to QuestionEditRequestVM.
     */
    @PostMapping("/bank/import")
    public RestResponse<Integer> importBank(@RequestBody @Valid QuestionBankImportRequestVM model) {
        for (QuestionEditRequestVM question : model.getQuestions()) {
            RestResponse validation = validQuestionEditRequestVM(question);
            if (validation.getCode() != SystemCode.OK.getCode()) {
                return validation;
            }
        }
        List<QuestionEditRequestVM> newQuestions = questionService.filterNewQuestions(model.getQuestions());
        return RestResponse.ok(questionService.importQuestions(newQuestions, getCurrentUser().getId()));
    }

    @PostMapping(value = "/bank/docx/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestResponse<QuestionDocxPreviewVM> previewDocx(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subjectId") Integer subjectId,
            @RequestParam("bankType") Integer bankType,
            @RequestParam(value = "positionId", required = false) Integer positionId,
            @RequestParam("difficult") Integer difficult) {
        if (file == null || file.isEmpty()) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "请选择要导入的 Word 文件");
        }
        String fileName = StringUtils.defaultString(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "仅支持 .docx 格式的 Word 文件");
        }
        if (file.getSize() > 5L * 1024L * 1024L) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "Word 文件不能超过 5MB");
        }
        if (subjectId == null) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "请选择科目");
        }
        QuestionBankTypeEnum bankTypeEnum = QuestionBankTypeEnum.fromCode(bankType);
        if (bankTypeEnum == null) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "请选择有效的题库类别");
        }
        if (bankTypeEnum != QuestionBankTypeEnum.PROFESSIONAL_ETHICS && positionId == null) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "职位类和安全类题库必须选择职位");
        }
        if (bankTypeEnum == QuestionBankTypeEnum.PROFESSIONAL_ETHICS) {
            positionId = null;
        }
        if (difficult == null || difficult < 1 || difficult > 5) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "难度必须在 1 到 5 之间");
        }

        try {
            QuestionDocxPreviewVM preview = questionDocxParser.parse(file.getBytes(), fileName,
                    subjectId, bankType, positionId, difficult);
            int parsedCount = preview.getQuestions().size();
            List<QuestionEditRequestVM> newQuestions = questionService.filterNewQuestions(preview.getQuestions());
            int duplicateCount = parsedCount - newQuestions.size();
            updatePreviewQuestions(preview, newQuestions);
            if (duplicateCount > 0) {
                preview.getWarnings().add("已自动跳过 " + duplicateCount + " 道完全重复的题目");
            }
            return RestResponse.ok(preview);
        } catch (QuestionDocxParseException exception) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), exception.getMessage());
        } catch (IOException exception) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "读取 Word 文件失败");
        }
    }

    @RequestMapping(value = "/select/{id}", method = RequestMethod.POST)
    public RestResponse<QuestionEditRequestVM> select(@PathVariable Integer id) {
        QuestionEditRequestVM newVM = questionService.getQuestionEditRequestVM(id);
        return RestResponse.ok(newVM);
    }


    @RequestMapping(value = "/delete/{id}", method = RequestMethod.POST)
    public RestResponse delete(@PathVariable Integer id) {
        Question question = questionService.selectById(id);
        question.setDeleted(true);
        questionService.updateByIdFilter(question);
        return RestResponse.ok();
    }

    @PostMapping("/batch/delete")
    public RestResponse<Integer> batchDelete(@RequestBody @Valid QuestionBatchRequestVM model) {
        return RestResponse.ok(questionService.softDeleteQuestions(model.getIds()));
    }

    private void updatePreviewQuestions(QuestionDocxPreviewVM preview,
                                        List<QuestionEditRequestVM> questions) {
        preview.setQuestions(questions);
        preview.setTotalCount(questions.size());
        preview.setSingleChoiceCount(countQuestions(questions, QuestionTypeEnum.SingleChoice.getCode()));
        preview.setMultipleChoiceCount(countQuestions(questions, QuestionTypeEnum.MultipleChoice.getCode()));
        preview.setTrueFalseCount(countQuestions(questions, QuestionTypeEnum.TrueFalse.getCode()));
        preview.setShortAnswerCount(countQuestions(questions, QuestionTypeEnum.ShortAnswer.getCode()));
    }

    private int countQuestions(List<QuestionEditRequestVM> questions, int questionType) {
        int count = 0;
        for (QuestionEditRequestVM question : questions) {
            if (question.getQuestionType() == questionType) {
                count++;
            }
        }
        return count;
    }

    private RestResponse validQuestionEditRequestVM(QuestionEditRequestVM model) {
        model.setScore("1");
        QuestionBankTypeEnum bankType = QuestionBankTypeEnum.fromCode(model.getBankType());
        if (bankType == null) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "Invalid question bank type");
        }
        if (bankType != QuestionBankTypeEnum.PROFESSIONAL_ETHICS && model.getPositionId() == null) {
            return RestResponse.fail(SystemCode.ParameterValidError.getCode(), "Position is required for position and safety questions");
        }
        if (bankType == QuestionBankTypeEnum.PROFESSIONAL_ETHICS) {
            model.setPositionId(null);
        }
        int qType = model.getQuestionType().intValue();
        boolean requireCorrect = qType == QuestionTypeEnum.SingleChoice.getCode() || qType == QuestionTypeEnum.TrueFalse.getCode();
        if (requireCorrect) {
            if (StringUtils.isBlank(model.getCorrect())) {
                String errorMsg = ErrorUtil.parameterErrorFormat("correct", "不能为空");
                return new RestResponse<>(SystemCode.ParameterValidError.getCode(), errorMsg);
            }
        }

        if (qType == QuestionTypeEnum.GapFilling.getCode()) {
            model.setScore("1");
            if (model.getItems() != null && !model.getItems().isEmpty()) {
                for (int i = 0; i < model.getItems().size(); i++) {
                    model.getItems().get(i).setScore(i == 0 ? "1" : "0");
                }
            }
            Integer fillSumScore = model.getItems().stream().mapToInt(d -> ExamUtil.scoreFromVM(d.getScore())).sum();
            Integer questionScore = ExamUtil.scoreFromVM(model.getScore());
            if (!fillSumScore.equals(questionScore)) {
                String errorMsg = ErrorUtil.parameterErrorFormat("score", "空分数和与题目总分不相等");
                return new RestResponse<>(SystemCode.ParameterValidError.getCode(), errorMsg);
            }
        }
        return RestResponse.ok();
    }
}
