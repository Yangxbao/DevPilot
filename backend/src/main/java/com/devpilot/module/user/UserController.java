package com.devpilot.module.user;

import com.devpilot.common.api.ApiResponse;
import com.devpilot.common.api.PageResult;
import com.devpilot.common.config.AppProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devpilot.module.user.dto.UserCreateRequest;
import com.devpilot.module.user.dto.UserUpdateStatusRequest;
import com.devpilot.module.user.dto.UserVO;
import com.devpilot.module.user.mapper.SysUserMapper;
import com.devpilot.security.SecurityUtils;
import com.devpilot.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口：个人信息 + 管理员用户管理。
 */
@Tag(name = "user", description = "用户管理")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController implements ApplicationRunner {

    private final UserService userService;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/users/me")
    public ApiResponse<UserVO> me() {
        UserPrincipal principal = SecurityUtils.current();
        return ApiResponse.ok(UserVO.from(userService.requireActive(principal.id())));
    }

    @Operation(summary = "用户分页列表（仅管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users")
    public ApiResponse<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userService.page(page, Math.min(size, 100), keyword));
    }

    @Operation(summary = "创建用户（仅管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/users")
    public ApiResponse<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @Operation(summary = "启用/禁用用户（仅管理员）")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/users/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserUpdateStatusRequest request) {
        userService.updateStatus(id, request.status(), SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    /**
     * 管理员引导：首次启动若无任何 ADMIN 账号，则按配置创建。
     * 幂等：已有管理员时什么都不做。默认密码会在日志中显著告警。
     */
    @Override
    public void run(ApplicationArguments args) {
        Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "ADMIN"));
        if (adminCount != null && adminCount > 0) {
            return;
        }
        AppProperties.Admin admin = properties.getAdmin();
        SysUser user = new SysUser();
        user.setUsername(admin.getBootstrapUsername());
        user.setPasswordHash(passwordEncoder.encode(admin.getBootstrapPassword()));
        user.setNickname("管理员");
        user.setRole("ADMIN");
        user.setStatus(1);
        userMapper.insert(user);
        if ("admin123456".equals(admin.getBootstrapPassword())) {
            List<String> warn = List.of("===", "警告: 正在使用默认管理员密码 admin123456，仅供本地开发演示。",
                    "生产环境请务必设置环境变量 ADMIN_PASSWORD 为强密码。", "===");
            warn.forEach(System.out::println);
            org.slf4j.LoggerFactory.getLogger(UserController.class).warn("默认管理员密码未被覆盖，仅限开发环境使用");
        } else {
            org.slf4j.LoggerFactory.getLogger(UserController.class).info("已创建引导管理员账号: {}", admin.getBootstrapUsername());
        }
    }
}
