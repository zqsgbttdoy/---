package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.dto.ChangePasswordDTO;
import com.supervision.dto.LoginDTO;
import com.supervision.service.AuthService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return authService.login(dto);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authorization) {
        return authService.logout(authorization.substring(7).trim());
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return authService.info();
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        return authService.changePassword(dto);
    }
}
