# Redis & SMTP Configuration Guide

## ✅ What's Fixed

### 1. Redis is Now Optional with In-Memory Fallback

**Changes Made:**
- `RefreshTokenService.java` - Added automatic fallback to in-memory storage
- `RedisConfig.java` - Made Redis bean conditional on configuration

**How It Works:**
```
┌─────────────────────────────────────┐
│  Login Request                      │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Generate Refresh Token             │
└──────────────┬──────────────────────┘
               │
               ▼
        ┌──────────────┐
        │ Redis Available? │
        └──────┬───────────┘
               │
       ┌───────┴────────┐
       │                │
      YES              NO
       │                │
       ▼                ▼
  ┌─────────┐    ┌──────────────┐
  │  Redis  │    │  In-Memory   │
  │ Storage │    │   Storage    │
  └─────────┘    └──────────────┘
       │                │
       └────────┬───────┘
                │
                ▼
         ┌──────────────┐
         │ Login Success │
         └──────────────┘
```

**Benefits:**
- ✅ Login works even without Redis
- ✅ Automatic failover to in-memory storage
- ✅ No code changes needed in controllers
- ✅ Seamless upgrade path when Redis is added

**Limitations of In-Memory Storage:**
- ⚠️ Tokens lost on server restart
- ⚠️ Won't work across multiple server instances
- ⚠️ Not suitable for production at scale

## 🔧 Redis Setup (Recommended for Production)

### Option 1: Railway Redis (Easiest)

1. **Add Redis to your Railway project:**
   ```bash
   # In Railway dashboard
   New → Database → Add Redis
   ```

2. **Railway automatically provides:**
   ```bash
   REDIS_PRIVATE_URL=redis://default:password@redis.railway.internal:6379
   ```

3. **No code changes needed** - It will automatically use Redis when available

### Option 2: External Redis (Upstash, Redis Cloud)

1. **Get Redis URL from provider**
2. **Set in Railway environment variables:**
   ```bash
   REDIS_PRIVATE_URL=redis://username:password@host:port
   ```

### Verify Redis Connection

Look for this log on startup:
```
✅ Redis connection successful
```

If you see:
```
⚠️ Redis connection failed: ... - Using in-memory fallback
```
Then it's using in-memory storage (works but not ideal for production).

## 📧 SMTP Configuration

### Current Issue
```
⚠️ SMTP connection test failed: Couldn't connect to host, port: smtp.sendgrid.net, 587; timeout 5000
```

### Possible Causes:

1. **Railway Network Restrictions**
   - Railway might block outbound SMTP on port 587
   - Solution: Use SendGrid API instead of SMTP

2. **Invalid SendGrid API Key**
   - Check if `SENDGRID_API_KEY` is correct
   - Verify it starts with `SG.`

3. **SendGrid Account Not Verified**
   - SendGrid requires domain verification for production
   - Check SendGrid dashboard

### Solution 1: Use SendGrid API (Recommended)

Instead of SMTP, use SendGrid's HTTP API which is more reliable:

**Add dependency to `pom.xml`:**
```xml
<dependency>
    <groupId>com.sendgrid</groupId>
    <artifactId>sendgrid-java</artifactId>
    <version>4.10.2</version>
</dependency>
```

**Create SendGrid service:**
```java
@Service
@Slf4j
public class SendGridEmailService {
    
    @Value("${sendgrid.api.key}")
    private String apiKey;
    
    @Value("${app.mail.from}")
    private String fromEmail;
    
    public void sendEmail(String to, String subject, String body) {
        Email from = new Email(fromEmail);
        Email toEmail = new Email(to);
        Content content = new Content("text/html", body);
        Mail mail = new Mail(from, subject, toEmail, content);
        
        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            
            log.info("Email sent successfully: {}", response.getStatusCode());
        } catch (IOException e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }
}
```

### Solution 2: Fix SMTP Configuration

**Update `application.properties`:**
```properties
# Try port 465 with SSL instead of 587 with TLS
spring.mail.host=smtp.sendgrid.net
spring.mail.port=465
spring.mail.username=apikey
spring.mail.password=${SENDGRID_API_KEY}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.ssl.enable=true
spring.mail.properties.mail.smtp.socketFactory.port=465
spring.mail.properties.mail.smtp.socketFactory.class=javax.net.ssl.SSLSocketFactory
```

### Solution 3: Use Alternative Email Provider

If SendGrid doesn't work on Railway, try:

**Resend (Modern, Developer-Friendly):**
```properties
# Resend has better Railway compatibility
resend.api.key=${RESEND_API_KEY}
```

**Mailgun:**
```properties
spring.mail.host=smtp.mailgun.org
spring.mail.port=587
spring.mail.username=${MAILGUN_USERNAME}
spring.mail.password=${MAILGUN_PASSWORD}
```

## 🚀 WebSocket Status

### ✅ WebSocket is Working!

Your logs show:
```
SimpleBrokerMessageHandler: Started.
BrokerAvailabilityEvent[available=true, ...]
```

**Current Setup:**
- Using **SimpleBroker** (in-memory)
- Works for single server instance
- Perfect for development and small-scale production

**Frontend Connection Example:**
```javascript
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

const socket = new SockJS('https://your-backend.railway.app/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, (frame) => {
  console.log('Connected:', frame);
  
  // Subscribe to order updates
  stompClient.subscribe('/topic/orders', (message) => {
    const order = JSON.parse(message.body);
    console.log('Order update:', order);
  });
  
  // Subscribe to user notifications
  stompClient.subscribe('/user/queue/notifications', (message) => {
    const notification = JSON.parse(message.body);
    console.log('Notification:', notification);
  });
});
```

**Backend Usage Example:**
```java
@Autowired
private SimpMessagingTemplate messagingTemplate;

// Send to all subscribers
messagingTemplate.convertAndSend("/topic/orders", orderUpdate);

// Send to specific user
messagingTemplate.convertAndSendToUser(
    username, 
    "/queue/notifications", 
    notification
);
```

### Future: Scale WebSocket with Redis

When you add Redis, you can upgrade to RabbitMQ or Redis-backed broker:

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Use Redis for multi-instance support
        config.enableStompBrokerRelay("/topic", "/queue")
              .setRelayHost("redis.railway.internal")
              .setRelayPort(6379);
        config.setApplicationDestinationPrefixes("/app");
    }
}
```

## 📋 Deployment Checklist

### Immediate (Works Now):
- ✅ Login/Authentication (using in-memory tokens)
- ✅ WebSocket real-time updates (single instance)
- ✅ Database operations
- ✅ File uploads
- ✅ OAuth2 Google login
- ⚠️ Email sending (needs SMTP fix)

### Add Redis (Recommended):
```bash
# In Railway
1. Add Redis database
2. Verify REDIS_PRIVATE_URL is set
3. Redeploy
4. Check logs for "✅ Redis connection successful"
```

### Fix Email (Choose One):
1. **SendGrid API** (recommended) - Add SendGrid Java SDK
2. **SMTP Port 465** - Try SSL instead of TLS
3. **Alternative Provider** - Use Resend or Mailgun

## 🧪 Testing

### Test Login (Should Work Now):
```bash
curl -X POST https://your-app.railway.app/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

Expected Response:
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "base64-encoded-token",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "role": "ROLE_USER"
  }
}
```

### Test WebSocket:
```javascript
// Should connect successfully
const socket = new SockJS('https://your-app.railway.app/ws');
```

### Test Email (After Fix):
```bash
curl -X POST https://your-app.railway.app/api/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'
```

## 📊 Monitoring

### Key Logs to Watch:

**Startup:**
```
✅ Redis connection successful          # Redis working
⚠️ Using in-memory fallback            # Redis not available (OK for now)
✅ SMTP connection test SUCCESSFUL!     # Email working
⚠️ SMTP connection test failed         # Email needs fixing
SimpleBrokerMessageHandler: Started.   # WebSocket working
```

**Runtime:**
```
Refresh token stored in Redis          # Using Redis
Refresh token stored in-memory         # Using fallback
Redis connection restored              # Redis came back online
```

## 🎯 Summary

| Feature | Status | Notes |
|---------|--------|-------|
| **Login/Auth** | ✅ Working | Using in-memory tokens |
| **WebSocket** | ✅ Working | SimpleBroker (single instance) |
| **Database** | ✅ Working | PostgreSQL connected |
| **Redis** | ⚠️ Optional | Fallback to in-memory |
| **Email** | ❌ Needs Fix | SMTP timeout issue |

**Your app is functional now!** Redis and email are enhancements, not blockers.
