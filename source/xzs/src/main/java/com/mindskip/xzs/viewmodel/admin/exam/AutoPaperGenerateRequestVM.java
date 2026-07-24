package com.mindskip.xzs.viewmodel.admin.exam;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class AutoPaperGenerateRequestVM {
    @NotBlank private String name;
    @NotNull private Integer positionId;
    @NotNull private Integer subjectId;
    @NotNull private Integer paperType = 1;
    @NotNull @Min(1) private Integer suggestTime = 60;
    @NotNull @Min(1) private Integer totalCount = 100;
    @NotNull @Min(0) private Integer positionCount = 80;
    @NotNull @Min(0) private Integer safetyCount = 10;
    @NotNull @Min(0) private Integer ethicsCount = 10;
    @NotNull @Min(0) private Integer choiceCount = 30;
    @NotNull @Min(0) private Integer gapCount = 20;
    @NotNull @Min(0) private Integer trueFalseCount = 40;
    @NotNull @Min(0) private Integer shortAnswerCount = 10;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getPositionId() { return positionId; }
    public void setPositionId(Integer positionId) { this.positionId = positionId; }
    public Integer getSubjectId() { return subjectId; }
    public void setSubjectId(Integer subjectId) { this.subjectId = subjectId; }
    public Integer getPaperType() { return paperType; }
    public void setPaperType(Integer paperType) { this.paperType = paperType; }
    public Integer getSuggestTime() { return suggestTime; }
    public void setSuggestTime(Integer suggestTime) { this.suggestTime = suggestTime; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getPositionCount() { return positionCount; }
    public void setPositionCount(Integer positionCount) { this.positionCount = positionCount; }
    public Integer getSafetyCount() { return safetyCount; }
    public void setSafetyCount(Integer safetyCount) { this.safetyCount = safetyCount; }
    public Integer getEthicsCount() { return ethicsCount; }
    public void setEthicsCount(Integer ethicsCount) { this.ethicsCount = ethicsCount; }
    public Integer getChoiceCount() { return choiceCount; }
    public void setChoiceCount(Integer choiceCount) { this.choiceCount = choiceCount; }
    public Integer getGapCount() { return gapCount; }
    public void setGapCount(Integer gapCount) { this.gapCount = gapCount; }
    public Integer getTrueFalseCount() { return trueFalseCount; }
    public void setTrueFalseCount(Integer trueFalseCount) { this.trueFalseCount = trueFalseCount; }
    public Integer getShortAnswerCount() { return shortAnswerCount; }
    public void setShortAnswerCount(Integer shortAnswerCount) { this.shortAnswerCount = shortAnswerCount; }
}
