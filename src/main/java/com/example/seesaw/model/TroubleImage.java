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
public class TroubleImage {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(nullable = false)
    private String troubleImage;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "TROUBLE_ID", nullable = false)
    private Trouble trouble;

    public TroubleImage(String troubleImage, User user, Trouble trouble){
        this.troubleImage = troubleImage;
        this.user = user;
        this.trouble = trouble;

    }
}
