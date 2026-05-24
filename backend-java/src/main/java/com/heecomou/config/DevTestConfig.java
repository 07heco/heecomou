package com.heecomou.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.heecomou.security.RefreshTokenService;
import com.heecomou.security.TokenBlacklistService;

@Configuration
@Profile("test")
public class DevTestConfig {

    @Bean
    @ConditionalOnMissingBean(TokenBlacklistService.class)
    public TokenBlacklistService tokenBlacklistService() {
        return new TokenBlacklistService(null) {
            @Override
            public void blacklist(String token, long ttlSeconds) {
            }

            @Override
            public boolean isBlacklisted(String token) {
                return false;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(RefreshTokenService.class)
    public RefreshTokenService refreshTokenService() {
        return new RefreshTokenService(null) {
            @Override
            public void save(String token, Long userId, long ttlMillis) {
            }

            @Override
            public boolean isValid(String token, Long userId) {
                return true;
            }

            @Override
            public void revoke(String token, Long userId) {
            }

            @Override
            public void revokeAll(Long userId) {
            }
        };
    }
}

