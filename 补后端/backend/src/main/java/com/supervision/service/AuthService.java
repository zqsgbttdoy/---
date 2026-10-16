package com.supervision.service;

import com.supervision.common.ApiResponse;
import com.supervision.common.AuthContext;
import com.supervision.dto.ChangePasswordDTO;
import com.supervision.dto.LoginDTO;
import com.supervision.entity.Role;
import com.supervision.entity.User;
import com.supervision.entity.UserRole;
import com.supervision.exception.BusinessException;
import com.supervision.repository.RoleRepository;
import com.supervision.repository.UserRepository;
import com.supervision.repository.UserRoleRepository;
import com.supervision.util.JwtUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public ApiResponse<Map<String, Object>> login(LoginDTO dto) {
        Optional<User> userOpt = userRepository.findByPhone(dto.getUsername());
        if (!userOpt.isPresent()) {
            userOpt = userRepository.findByName(dto.getUsername());
        }
        if (!userOpt.isPresent()) {
            // 审计：登录失败（用户不存在），不记录密码
            log.info("登录失败：用户不存在，登录名={}", dto.getUsername());
            throw new BusinessException("用户名或密码错误");
        }
        User user = userOpt.get();
        if (!"active".equals(user.getStatus())) {
            log.info("登录失败：账号已被禁用，userId={}, 登录名={}", user.getId(), dto.getUsername());
            throw new BusinessException("账号已被禁用");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            // 审计：登录失败（密码错误），不记录密码
            log.info("登录失败：密码错误，userId={}, 登录名={}", user.getId(), dto.getUsername());
            throw new BusinessException("用户名或密码错误");
        }

        List<String> roles = loadRoleCodes(user.getId());
        String token = jwtUtil.generateToken(user.getId(), user.getName(), roles);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("userName", user.getName());
        result.put("avatar", user.getAvatar());
        result.put("roles", roles);
        result.put("token", token);
        log.info("登录成功：userId={}, userName={}, roles={}", user.getId(), user.getName(), roles);
        return ApiResponse.success("登录成功", result);
    }

    public ApiResponse<Void> logout(String token) {
        jwtUtil.revoke(token);
        return ApiResponse.success("退出成功", null);
    }

    public ApiResponse<Map<String, Object>> info() {
        AuthContext.CurrentUser current = AuthContext.get();
        if (current == null) {
            throw new BusinessException(401, "未登录");
        }
        User user = userRepository.findById(current.userId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("userName", user.getName());
        result.put("avatar", user.getAvatar());
        result.put("position", user.getPosition());
        result.put("deptId", user.getDeptId());
        result.put("roles", loadRoleCodes(user.getId()));
        return ApiResponse.success(result);
    }

    @Transactional
    public ApiResponse<Void> changePassword(ChangePasswordDTO dto) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getPassword() == null || !passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        log.info("修改密码成功：userId={}", userId);
        return ApiResponse.success("密码修改成功", null);
    }

    private List<String> loadRoleCodes(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量加载角色，避免逐条 findById 造成 N+1 查询
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        List<String> codes = new ArrayList<>();
        for (Role role : roleRepository.findAllById(roleIds)) {
            if (role.getCode() != null) {
                codes.add(role.getCode());
            }
        }
        return codes;
    }
}
