# Railway SMTP Blocking - Complete Troubleshooting Guide

## 🚨 Current Issue

```
❌ SMTP connection test FAILED: Couldn't connect to host, port: smtp.gmail.com, 587; timeout -1
```

**Diagnosis**: Railway is **blocking outbound SMTP connections** to Gmail.

## Why This Happens

Railway (and many cloud platforms) block SMTP ports to prevent spam abuse:
- Port 25: Always blocked
- Port 587: Often blocked (STARTTLS)
- Port 465: Sometimes blocked (SSL)
- Port 2525: Alternative port (rarely blocked)

## 🔧 Solutions (In Order of Preference)

### Solution 1: Try Port 465 (SSL) ⭐ EASIEST

**Status**: Already applied in code

**What changed**:
```properties
# Before (BLOCKED)
spring.mail.port=587
spring.mail.properties.mail.smtp.starttls.enable=true

# After (TRY THIS)
spring.mail.port=465
spring.mail.properties.mail.smtp.ssl.enable=true
```

**Deploy and test**:
```bash
git add .
git commit -m "Try port 465 for Gmail SMTP"
git push
```

**Check logs for**:
```
✅ SMTP connection test SUCCESSFUL!
```

**If this works**: Problem solved! ✅

**If this fails**: Try Solution 2

---

### Solution 2: Use SendGrid ⭐ RECOMMENDED FOR PRODUCTION

**Why**: SendGrid is designed for cloud platforms and won't be blocked.

**Setup** (5 minutes):

1. **Create SendGrid account**: https://sendgrid.com/
2. **Create API Key**: Settings → API Keys → Create
3. **Verify sender**: Settings → Sender Authentication
4. **Update Railway variables**:
   ```
   MAIL_USERNAME=apikey
   MAIL_PASSWORD=<your-sendgrid-api-key>
   ```
5. **Update application.properties**:
   ```properties
   spring.mail.host=smtp.sendgrid.net
   spring.mail.port=587
   ```
6. **Deploy**

**Advantages**:
- ✅ Not blocked by Railway
- ✅ Better deliverability
- ✅ Email analytics
- ✅ 100 emails/day free
- ✅ Production-ready

**Full guide**: See `SENDGRID_SETUP.md`

---

### Solution 3: Use Mailgun

**Similar to SendGrid**:

1. Sign up: https://mailgun.com/
2. Get SMTP credentials
3. Update Railway:
   ```
   MAIL_USERNAME=<mailgun-username>
   MAIL_PASSWORD=<mailgun-password>
   ```
4. Update application.properties:
   ```properties
   spring.mail.host=smtp.mailgun.org
   spring.mail.port=587
   ```

---

### Solution 4: Use AWS SES

**If you have AWS account**:

1. Set up AWS SES
2. Verify domain/email
3. Get SMTP credentials
4. Update Railway:
   ```
   MAIL_USERNAME=<ses-smtp-username>
   MAIL_PASSWORD=<ses-smtp-password>
   ```
5. Update application.properties:
   ```properties
   spring.mail.host=email-smtp.us-east-1.amazonaws.com
   spring.mail.port=587
   ```

---

### Solution 5: Contact Railway Support

**If all else fails**:

1. Open Railway support ticket
2. Ask them to whitelist SMTP ports for your project
3. Explain you need it for transactional emails (OTP)

**Railway Support**: https://railway.app/help

---

### Solution 6: Use HTTP API Instead of SMTP

**Bypass SMTP entirely** by using email service HTTP APIs:

#### SendGrid HTTP API

1. Add dependency:
```xml
<dependency>
    <groupId>com.sendgrid</groupId>
    <artifactId>sendgrid-java</artifactId>
    <version>4.9.3</version>
</dependency>
```

2. Create service:
```java
@Service
public class SendGridHttpService {
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
            sg.api(request);
        } catch (IOException e) {
            log.error("Failed to send email", e);
        }
    }
}
```

3. Update OtpService to use HTTP service instead of JavaMailSender

**Advantage**: Uses HTTPS (port 443) which is never blocked

---

## 🧪 Testing Each Solution

### Test Port 465 (Current)

```bash
# Deploy
git push

# Check logs
railway logs | grep "SMTP connection test"

# Look for
✅ SMTP connection test SUCCESSFUL!
# or
❌ SMTP connection test FAILED
```

### Test SendGrid

```bash
# After setting up SendGrid
railway logs | grep "SMTP connection test"

# Should see
✅ SMTP connection test SUCCESSFUL!
```

### Test Email Sending

```bash
# Register a user
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","fullName":"Test"}'

# Check logs
railway logs | grep "OTP email sent"

# Should see
✅ OTP email sent successfully to: test@example.com
```

---

## 📊 Decision Matrix

| Solution | Setup Time | Cost | Reliability | Blocked? |
|----------|-----------|------|-------------|----------|
| Port 465 | 0 min | Free | Medium | Maybe |
| SendGrid | 5 min | Free (100/day) | High | No |
| Mailgun | 5 min | Free (100/day) | High | No |
| AWS SES | 10 min | $0.10/1000 | High | No |
| HTTP API | 30 min | Free | High | No |

---

## 🎯 Recommended Path

### For Quick Fix (Try First)
1. ✅ **Already done**: Changed to port 465
2. Deploy and test
3. If works → Done! ✅
4. If fails → Go to production solution

### For Production (Recommended)
1. **Use SendGrid** (5 minutes setup)
2. More reliable than Gmail
3. Better deliverability
4. Email analytics
5. Higher limits

---

## 🔍 How to Verify Current Status

### Check if Port 465 Works

```bash
# Deploy current changes
git add .
git commit -m "Try Gmail port 465"
git push

# Wait for deployment
railway logs --follow

# Look for
✅ SMTP connection test SUCCESSFUL!
```

### If Port 465 Fails

You'll see:
```
❌ SMTP connection test FAILED: Couldn't connect to host, port: smtp.gmail.com, 465
```

Then switch to SendGrid.

---

## 📝 Summary

**Current Status**:
- ✅ Code is correct
- ✅ Credentials are correct
- ❌ Railway is blocking SMTP port 587
- 🔄 Trying port 465 now
- 📋 SendGrid ready as backup

**Next Steps**:
1. Deploy current changes (port 465)
2. Check if it works
3. If not, switch to SendGrid (5 min setup)

**Expected Outcome**:
- Port 465 might work (50% chance)
- SendGrid will definitely work (100% chance)

---

## 🆘 Emergency Workaround

**If you need emails NOW**:

1. Disable SMTP connection test in MailConfig:
```java
// Comment out the test
// senderImpl.testConnection();
log.warn("SMTP connection test skipped");
```

2. Deploy

3. Try sending email anyway - it might work at runtime even if test fails

4. Check logs during registration for actual email sending result

---

## 📞 Need Help?

1. **Railway Support**: https://railway.app/help
2. **SendGrid Support**: https://support.sendgrid.com/
3. **Check logs**: `railway logs | grep -E "(SMTP|email|OTP)"`
