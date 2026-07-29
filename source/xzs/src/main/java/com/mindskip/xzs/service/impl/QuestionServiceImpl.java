package com.mindskip.xzs.service.impl;

import com.mindskip.xzs.domain.other.KeyValue;
import com.mindskip.xzs.domain.Question;
import com.mindskip.xzs.domain.TextContent;
import com.mindskip.xzs.domain.enums.QuestionStatusEnum;
import com.mindskip.xzs.domain.enums.QuestionTypeEnum;
import com.mindskip.xzs.domain.question.QuestionItemObject;
import com.mindskip.xzs.domain.question.QuestionObject;
import com.mindskip.xzs.repository.QuestionMapper;
import com.mindskip.xzs.service.QuestionDuplicateChecker;
import com.mindskip.xzs.service.QuestionService;
import com.mindskip.xzs.service.SubjectService;
import com.mindskip.xzs.service.TextContentService;
import com.mindskip.xzs.utility.DateTimeUtil;
import com.mindskip.xzs.utility.JsonUtil;
import com.mindskip.xzs.utility.ModelMapperSingle;
import com.mindskip.xzs.utility.ExamUtil;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditItemVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionEditRequestVM;
import com.mindskip.xzs.viewmodel.admin.question.QuestionPageRequestVM;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl extends BaseServiceImpl<Question> implements QuestionService {

    protected final static ModelMapper modelMapper = ModelMapperSingle.Instance();
    private final QuestionMapper questionMapper;
    private final TextContentService textContentService;
    private final SubjectService subjectService;

    @Autowired
    public QuestionServiceImpl(QuestionMapper questionMapper, TextContentService textContentService, SubjectService subjectService) {
        super(questionMapper);
        this.textContentService = textContentService;
        this.questionMapper = questionMapper;
        this.subjectService = subjectService;
    }

    @Override
    public PageInfo<Question> page(QuestionPageRequestVM requestVM) {
        return PageHelper.startPage(requestVM.getPageIndex(), requestVM.getPageSize(), "id desc").doSelectPageInfo(() ->
                questionMapper.page(requestVM)
        );
    }


    @Override
    @Transactional
    public Question insertFullQuestion(QuestionEditRequestVM model, Integer userId) {
        Date now = new Date();
        Integer gradeLevel = subjectService.levelBySubjectId(model.getSubjectId());

        //题干、解析、选项等 插入
        TextContent infoTextContent = new TextContent();
        infoTextContent.setCreateTime(now);
        setQuestionInfoFromVM(infoTextContent, model);
        textContentService.insertByFilter(infoTextContent);

        Question question = new Question();
        question.setSubjectId(model.getSubjectId());
        question.setBankType(model.getBankType());
        question.setPositionId(model.getPositionId());
        question.setGradeLevel(gradeLevel);
        question.setCreateTime(now);
        question.setQuestionType(model.getQuestionType());
        question.setStatus(QuestionStatusEnum.OK.getCode());
        question.setCorrectFromVM(model.getCorrect(), model.getCorrectArray());
        // Business rule: every question is worth exactly one point.
        question.setScore(ExamUtil.scoreFromVM("1"));
        question.setDifficult(model.getDifficult());
        question.setInfoTextContentId(infoTextContent.getId());
        question.setCreateUser(userId);
        question.setDeleted(false);
        questionMapper.insertSelective(question);
        return question;
    }

    @Override
    @Transactional
    public Question updateFullQuestion(QuestionEditRequestVM model) {
        Integer gradeLevel = subjectService.levelBySubjectId(model.getSubjectId());
        Question question = questionMapper.selectByPrimaryKey(model.getId());
        question.setSubjectId(model.getSubjectId());
        question.setBankType(model.getBankType());
        question.setPositionId(model.getPositionId());
        question.setGradeLevel(gradeLevel);
        // Business rule: every question is worth exactly one point.
        question.setScore(ExamUtil.scoreFromVM("1"));
        question.setDifficult(model.getDifficult());
        question.setCorrectFromVM(model.getCorrect(), model.getCorrectArray());
        questionMapper.updateByPrimaryKeySelective(question);

        //题干、解析、选项等 更新
        TextContent infoTextContent = textContentService.selectById(question.getInfoTextContentId());
        setQuestionInfoFromVM(infoTextContent, model);
        textContentService.updateByIdFilter(infoTextContent);

        return question;
    }

    @Override
    public QuestionEditRequestVM getQuestionEditRequestVM(Integer questionId) {
        //题目映射
        Question question = questionMapper.selectByPrimaryKey(questionId);
        return getQuestionEditRequestVM(question);
    }

    @Override
    public QuestionEditRequestVM getQuestionEditRequestVM(Question question) {
        //题目映射
        TextContent questionInfoTextContent = textContentService.selectById(question.getInfoTextContentId());
        QuestionObject questionObject = JsonUtil.toJsonObject(questionInfoTextContent.getContent(), QuestionObject.class);
        QuestionEditRequestVM questionEditRequestVM = modelMapper.map(question, QuestionEditRequestVM.class);
        questionEditRequestVM.setTitle(questionObject.getTitleContent());

        //答案
        QuestionTypeEnum questionTypeEnum = QuestionTypeEnum.fromCode(question.getQuestionType());
        switch (questionTypeEnum) {
            case SingleChoice:
            case TrueFalse:
                questionEditRequestVM.setCorrect(question.getCorrect());
                break;
            case MultipleChoice:
                questionEditRequestVM.setCorrectArray(ExamUtil.contentToArray(question.getCorrect()));
                break;
            case GapFilling:
                List<String> correctContent = questionObject.getQuestionItemObjects().stream().map(d -> d.getContent()).collect(Collectors.toList());
                questionEditRequestVM.setCorrectArray(correctContent);
                break;
            case ShortAnswer:
                questionEditRequestVM.setCorrect(questionObject.getCorrect());
                break;
            default:
                break;
        }
        questionEditRequestVM.setScore(ExamUtil.scoreToVM(question.getScore()));
        questionEditRequestVM.setAnalyze(questionObject.getAnalyze());


        //题目项映射
        List<QuestionEditItemVM> editItems = questionObject.getQuestionItemObjects().stream().map(o -> {
            QuestionEditItemVM questionEditItemVM = modelMapper.map(o, QuestionEditItemVM.class);
            if (o.getScore() != null) {
                questionEditItemVM.setScore(ExamUtil.scoreToVM(o.getScore()));
            }
            return questionEditItemVM;
        }).collect(Collectors.toList());
        questionEditRequestVM.setItems(editItems);
        return questionEditRequestVM;
    }

    public void setQuestionInfoFromVM(TextContent infoTextContent, QuestionEditRequestVM model) {
        List<QuestionItemObject> itemObjects = model.getItems().stream().map(i ->
                {
                    QuestionItemObject item = new QuestionItemObject();
                    item.setPrefix(i.getPrefix());
                    item.setContent(i.getContent());
                    item.setItemUuid(i.getItemUuid());
                    item.setScore(ExamUtil.scoreFromVM(i.getScore()));
                    return item;
                }
        ).collect(Collectors.toList());
        QuestionObject questionObject = new QuestionObject();
        questionObject.setQuestionItemObjects(itemObjects);
        questionObject.setAnalyze(model.getAnalyze());
        questionObject.setTitleContent(model.getTitle());
        questionObject.setCorrect(model.getCorrect());
        infoTextContent.setContent(JsonUtil.toJsonStr(questionObject));
    }

    @Override
    public Integer selectAllCount() {
        return questionMapper.selectAllCount();
    }

    @Override
    public List<Integer> selectMothCount() {
        Date startTime = DateTimeUtil.getMonthStartDay();
        Date endTime = DateTimeUtil.getMonthEndDay();
        List<String> mothStartToNowFormat = DateTimeUtil.MothStartToNowFormat();
        List<KeyValue> mouthCount = questionMapper.selectCountByDate(startTime, endTime);
        return mothStartToNowFormat.stream().map(md -> {
            KeyValue keyValue = mouthCount.stream().filter(kv -> kv.getName().equals(md)).findAny().orElse(null);
            return null == keyValue ? 0 : keyValue.getValue();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int importQuestions(List<QuestionEditRequestVM> questions, Integer userId) {
        for (QuestionEditRequestVM question : questions) {
            insertFullQuestion(question, userId);
        }
        return questions.size();
    }

    @Override
    public List<String> findExistingTitles(List<QuestionEditRequestVM> questions) {
        Map<String, List<QuestionEditRequestVM>> scopes = new LinkedHashMap<>();
        for (QuestionEditRequestVM question : questions) {
            String scopeKey = question.getSubjectId() + ":" + question.getBankType() + ":" + question.getPositionId();
            scopes.computeIfAbsent(scopeKey, key -> new ArrayList<>()).add(question);
        }

        List<String> existingTitles = new ArrayList<>();
        for (List<QuestionEditRequestVM> scopeQuestions : scopes.values()) {
            QuestionEditRequestVM scope = scopeQuestions.get(0);
            List<String> titles = scopeQuestions.stream()
                    .map(QuestionEditRequestVM::getTitle)
                    .collect(Collectors.toList());
            existingTitles.addAll(questionMapper.selectExistingTitles(scope.getSubjectId(), scope.getBankType(),
                    scope.getPositionId(), titles));
        }
        return existingTitles;
    }

    @Override
    public List<QuestionEditRequestVM> filterNewQuestions(List<QuestionEditRequestVM> questions) {
        Map<String, List<QuestionEditRequestVM>> scopes = groupByScope(questions);
        Set<String> existingFingerprints = new LinkedHashSet<>();
        for (List<QuestionEditRequestVM> scopeQuestions : scopes.values()) {
            QuestionEditRequestVM scope = scopeQuestions.get(0);
            List<String> titles = scopeQuestions.stream()
                    .map(QuestionEditRequestVM::getTitle)
                    .distinct()
                    .collect(Collectors.toList());
            List<Question> existingQuestions = questionMapper.selectExistingQuestions(
                    scope.getSubjectId(), scope.getBankType(), scope.getPositionId(), titles);
            for (Question existingQuestion : existingQuestions) {
                existingFingerprints.add(QuestionDuplicateChecker.fingerprint(
                        getQuestionEditRequestVM(existingQuestion)));
            }
        }

        Set<String> currentFingerprints = new LinkedHashSet<>();
        List<QuestionEditRequestVM> newQuestions = new ArrayList<>();
        for (QuestionEditRequestVM question : questions) {
            String fingerprint = QuestionDuplicateChecker.fingerprint(question);
            if (!existingFingerprints.contains(fingerprint) && currentFingerprints.add(fingerprint)) {
                newQuestions.add(question);
            }
        }
        return newQuestions;
    }

    @Override
    @Transactional
    public int softDeleteQuestions(List<Integer> ids) {
        List<Integer> distinctIds = new ArrayList<>(new LinkedHashSet<>(ids));
        return questionMapper.softDeleteByIds(distinctIds);
    }

    private Map<String, List<QuestionEditRequestVM>> groupByScope(List<QuestionEditRequestVM> questions) {
        Map<String, List<QuestionEditRequestVM>> scopes = new LinkedHashMap<>();
        for (QuestionEditRequestVM question : questions) {
            String scopeKey = question.getSubjectId() + ":" + question.getBankType() + ":" + question.getPositionId();
            scopes.computeIfAbsent(scopeKey, key -> new ArrayList<>()).add(question);
        }
        return scopes;
    }


}
