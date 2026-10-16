package com.supervision.util;

import com.supervision.entity.TokenRevocation;
import com.supervision.repository.TokenRevocationRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * JWT 工具类：负责签发与解析令牌。
 * 密钥与有效期通过配置项 app.jwt.secret / app.jwt.expiration 提供，生产环境使用环境变量覆盖。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final TokenRevocationRepository tokenRevocationRepository;

    @Value("${app.jwt.invalidate-on-restart:false}")
    private boolean invalidateOnRestart;

    private final String bootId = UUID.randomUUID().toString();

    @Value("${app.jwt.secret:supervision-site-management-default-secret-key-please-change-in-production-2026}")
    private String secret;

    /** 令牌有效期（毫秒），默认 24 小时 */
    @Value("${app.jwt.expiration:86400000}")
    private long expiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // 安全断言：HMAC-SHA256 要求密钥至少 32 字节，长度不足直接快速失败
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT 密钥长度不足 32 字节，请通过配置项 app.jwt.secret（环境变量 APP_JWT_SECRET）设置至少 32 个字符的密钥");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌
     */
    public String generateToken(Long userId, String userName, List<String> roles) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("bootId", invalidateOnRestart ? bootId : null)
                .subject(String.valueOf(userId))
                .claim("userName", userName)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌，签名/过期非法时抛出 JwtException
     */
    public Claims parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getId() == null || claims.getExpiration() == null
                || (invalidateOnRestart && !bootId.equals(claims.get("bootId", String.class)))
                || tokenRevocationRepository.existsById(claims.getId())) {
            throw new JwtException("登录会话已失效，请重新登录");
        }
        return claims;
    }

    /** 撤销当前登录，其他标签页独立签发的令牌不受影响。 */
    @Transactional
    public void revoke(String token) {
        Claims claims = parse(token);
        tokenRevocationRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
        TokenRevocation revoked = new TokenRevocation();
        revoked.setTokenId(claims.getId());
        revoked.setExpiresAt(LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneOffset.UTC));
        tokenRevocationRepository.save(revoked);
    }

    /**
     * 校验令牌是否合法（不抛异常）
     */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
