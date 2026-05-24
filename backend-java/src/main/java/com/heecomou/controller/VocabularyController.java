package com.heecomou.controller;

import com.heecomou.annotation.RateLimit;
import com.heecomou.model.dto.ApiResponse;
import com.heecomou.model.dto.VocabSyncRequest;
import com.heecomou.model.dto.VocabSyncResponse;
import com.heecomou.model.dto.VocabListResponse;
import com.heecomou.model.dto.VocabRequest;
import com.heecomou.model.vo.VocabularyVO;
import com.heecomou.security.JwtUtil;
import com.heecomou.service.VocabularyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vocabulary")
public class VocabularyController {

    private final VocabularyService vocabularyService;
    private final JwtUtil jwtUtil;

    public VocabularyController(VocabularyService vocabularyService, JwtUtil jwtUtil) {
        this.vocabularyService = vocabularyService;
        this.jwtUtil = jwtUtil;
    }

    @RateLimit(key = "vocab-add", capacity = 30, rate = 30, seconds = 60)
    @PostMapping
    public ApiResponse<VocabularyVO> add(HttpServletRequest request,
                                         @Valid @RequestBody VocabRequest req) {
        Long userId = extractUserId(request);
        VocabularyVO vo = vocabularyService.add(userId, req);
        return ApiResponse.success("词汇已添加", vo);
    }

    @PutMapping("/{id}")
    public ApiResponse<VocabularyVO> update(HttpServletRequest request,
                                            @PathVariable Long id,
                                            @Valid @RequestBody VocabRequest req) {
        Long userId = extractUserId(request);
        VocabularyVO vo = vocabularyService.update(userId, id, req);
        return ApiResponse.success("词汇已更新", vo);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request,
                                    @PathVariable Long id) {
        Long userId = extractUserId(request);
        vocabularyService.delete(userId, id);
        return ApiResponse.success("词汇已删除", null);
    }

    @GetMapping
    public ApiResponse<VocabListResponse> list(HttpServletRequest request,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Long userId = extractUserId(request);
        VocabListResponse resp = vocabularyService.listByUser(userId, page, size);
        return ApiResponse.success(resp);
    }

    @GetMapping("/search")
    public ApiResponse<VocabListResponse> search(HttpServletRequest request,
                                                  @RequestParam String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        Long userId = extractUserId(request);
        VocabListResponse resp = vocabularyService.search(userId, keyword, page, size);
        return ApiResponse.success(resp);
    }

    @GetMapping("/{id}")
    public ApiResponse<VocabularyVO> getById(HttpServletRequest request,
                                              @PathVariable Long id) {
        Long userId = extractUserId(request);
        VocabularyVO vo = vocabularyService.getById(userId, id);
        return ApiResponse.success(vo);
    }

    @PostMapping("/sync")
    public ApiResponse<VocabSyncResponse> sync(HttpServletRequest request,
                                                @Valid @RequestBody VocabSyncRequest req) {
        Long userId = extractUserId(request);
        VocabSyncResponse resp = vocabularyService.sync(userId, req);
        return ApiResponse.success(resp);
    }

    private Long extractUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = header.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
}
