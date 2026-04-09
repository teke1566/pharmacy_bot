package com.tenahub.bot.dto;

import lombok.Data;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class TelegramMessageDTO {

    @JsonProperty("message_id")
    private Integer messageId;

    private Chat chat;

    private From from;

    private String text;

    private Location location;

    private Document document;

    private List<Photo> photo;

    @JsonProperty("contact")
    private Contact contact;

    private String caption;

    @Data
    public static class Chat {
        private Long id;
    }

    @Data
    public static class From {
        private Long id;
    }

    @Data
    public static class Location {

        @JsonProperty("latitude")
        private Double latitude;

        @JsonProperty("longitude")
        private Double longitude;
    }

    @Data
    public static class Document {

        @JsonProperty("file_id")
        private String fileId;

        @JsonProperty("file_name")
        private String fileName;

        @JsonProperty("mime_type")
        private String mimeType;
    }

    @Data
    public static class Photo {

        @JsonProperty("file_id")
        private String fileId;

        private Integer width;
        private Integer height;
    }

    @Data
    public static class Contact {

        @JsonProperty("phone_number")
        private String phoneNumber;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("user_id")
        private Long userId;
    }
}