package com.example.seesaw.service;

import com.example.seesaw.dto.*;
import com.example.seesaw.model.*;
import com.example.seesaw.repository.*;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Builder
@RequiredArgsConstructor
@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostTagRepository postTagRepository;
    private final PostS3Service postS3Service;
    private final UserService userService;
    private final ConvertTimeService convertTimeService;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final PostScrapRepository postScrapRepository;
    private final PostCommentLikeRepository postCommentLikeRepository;

    //단어장 작성
    public void createPost(PostRequestDto requestDto, List<MultipartFile> files, User user) {

        // 단어장 작성시 이미지파일 없이 등록시 기본 이미지 파일로 올리기!
        List<String> imagePaths = new ArrayList<>();
        if (files == null) {
            imagePaths.add("https://myseesaw.s3.ap-northeast-2.amazonaws.com/DictBasicCard.svg");
        } else {
            imagePaths.addAll(postS3Service.upload(files));

        }
        // 단어
        String title = requestDto.getTitle();
        // 내용
        String contents = requestDto.getContents();
        // videoUrl
        String videoUrl = requestDto.getVideoUrl();
        // generation
        String generation = requestDto.getGeneration();
        // 태그
        List<String> tagNames = requestDto.getTagNames();

        if (title == null) {
            throw new IllegalArgumentException("등록할 단어를 적어주세요.");
        } else if (contents == null) {
            throw new IllegalArgumentException("등록할 단어의 내용을 적어주세요.");
        }

        //post 저장
        Post post = new Post(title, contents, videoUrl, generation, user, 0L);
        postRepository.save(post);

        //이미지 URL 저장하기
        for(String imageUrl : imagePaths){
            PostImage postImage = new PostImage(imageUrl, post);
            postImageRepository.save(postImage);
        }
        // tag 저장하기.
        for(String tagName : tagNames){
            if(!tagName.equals("")){
                PostTag postTag = new PostTag(tagName, post);
                postTagRepository.save(postTag);
            }
        }
    }

    // 중복체크
    public boolean wordCheck(String title) {
        return postRepository.existsByTitle(title);
    }

    // 단어장 수정
    public void updatePost(Long postId, PostRequestDto requestDto, List<MultipartFile> files, User user) {

        // 해당 단어장 체크.
        Post post = postRepository.findById(postId).orElseThrow(
                () -> new IllegalArgumentException("해당 단어장이 없습니다.")
        );

        //post update
        post.update(requestDto, user);

        // 이미지 수정작업
        List<String> imagePaths = new ArrayList<>();
        if (files==null && requestDto.getPostImages().isEmpty()) {
            imagePaths.add("https://myseesaw.s3.ap-northeast-2.amazonaws.com/DictBasicCard.svg");
            postS3Service.delete(postId, requestDto.getPostImages());
            postImageRepository.deleteAllByPostId(postId);
        } else if(files!=null) {
            imagePaths.addAll(postS3Service.update(postId, requestDto.getPostImages(), files));
        } else{
            imagePaths = requestDto.getPostImages();
            postS3Service.delete(postId, requestDto.getPostImages());
            postImageRepository.deleteAllByPostId(postId);
        }

        //이미지 URL 저장
        for(String imageUrl : imagePaths){
            PostImage postImage = new PostImage(imageUrl, post);
            postImageRepository.save(postImage);

        }

        // tag 저장
        List<String> tagName = requestDto.getTagNames();
        postTagRepository.deleteAllByPostId(postId);
        for(String tagNames : tagName){
            PostTag postTag = new PostTag(tagNames, post);
            postTagRepository.save(postTag);
        }

    }

    // 단어 상세 보기.
    public PostDetailResponseDto findDetailPost(Long postId, int page, User user) {
        Post post = postRepository.findById(postId).orElseThrow(
                () -> new IllegalArgumentException("고민 Id에 해당하는 글이 없습니다.")
        );
        PostResponseDto postResponseDto = postTagAndImages(postId);


        PostDetailResponseDto postDetailResponseDto = getPostDetailResponseDto(user, post, postResponseDto);

        PostScrap savedPostScrap = postScrapRepository.findByUserAndPost(user, post);
        postDetailResponseDto.setScrapStatus(savedPostScrap != null);

        // paging 처리
        Pageable pageable = PageRequest.of(page-1, 4);
        Page<PostComment> postCommentPage = postCommentRepository.findAllByPostIdOrderByCreatedAtDesc(postId, pageable);

        // 댓글 개수
        List<PostComment> postComments = postCommentRepository.findAllByPostId(postId);
        postDetailResponseDto.setCommentCount((long) postComments.size());


        List<PostCommentDto> postCommentDtos = new ArrayList<>();
        for(PostComment postComment:postCommentPage){
            PostCommentDto postCommentRequestDto = getPostCommentDto(user, postComment);
            postCommentDtos.add(postCommentRequestDto);
        }
        postDetailResponseDto.setPostComments(postCommentDtos);

        return postDetailResponseDto;
    }

    public PostDetailResponseDto getPostDetailResponseDto(User user, Post post, PostResponseDto postResponseDto) {
        PostDetailResponseDto postDetailResponseDto = new PostDetailResponseDto(postResponseDto);
        postDetailResponseDto.setNickname(user.getNickname()); //로그인한 사용자의 닉네임
        postDetailResponseDto.setTitle(post.getTitle());
        postDetailResponseDto.setContents(post.getContents());
        postDetailResponseDto.setGeneration(post.getGeneration());
        postDetailResponseDto.setVideoUrl(post.getVideoUrl());
        postDetailResponseDto.setScrapCount(post.getScrapCount());
        postDetailResponseDto.setProfileImages(userService.findUserProfiles(post.getUser()));
        postDetailResponseDto.setLastNickname(post.getUser().getNickname()); //글을 마지막으로 작성한 사람의 닉네임
        String postTime = convertTimeService.convertLocaldatetimeToTime(post.getCreatedAt());
        postDetailResponseDto.setPostUpdateTime(postTime);
        postDetailResponseDto.setViews(post.getViews());
        postDetailResponseDto.setScrapCount(post.getScrapCount());
        post.setViews(post.getViews()+1);
        postRepository.save(post);
        return postDetailResponseDto;
    }

    public PostCommentDto getPostCommentDto(User user, PostComment postComment) {
        PostCommentDto postCommentRequestDto = new PostCommentDto(postComment);
        User commentUser = userRepository.findByNickname(postComment.getNickname()).orElseThrow(
                () -> new IllegalArgumentException("고민댓글에 해당하는 사용자를 찾을 수 없습니다."));
        postCommentRequestDto.setProfileImages(userService.findUserProfiles(commentUser));
        postCommentRequestDto.setCommentLikeCount(postComment.getLikeCount());
        String postCommentTime = convertTimeService.convertLocaldatetimeToTime(postComment.getCreatedAt());
        postCommentRequestDto.setCommentTime(postCommentTime);
        Long size = (long) postCommentRepository.findAllByPostId(postComment.getPost().getId()).size();
        postCommentRequestDto.setCommentCount(size);
        PostCommentLike savedPostCommentLike = postCommentLikeRepository.findByPostCommentAndUserId(postComment, user.getId());
        postCommentRequestDto.setCommentLikeStatus(savedPostCommentLike != null);
        return postCommentRequestDto;
    }


    public PostResponseDto postTagAndImages(Long postId) {

        Post post = postRepository.findById(postId).orElseThrow(
                () -> new IllegalArgumentException("해당하는 단어가 없습니다.")
        );

        List<PostTag> postTags = postTagRepository.findAllByPostId(postId);
        List<String> postTagList = new ArrayList<>();
        for(PostTag postTag : postTags){
            postTagList.add(postTag.getTagName());
        }

        List<PostImage> postImages = postImageRepository.findAllByPostId(postId);

        List<String> postImageList = new ArrayList<>();
        for(PostImage postImage : postImages){
            if(postImage.getPostImage().equals("https://myseesaw.s3.ap-northeast-2.amazonaws.com/DictBasicCard.svg")){
                break;
            } else {
                postImageList.add(postImage.getPostImage());
            }
        }

        return new PostResponseDto(post, postImageList, postTagList);
    }
    @Transactional
    public PostSearchDto searchPosts(String title, String contents, User user) {
        List<Post> posts = postRepository.findByTitleContainingOrContentsContaining(title,contents);
        List<PostSearchResponseDto> searchList = new ArrayList<>();
        PostSearchDto postSearchList = new PostSearchDto();
        if (posts.isEmpty())
            return postSearchList;

        for (Post post : posts) {
            searchList.add(this.convertEntityToDto(post, user));
        }
        Long size = (long) searchList.size();
        postSearchList.setListCount(size);
        postSearchList.setPostSearchList(searchList);

        return postSearchList;
    }
    private PostSearchResponseDto convertEntityToDto(Post post, User user) {
        List<PostComment> postComments = postCommentRepository.findAllByPostId(post.getId());
        PostScrap savedPostScrap = postScrapRepository.findByUserAndPost(user, post);
        boolean scrapStatus = savedPostScrap != null;

        return PostSearchResponseDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .contents(post.getContents())
                .generation(post.getGeneration())
                .imageCount((long)post.getPostImages().size())
                .commentCount((long)postComments.size())
                .scrapStatus(scrapStatus)
                .build();
    }


    // 최신순으로 단어 전체 리스트 페이지 조회
    public List<PostListResponseDto> findListPosts(User user){
        List<Post> posts = postRepository.findAllByOrderByCreatedAtDesc();
        List<PostListResponseDto> postListResponseDtos = new ArrayList<>();
        for (Post post: posts) {
            PostScrap savedPostScrap = postScrapRepository.findByUserAndPost(user, post);
            boolean scrapStatus = savedPostScrap != null;
            List<PostImage> postImages = postImageRepository.findAllByPostId(post.getId());
            postListResponseDtos.add(new PostListResponseDto(post, scrapStatus, postImages.get(0).getPostImage()));
        }
        return postListResponseDtos;
    }

    // 스크랩 순으로 매인페이지 조회
    public List<PostScrapSortResponseDto> findAllPosts(){
        // 스크랩 수가 많은 순서대로 16개의 post를 담는다.
        List<Post> posts = postRepository.findTop16ByOrderByScrapCountDesc();
        // 리스트 형태 Dto 타입 빈 객체 생성
        List<PostScrapSortResponseDto> postScrapSortResponseDtos = new ArrayList<>();
        // posts 하나씩 꺼내어 처리
        for (Post post: posts) {
            // postId 에 해당하는 post image 를 가져온다.
            List<PostImage> postImages = postImageRepository.findAllByPostId(post.getId());
            postScrapSortResponseDtos.add(new PostScrapSortResponseDto(post, postImages.get(0).getPostImage()));
        }

        return postScrapSortResponseDtos;
    }

    public List<PostScrapSortResponseDto> findRandomPosts() {
        List<PostScrapSortResponseDto> postScrapSortResponseDtos = new ArrayList<>();
        //게시글의 개수를 구한다.
        long postCount = postRepository.count();
        // 가져온 개수 중 랜덤한 하나의 인덱스를 뽑는다.
        int idx = (int)(Math.random() * postCount)/2;
        // 페이징하여 하나만 추출해낸다.
        Page<Post> postPages = postRepository.findAll(PageRequest.of(idx, 2));
        if (postPages.hasContent()) {
            for(Post postPage:postPages){
                // postId 에 해당하는 post image 를 가져온다.
                List<PostImage> postImages = postImageRepository.findAllByPostId(postPage.getId());

                postScrapSortResponseDtos.add(new PostScrapSortResponseDto(postPage, postImages.get(0).getPostImage()));
            }
        }
        return postScrapSortResponseDtos;
    }

    // 9개 단어 최신순으로 단어 메인 리스트 페이지 조회
    public List<PostListResponseDto> findMainListPosts(){
        List<Post> posts = postRepository.findTop9ByOrderByCreatedAtDesc();
        List<PostListResponseDto> postListResponseDtos = new ArrayList<>();
        for (Post post: posts) {
            List<PostImage> postImages = postImageRepository.findAllByPostId(post.getId());
            postListResponseDtos.add(new PostListResponseDto(post, postImages.get(0).getPostImage()));
            Collections.reverse(postListResponseDtos);
        }
        return postListResponseDtos;
    }
}
