package com.heecomou.config;

import com.heecomou.security.RefreshTokenService;
import com.heecomou.security.TokenBlacklistService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return mock(StringRedisTemplate.class);
    }

    @Bean
    @Primary
    public TokenBlacklistService tokenBlacklistService(StringRedisTemplate redisTemplate) {
        return mock(TokenBlacklistService.class);
    }

    @Bean
    @Primary
    public RefreshTokenService refreshTokenService(StringRedisTemplate redisTemplate) {
        return mock(RefreshTokenService.class);
    }
}
