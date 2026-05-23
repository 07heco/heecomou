package com.heecomou.aspect;

import java.util.Collections;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.heecomou.annotation.RateLimit;
import com.heecomou.exception.RateLimitException;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private static final String TOKEN_BUCKET_SCRIPT =
            "local key = KEYS[1]\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local rate = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local requested = tonumber(ARGV[4])\n" +
            "\n" +
            "local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')\n" +
            "local tokens = tonumber(bucket[1]) or capacity\n" +
            "local lastRefill = tonumber(bucket[2]) or now\n" +
            "\n" +
            "local elapsed = now - lastRefill\n" +
            "local refill = elapsed * rate\n" +
            "tokens = math.min(capacity, tokens + refill)\n" +
            "\n" +
            "if tokens >= requested then\n" +
            "    tokens = tokens - requested\n" +
            "    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)\n" +
            "    redis.call('EXPIRE', key, math.ceil(capacity / rate) + 10)\n" +
            "    return 1\n" +
            "end\n" +
            "\n" +
            "return 0\n";

    private final DefaultRedisScript<Long> script;
    private final StringRedisTemplate redisTemplate;
    private final HttpServletRequest request;

    public RateLimitAspect(StringRedisTemplate redisTemplate, HttpServletRequest request) {
        this.redisTemplate = redisTemplate;
        this.request = request;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(TOKEN_BUCKET_SCRIPT);
        this.script.setResultType(Long.class);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = buildKey(joinPoint, rateLimit);
        long now = System.currentTimeMillis() / 1000;

        Long allowed = redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(rateLimit.capacity()),
                String.valueOf((double) rateLimit.rate() / rateLimit.seconds()),
                String.valueOf(now),
                "1"
        );

        if (allowed == null || allowed == 0) {
            log.warn("限流触发: key={}", key);
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }

        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (!rateLimit.key().isEmpty()) {
            return "rate_limit:" + rateLimit.key();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getMethod().getDeclaringClass().getSimpleName();
        String methodName = signature.getMethod().getName();
        String ip = getClientIp();
        return "rate_limit:" + className + ":" + methodName + ":" + ip;
    }

    private String getClientIp() {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }
}
