package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bot_translations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"language_code", "translation_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "translation_key", nullable = false)
    private String translationKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;
}
