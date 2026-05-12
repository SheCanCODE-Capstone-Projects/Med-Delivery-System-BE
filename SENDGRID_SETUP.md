# SendGrid Setup Guide (Alternative to Gmail)

## Why SendGrid?

Railway is blocking outbound SMTP connections to Gmail (port 587 and possibly 465). SendGrid is a dedicated email service that works reliably on cloud platforms like Railway.

## Setup Steps

### 1. Create SendGrid Account
1. Go to https://sendgrid.com/
2. Sign up for free account (100 emails/day free)
3. Verify your email address

### 2. Create API Key
1. Go to Settings → API Keys
2. Click "Create API Key"
3. Name it "MedDelivery Railway"
4. Select "Full Access" or "Mail Send" only
5. Click "Create & View"
6. **Copy the API key** (you won't see it again)

### 3. Verify Sender Identity
1. Go to Settings → Sender Authentication
2. Click "Verify a Single Sender"
3. Enter your email (samillah.mutoni@gmail.com)
4. Fill in the form
5. Check your email and click verification link

### 4. Update Railway Environment Variables

```bash
MAIL_USERNAME=apikey
MAIL_PASSWORD=<your-sendgrid-api-key>
```

**Important**: The username is literally the word "apikey", not your email!

### 5. Update application.properties

```properties
#EMAIL (OTP) - SendGrid Configuration

spring.mail.host=smtp.sendgrid.net
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### 6. Deploy

```bash
git add .
git commit -m "Switch to SendGrid for email delivery"
git push
```

## Expected Logs After SendGrid Setup

```
📧 EMAIL CONFIGURATION VALIDATION
📧 Environment Variables:
   - MAIL_USERNAME: ✅ SET
   - MAIL_PASSWORD: ✅ SET (length: 69)
📧 Spring Mail Properties:
   - spring.mail.host: smtp.sendgrid.net
   - spring.mail.port: 587
   - spring.mail.username: apikey
📧 Testing SMTP connection...
✅ SMTP connection test SUCCESSFUL!
```

## Advantages of SendGrid

1. ✅ **Not blocked by Railway** - Designed for cloud platforms
2. ✅ **Better deliverability** - Dedicated email infrastructure
3. ✅ **Email analytics** - Track opens, clicks, bounces
4. ✅ **Higher limits** - 100 emails/day free (vs Gmail's strict limits)
5. ✅ **No 2FA required** - Just API key
6. ✅ **Production-ready** - Used by major companies

## Testing

After setup, test registration:

```bash
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","fullName":"Test User"}'
```

Check logs for:
```
✅ OTP email sent successfully to: test@example.com
```

## Troubleshooting

### Issue: "550 The from address does not match a verified Sender Identity"
**Solution**: Verify your sender email in SendGrid dashboard

### Issue: "Authentication failed"
**Solution**: 
- Ensure username is "apikey" (not your email)
- Regenerate API key if needed
- Check API key has "Mail Send" permission

### Issue: Still can't connect
**Solution**: Railway might be blocking all SMTP. Contact Railway support or use SendGrid's HTTP API instead.

## Alternative: SendGrid HTTP API

If SMTP is completely blocked, use SendGrid's HTTP API:

1. Add dependency to pom.xml:
```xml
<dependency>
    <groupId>com.sendgrid</groupId>
    <artifactId>sendgrid-java</artifactId>
    <version>4.9.3</version>
</dependency>
```

2. Create SendGridEmailService:
```java
@Service
public class SendGridEmailService {
    
    @Value("${sendgrid.api.key}")
    private String apiKey;
    
    public void sendEmail(String to, String subject, String body) {
        Email from = new Email("noreply@meddelivery.com");
        Email toEmail = new Email(to);
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, toEmail, content);
        
        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            log.info("Email sent: {}", response.getStatusCode());
        } catch (IOException e) {
            log.error("Failed to send email", e);
        }
    }
}
```

This bypasses SMTP entirely and uses HTTPS (port 443) which is never blocked.
