package com.workforcehub.im.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 员工端消息翻译响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslateImMessageResponse {

    private Long messageId;
    private String sourceText;
    private String translatedText;
}
