package com.booktown.domain.auth.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/auth/oauth2")
@Tag(name = "OAuth2", description = "Google, Kakao, Naver 소셜 로그인 API")
public interface OAuth2Api {

    @GetMapping("/{provider}/login")
    @Operation(summary = "소셜 로그인 시작", description = "`provider`는 `google`, `kakao`, `naver` 중 하나입니다. Provider 인증 페이지로 302 redirect합니다.")
    ResponseEntity<Void> login(@PathVariable String provider);

    @GetMapping("/{provider}/callback")
    @Operation(summary = "소셜 로그인 callback", description = "Provider 인가 코드를 처리하고 프론트 성공/실패 페이지로 redirect합니다.")
    ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    );
}
