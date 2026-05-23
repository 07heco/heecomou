package com.heecomou.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "heecomou-backend");

        boolean dbOk = false;
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                dbOk = conn.isValid(3);
            } catch (Exception ignored) {
            }
        }
        result.put("db", dbOk);

        boolean redisOk = false;
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().get("health_check");
                redisOk = true;
            } catch (Exception ignored) {
            }
        }
        result.put("redis", redisOk);

        return result;
    }
}