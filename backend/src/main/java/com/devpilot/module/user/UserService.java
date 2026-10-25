package com.devpilot.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devpilot.common.api.ErrorCode;
import com.devpilot.common.exception.BizException;
import com.devpilot.module.user.dto.UserCreateRequest;
import com.devpilot.module.user.dto.UserVO;
import com.devpilot.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户领域服务：账号查询、管理员管理、越权防护的统一入口。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUser getByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    public SysUser requireActive(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在或已禁用");
        }
        return user;
    }

    public SysUser requireById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    public SysUser register(String username, String password, String nickname) {
        if (getByUsername(username) != null) {
            throw BizException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(StringUtils.hasText(nickname) ? nickname : username);
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    public com.devpilot.common.api.PageResult<UserVO> page(int page, int size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getNickname, keyword))
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return new com.devpilot.common.api.PageResult<>(
                result.getRecords().stream().map(UserVO::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public UserVO create(UserCreateRequest request) {
        if (getByUsername(request.username()) != null) {
            throw BizException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRole(request.role() == null ? "USER" : request.role());
        user.setStatus(1);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    public void updateStatus(Long targetId, Integer status, Long operatorId) {
        if (targetId.equals(operatorId)) {
            throw BizException.badRequest("不能禁用自己的账号");
        }
        SysUser user = requireById(targetId);
        user.setStatus(status);
        userMapper.updateById(user);
    }
}
