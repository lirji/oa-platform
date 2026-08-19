package com.lrj.oa.security.model;

/** 脱敏策略。 */
public enum SensitiveType {
    MOBILE,     // 138****1234
    ID_CARD,    // 110***********1234
    EMAIL,      // ab***@example.com
    BANK_CARD,  // **** **** **** 1234
    NAME,       // 张*
    ADDRESS,    // 前 6 位后打码
    AMOUNT      // 整体替换为 "***"
}
