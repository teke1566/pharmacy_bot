package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_inbox_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminInboxItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminInboxItemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminInboxItemStatus status;

    @Column(name = "user_telegram_id", nullable = false)
    private Long userTelegramId;

    @Column(name = "pharmacy_id")
    private Long pharmacyId;

    @Column(name = "medicine_name", length = 150)
    private String medicineName;

    @Column(name = "issue_type", length = 80)
    private String issueType;

    @Column(name = "message_text", nullable = false, length = 4000)
    private String messageText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
