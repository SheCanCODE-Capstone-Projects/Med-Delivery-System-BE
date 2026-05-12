# Redis Setup Guide

## Problem
The application was configured to connect to Railway's internal Redis (`redis.railway.internal`), which is only accessible from within Railway's network. When running locally, the connection fails.

## Solution

### Option 1: Run Redis Locally with Docker (Recommended)

1. **Start Redis container:**
```bash
docker run -d --name redis -p 6379:6379 redis:latest
```

2. **Verify Redis is running:**
```bash
docker ps
```

3. **Your `.env` is already configured for localhost Redis**

4. **Start your application:**
```bash
mvn spring-boot:run
```

### Option 2: Run Without Redis (In-Memory Fallback)

The application now supports running without Redis using in-memory caching:

1. **Comment out Redis configuration in `.env`:**
```env
# REDISHOST=localhost
# REDISPORT=6379
# REDISPASSWORD=
```

2. **Start your application:**
```bash
mvn spring-boot:run
```

The app will automatically use in-memory storage for OTPs and rate limiting.

## What Was Fixed

1. **RedisConfig**: Made Redis beans conditional - only load when Redis host is configured
2. **OtpService**: Uses `Optional<RedisTemplate>` with in-memory fallback
3. **RateLimitService**: Uses `Optional<RedisTemplate>` with graceful degradation
4. **.env**: Updated to use `localhost` instead of Railway internal hostname

## For Production (Railway)

When deploying to Railway, update your environment variables:
```env
REDISHOST=redis.railway.internal
REDISPORT=6379
REDISPASSWORD=YRpnBdadsoogiEBVogIOdziZTFvRNQiv
```

## Testing

1. **With Redis:**
   - OTPs stored in Redis with TTL
   - Rate limiting enforced across restarts

2. **Without Redis:**
   - OTPs stored in-memory (lost on restart)
   - Rate limiting disabled (always allows requests)
