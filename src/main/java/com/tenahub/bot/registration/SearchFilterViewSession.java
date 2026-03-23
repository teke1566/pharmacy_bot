package com.tenahub.bot.registration;

public class SearchFilterViewSession {

    private Integer filterMessageId;

    public SearchFilterViewSession(Integer filterMessageId) {
        this.filterMessageId = filterMessageId;
    }

    public Integer getFilterMessageId() {
        return filterMessageId;
    }

    public void setFilterMessageId(Integer filterMessageId) {
        this.filterMessageId = filterMessageId;
    }
}