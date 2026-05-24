package com.heecomou.controller;

import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.dto.CorrectionRequest;
import com.heecomou.model.entity.CorrectionHistory;
import com.heecomou.security.JwtUtil;
import com.heecomou.service.CorrectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/corrections")
public class CorrectionController {

    private final CorrectionService correctionService;
    private final JwtUtil jwtUtil;

    public CorrectionController(CorrectionService correctionService, JwtUtil jwtUtil) {
        this.correctionService = correctionService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ApiResponse<CorrectionHistory> record(HttpServletRequest request,
                                                   @Valid @RequestBody CorrectionRequest req) {
        Long userId = extractUserId(request);
        CorrectionHistory history = correctionService.record(userId, req);
        return ApiResponse.success("纠错已记录", history);
    }

    @GetMapping
    public ApiResponse<List<CorrectionHistory>> list(HttpServletRequest request,
                                                       @RequestParam(defaultValue = "20") int limit) {
        Long userId = extractUserId(request);
        List<CorrectionHistory> list = correctionService.listByUser(userId, limit);
        return ApiResponse.success(list);
    }

    private Long extractUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = header.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
}
