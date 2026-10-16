package com.supervision.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 跨域配置：允许来源通过配置项 app.cors.allowed-origins 指定（逗号分隔）。
 * <p>
 * 策略说明：
 * <ul>
 *   <li>当配置包含通配符 "*"（仅开发场景）：使用 allowedOriginPatterns("*")
 *       并关闭 allowCredentials。前端采用 Authorization: Bearer token 而非 Cookie 认证，
 *       关闭 credentials 不影响任何功能。</li>
 *   <li>当配置为明确域名列表（生产场景）：使用 allowedOrigins(明确列表)
 *       并开启 allowCredentials(true)。</li>
 * </ul>
 * 生产环境由 application-prod.yml 强制要求配置明确域名（禁止 "*"，
 * 由 ProductionSafetyValidator 启动校验）。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean wildcard = Arrays.asList(allowedOrigins).contains("*");
        if (wildcard) {
            // 开发场景：放开全部来源，但关闭 credentials（前端用 Bearer token，不依赖 Cookie）
            registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(false)
                    .maxAge(3600);
        } else {
            // 生产场景：仅允许明确域名，并开启 credentials
            registry.addMapping("/**")
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}
