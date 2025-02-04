package com.example.seesaw.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class PostTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String tagName;

    @ManyToOne //(fetch = FetchType.LAZY)
    @JoinColumn(name = "postid", nullable = false)
    private Post post;


    public PostTag(String tagName, Post post){
        this.tagName = tagName;
        this.post = post;
    }

}
