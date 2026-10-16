package com.supervision.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 只保存已撤销令牌的随机标识，不保存登录凭证。 */
@Data
@Entity
@Table(name = "sys_token_revocation")
public class TokenRevocation {

    @Id
    @Column(length = 36)
    private String tokenId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
