package com.meddelivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostgresCacheService {

    private final JdbcTemplate jdbcTemplate;

    public void set(String key, String value, long timeout, TimeUnit unit) {
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(unit.toSeconds(timeout));
        
        String sql = """
            INSERT INTO cache_entries (cache_key, cache_value, expires_at)
            VALUES (?, ?, ?)
            ON CONFLICT (cache_key) 
            DO UPDATE SET cache_value = EXCLUDED.cache_value, 
                         expires_at = EXCLUDED.expires_at
            """;
        
        jdbcTemplate.update(sql, key, value, expiresAt);
        log.debug("Cached: {} (expires: {})", key, expiresAt);
    }

    public String get(String key) {
        String sql = """
            SELECT cache_value FROM cache_entries 
            WHERE cache_key = ? AND expires_at > NOW()
            """;
        
        try {
            return jdbcTemplate.queryForObject(sql, String.class, key);
        } catch (Exception e) {
            return null;
        }
    }

    public void delete(String key) {
        String sql = "DELETE FROM cache_entries WHERE cache_key = ?";
        jdbcTemplate.update(sql, key);
    }

    public void increment(String key) {
        String sql = """
            UPDATE cache_entries 
            SET cache_value = (CAST(cache_value AS INTEGER) + 1)::TEXT
            WHERE cache_key = ? AND expires_at > NOW()
            """;
        jdbcTemplate.update(sql, key);
    }

    public void cleanupExpired() {
        String sql = "DELETE FROM cache_entries WHERE expires_at < NOW()";
        int deleted = jdbcTemplate.update(sql);
        if (deleted > 0) {
            log.info("Cleaned up {} expired cache entries", deleted);
        }
    }
}
