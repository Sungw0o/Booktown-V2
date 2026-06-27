package com.booktown.controller.api;

import com.booktown.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Tag(name = "Health", description = "서비스와 의존 인프라 상태 확인 API")
public interface HealthApi {

    @GetMapping("/health")
    @Operation(summary = "서비스 상태 조회", description = "MySQL, Redis, MongoDB, ChromaDB 연결 상태를 포함한 서비스 상태를 조회합니다.")
    ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth();

    @GetMapping("/health/readiness")
    @Operation(summary = "배포 readiness 조회", description = "배포 후 트래픽 수신 가능 여부를 판단하기 위한 readiness endpoint입니다.")
    ResponseEntity<?> checkReadiness();
}
