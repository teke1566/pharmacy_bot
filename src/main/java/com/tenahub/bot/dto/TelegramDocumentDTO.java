package com.tenahub.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramDocumentDTO {

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("mime_type")
    private String mimeType;
}