package com.mindskip.xzs.domain.enums;

public enum QuestionBankTypeEnum {
    POSITION(1, "Position"),
    SAFETY(2, "Safety"),
    PROFESSIONAL_ETHICS(3, "Professional ethics");

    private final int code;
    private final String name;

    QuestionBankTypeEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() { return code; }
    public String getName() { return name; }

    public static QuestionBankTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (QuestionBankTypeEnum value : values()) {
            if (value.code == code) return value;
        }
        return null;
    }
}
