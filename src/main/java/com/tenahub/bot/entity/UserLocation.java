package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_locations")
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;

    private Double latitude;

    private Double longitude;
    @Column(name = "region")
private String region;

@Column(name = "city")
private String city;

@Column(name = "area")
private String area;

@Column(name = "sub_city")
private String subCity;

@Column(name = "display_name")
private String displayName;
}