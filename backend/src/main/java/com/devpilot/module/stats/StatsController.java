package com.devpilot.module.stats;

import com.devpilot.common.api.ApiResponse;
import com.devpilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘接口。
 */
@Tag(name = "stats", description = "仪表盘统计")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "仪表盘聚合统计（管理员全局 / 用户本人）")
    @GetMapping("/dashboard")
    public ApiResponse<StatsService.DashboardVO> dashboard() {
        return ApiResponse.ok(statsService.dashboard(SecurityUtils.current()));
    }
}
