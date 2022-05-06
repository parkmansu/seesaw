package com.example.seesaw.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Getter
@NoArgsConstructor
@Setter
@Entity
@AllArgsConstructor
public class GominImage {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(nullable = false)
    private String imageUrl;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "GOMIN_ID", nullable = false)
    private Gomin gomin;

    public GominImage(String imageUrl, User user, Gomin gomin){
        this.imageUrl = imageUrl;
        this.user = user;
        this.gomin = gomin;

    }
}
