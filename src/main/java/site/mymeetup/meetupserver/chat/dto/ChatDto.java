package site.mymeetup.meetupserver.chat.dto;

import lombok.*;
import site.mymeetup.meetupserver.chat.entity.Chat;

import java.time.LocalDateTime;
import java.util.UUID;

public class ChatDto {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class ChatRespDto {
        private String id;
        private String message;
        private Long senderId;
        private Long receiverId;
        private Long crewId;
        private LocalDateTime createDate;

        @Builder
        public ChatRespDto(Chat chat) {
            this.id = UUID.randomUUID().toString();
            this.message = chat.getMessage();
            this.senderId = chat.getSenderId();
            this.receiverId = chat.getReceiverId();
            this.crewId = chat.getCrewId();
            this.createDate = chat.getCreateDate();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class ChatSaveReqDto {
        private String id;
        private Long senderId;
        private Long receiverId;
        private String message;

        @Builder
        public ChatSaveReqDto(Chat chat) {
            this.id = UUID.randomUUID().toString();
            this.message = chat.getMessage();
            this.senderId = chat.getSenderId();
            this.receiverId = chat.getReceiverId();
        }
    }
}