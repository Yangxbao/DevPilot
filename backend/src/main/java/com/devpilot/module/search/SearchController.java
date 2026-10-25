package com.devpilot.module.search;

import com.devpilot.common.api.ApiResponse;
import com.devpilot.module.search.dto.SearchResponse;
import com.devpilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代码检索接口。
 */
@Tag(name = "search", description = "代码检索")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "检索代码（keyword/semantic/hybrid）")
    @GetMapping
    public ApiResponse<SearchResponse> search(@RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) Long repositoryId,
                                              @RequestParam String q,
                                              @RequestParam(defaultValue = "keyword") String mode,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(searchService.search(SecurityUtils.current(), projectId, repositoryId,
                q, mode, page, Math.min(size, 50)));
    }
}
