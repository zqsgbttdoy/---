package com.supervision.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 生产环境安全校验器（仅在 prod profile 下生效）。
 * <p>
 * 在应用启动阶段（{@link PostConstruct}）对生产环境关键安全配置进行校验，
 * 任一项不满足要求即抛出 {@link IllegalStateException} 使启动快速失败，
 * 避免带着不安全的默认配置上线。校验项包括：
 * <ul>
 *   <li>JWT 密钥：非空、长度 ≥32 字符、不等于开发默认密钥、不得保留模板占位符</li>
 *   <li>数据库密码：非空、不得为弱密码（root / 123456）、长度 ≥8 且同时包含字母与数字</li>
 *   <li>CORS 允许来源：非空且不得包含通配符 "*"</li>
 *   <li>默认重置密码：非空、不得为 "123456"、长度 ≥8 且同时包含字母与数字</li>
 * </ul>
 */
@Component
@Profile("prod")
public class ProductionSafetyValidator {

    /** 开发环境默认 JWT 密钥，生产禁止使用 */
    static final String DEV_DEFAULT_JWT_SECRET =
            "supervision-site-management-default-secret-key-please-change-in-production-2026";

    /** 密码类（数据库密码 / 默认重置密码）最小长度 */
    private static final int MIN_PASSWORD_LENGTH = 8;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.upload.signing-secret:}")
    private String uploadSigningSecret;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${app.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${app.security.default-reset-password:}")
    private String defaultResetPassword;

    /**
     * 启动时执行全部安全校验，任一项失败即中止启动。
     */
    @PostConstruct
    public void validate() {
        validateJwtSecret();
        validateUploadSigningSecret();
        validateDatasourcePassword();
        validateCorsAllowedOrigins();
        validateDefaultResetPassword();
    }

    private void validateUploadSigningSecret() {
        if (uploadSigningSecret == null || uploadSigningSecret.trim().length() < 32
                || uploadSigningSecret.contains("请替换") || uploadSigningSecret.equals(jwtSecret)
                || uploadSigningSecret.equals(DEV_DEFAULT_JWT_SECRET)) {
            throw new IllegalStateException("生产环境启动失败：请设置独立随机的 APP_UPLOAD_SIGNING_SECRET（至少 32 字符，不可复用 JWT 密钥或占位符）");
        }
    }

    /** 校验 JWT 密钥：非空、≥32 字符、不等于开发默认密钥、不是公开模板的占位值 */
    void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "生产环境启动失败：JWT 密钥未配置，请设置环境变量 APP_JWT_SECRET（至少 32 个字符）");
        }
        if (jwtSecret.contains("请替换")) {
            throw new IllegalStateException(
                    "生产环境启动失败：JWT 密钥仍为 supervision.env.example 模板占位值，请通过环境变量 APP_JWT_SECRET 注入 openssl rand -base64 48 生成的随机密钥");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "生产环境启动失败：JWT 密钥长度不足 32 个字符，请设置更长的环境变量 APP_JWT_SECRET");
        }
        if (DEV_DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "生产环境启动失败：JWT 密钥不得使用开发默认值，请通过环境变量 APP_JWT_SECRET 设置专用密钥");
        }
    }

    /** 校验数据库密码：非空、不得为弱密码 root / 123456、满足强度要求（≥8 且含字母+数字） */
    void validateDatasourcePassword() {
        if (datasourcePassword == null || datasourcePassword.trim().isEmpty()) {
            throw new IllegalStateException(
                    "生产环境启动失败：数据库密码未配置，请设置环境变量 SPRING_DATASOURCE_PASSWORD");
        }
        if ("root".equals(datasourcePassword) || "123456".equals(datasourcePassword)) {
            throw new IllegalStateException(
                    "生产环境启动失败：数据库密码不得使用弱密码（root/123456），请通过环境变量 SPRING_DATASOURCE_PASSWORD 设置强密码");
        }
        checkPasswordStrength(datasourcePassword, "SPRING_DATASOURCE_PASSWORD", "数据库密码");
    }

    /** 校验 CORS 允许来源：非空且不得包含通配符 "*" */
    void validateCorsAllowedOrigins() {
        if (corsAllowedOrigins == null || corsAllowedOrigins.trim().isEmpty()) {
            throw new IllegalStateException(
                    "生产环境启动失败：CORS 允许来源未配置，请设置环境变量 APP_CORS_ALLOWED_ORIGINS（逗号分隔的明确域名）");
        }
        if (corsAllowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "生产环境启动失败：CORS 允许来源不得包含通配符 \"*\"，请通过环境变量 APP_CORS_ALLOWED_ORIGINS 配置明确域名列表");
        }
    }

    /** 校验默认重置密码：非空、不得为 "123456"、满足强度要求（≥8 且含字母+数字） */
    void validateDefaultResetPassword() {
        if (defaultResetPassword == null || defaultResetPassword.trim().isEmpty()) {
            throw new IllegalStateException(
                    "生产环境启动失败：默认重置密码未配置，请设置环境变量 APP_DEFAULT_RESET_PASSWORD");
        }
        if ("123456".equals(defaultResetPassword)) {
            throw new IllegalStateException(
                    "生产环境启动失败：默认重置密码不得使用 \"123456\"，请通过环境变量 APP_DEFAULT_RESET_PASSWORD 设置更安全的值");
        }
        checkPasswordStrength(defaultResetPassword, "APP_DEFAULT_RESET_PASSWORD", "默认重置密码");
    }

    /**
     * <p>注：与 JWT 密钥同源的占位符拦截在 validateJwtSecret 内独立完成，任一模板值未替换均会阻断启动。</p>
     * 通用密码强度校验：长度 ≥{@value #MIN_PASSWORD_LENGTH}，且至少同时包含一个字母与一个数字。
     * 不满足即抛 {@link IllegalStateException} 并提示对应环境变量。
     * 注：{@link Character#isLetter(char)} 认中文等 Unicode 字母，故中文占位符会因“无数字”被拦截，
     * 正好强制运维用真实强密码替换模板占位值。
     */
    private void checkPasswordStrength(String password, String envName, String label) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "生产环境启动失败：" + label + "长度不足 " + MIN_PASSWORD_LENGTH
                            + " 位，请通过环境变量 " + envName + " 设置更长的密码");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new IllegalStateException(
                    "生产环境启动失败：" + label + "须同时包含字母与数字，请通过环境变量 "
                            + envName + " 设置更强的密码");
        }
    }

    // ---- 以下 setter 仅供单元测试使用 ----

    void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    void setDatasourcePassword(String datasourcePassword) {
        this.datasourcePassword = datasourcePassword;
    }

    void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    void setDefaultResetPassword(String defaultResetPassword) {
        this.defaultResetPassword = defaultResetPassword;
    }
}
