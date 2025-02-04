package com.example.seesaw.model;

import com.example.seesaw.dto.PostRequestDto;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;


@NoArgsConstructor
@Getter
@Setter
@Entity
public class Post extends Timestamped {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String contents;

    @Column
    private String videoUrl;

    @Column(nullable = false)
    private String generation;

    @Column
    private Long scrapCount = 0L;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    @Column
    private List<PostImage> postImages;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    @Column
    private List<PostTag> postTags;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    @Column
    private List<PostComment> postComments;

    @ManyToOne
    @JoinColumn(name = "userid")
    private User user;

    private Long views = 0L;


    public Post(String title, String contents, String videoUrl, String generation, User user, Long scrapCount) {
        this.title = title;
        this.contents = contents;
        this.videoUrl = videoUrl;
        this.generation = generation;
        this.user = user;
        this.scrapCount = scrapCount;
    }


    @Builder
    public Post(Long id, String title, String contents, String generation) {
        this.id = id;
        this.title = title;
        this.contents = contents;
        this.generation = generation;
    }


    public void update(PostRequestDto requestDto, User user) {
        this.title = requestDto.getTitle();
        this.generation = requestDto.getGeneration();
        this.contents = requestDto.getContents();
        this.videoUrl = requestDto.getVideoUrl();
        this.user = user;
    }

}
