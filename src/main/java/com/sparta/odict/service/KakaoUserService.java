package com.sparta.odict.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.odict.dto.KakaoUserInfoDto;
import com.sparta.odict.model.User;
import com.sparta.odict.model.UserRoleEnum;
import com.sparta.odict.repository.UserRepository;
import com.sparta.odict.security.UserDetailsImpl;
import com.sparta.odict.security.jwt.JwtTokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class KakaoUserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public String kakaoLogin(String code) throws JsonProcessingException {
        // 1. "인가 코드"로 "액세스 토큰" 요청
        String accessToken = getAccessToken(code);

        System.out.println("인가 코드 : " + code);
        System.out.println("엑세스 토큰 : " + accessToken);

        // 2. "액세스 토큰"으로 "카카오 사용자 정보" 가져오기
        KakaoUserInfoDto kakaoUserInfo = getKakaoUserInfo(accessToken);

        // 3. 필요시에 회원가입
        User kakaoUser = registerKakaoUserIfNeeded(kakaoUserInfo);

        // 4. 로그인 JWT 토큰 발행
        return jwtTokenCreate(kakaoUser);
    }

    private String getAccessToken(String code) throws JsonProcessingException {
        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // HTTP Body 생성
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        // 클라이언트 아이디, url 확인
        body.add("client_id", "6f05e336898a8b021c45ac7c1f8770b8");
        body.add("redirect_uri", "http://localhost:8080/oauth/kakao/callback");
        body.add("code", code);

        // HTTP 요청 보내기
        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest =
                new HttpEntity<>(body, headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                kakaoTokenRequest,
                String.class
        );

        // HTTP 응답 (JSON) -> 액세스 토큰 파싱
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return jsonNode.get("access_token").asText();
    }

    private KakaoUserInfoDto getKakaoUserInfo(String accessToken) throws JsonProcessingException {
        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // HTTP 요청 보내기
        HttpEntity<MultiValueMap<String, String>> kakaoUserInfoRequest = new HttpEntity<>(headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                kakaoUserInfoRequest,
                String.class
        );

        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        Long id = jsonNode.get("id").asLong();
        String nickname = jsonNode.get("properties")
                .get("nickname").asText();
        String email = jsonNode.get("kakao_account")
                .get("email").asText();
        String age = jsonNode.get("kakao_account")
                .get("age_range").asText();

        return new KakaoUserInfoDto(id, nickname, email, age);
    }

    private User registerKakaoUserIfNeeded(KakaoUserInfoDto kakaoUserInfo) {
        // DB 에 중복된 Kakao Id 가 있는지 확인
        Long kakaoId = kakaoUserInfo.getId();
        // 카카오 아이디를 String으로 변환하여 username으로 db에 저장
//        String kakaoUsername = Long.toString(kakaoUserInfo.getId());
        String kakaoUsername = kakaoUserInfo.getEmail();

        System.out.println("input kakaoEmail : " +kakaoUsername);

        User kakaoUser = userRepository.findByUsername(kakaoUsername)
                .orElse(null);
        if (kakaoUser == null) {
            // 회원가입
            // nickname: kakao nickname
            String kakaoNickname = kakaoUserInfo.getNickname();

            System.out.println("kakaoNickname : " + kakaoNickname);

            // password: random UUID
            String password = UUID.randomUUID().toString();
            String encodedPassword = passwordEncoder.encode(password);

            //연령대
            String age = kakaoUserInfo.getAge();
            System.out.println(age);

            kakaoUser = new User(kakaoUsername, kakaoNickname, encodedPassword, age, null, 0L, UserRoleEnum.USER, kakaoId);
            userRepository.save(kakaoUser);
        }
        return kakaoUser;
    }

    //강제 로그인
    private String jwtTokenCreate(User kakaoUser) {
        String TOKEN_TYPE = "BEARER";

        UserDetails userDetails = new UserDetailsImpl(kakaoUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails1 = ((UserDetailsImpl) authentication.getPrincipal());

        System.out.println("userDetails1 : " + userDetails1.toString());

        final String token = JwtTokenUtils.generateJwtToken(userDetails1);
        return TOKEN_TYPE + " " + token;
    }
}