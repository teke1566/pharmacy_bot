package com.tenahub.bot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminViewSession {
    private String type;
    private Long targetId;
    private Integer summaryMessageId;
    private Integer detailMessageId;
}