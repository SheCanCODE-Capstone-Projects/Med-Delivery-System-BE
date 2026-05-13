# 🚀 Railway Deployment Checklist - SendGrid Email

## ✅ Pre-Deployment Checklist

### 1. SendGrid Setup
- [ ] Create SendGrid account at https://sendgrid.com
- [ ] Verify sender email address in SendGrid
- [ ] Create API key with "Mail Send" permission
- [ ] Copy API key (starts with `SG.`)

### 2. Railway Environment Variables
Set these in Railway dashboard:

```bash
SENDGRID_API_KEY=SG.your-actual-api-key-here
MAIL_FROM=noreply@meddelivery.com
```

### 3. Verify Configuration
- [ ] SMTP config is commented out in `application.properties`
- [ ] SendGrid Web API config is active
- [ ] `sendgrid-java` dependency is in `pom.xml`
- [ ] All tests pass: `mvn test`

---

## 🔧 Railway Environment Variables (Complete List)

### Required for Email
```bash
SENDGRID_API_KEY=SG.xxxxxxxxxxxxxxxxxxxxx
MAIL_FROM=noreply@meddelivery.com
```

### Other Required Variables
```bash
# Database (Railway provides these automatically)
PGHOST=containers-us-west-xxx.railway.app
PGPORT=5432
PGDATABASE=railway
PGUSER=postgres
PGPASSWORD=xxxxxxxxxxxxx

# Redis (Railway provides these automatically)
REDIS_URL=redis://default:xxxxx@containers-us-west-xxx.railway.app:6379

# JWT
JWT_SECRET=your-secure-jwt-secret-key-minimum-32-chars

# OAuth2
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
OAUTH2_REDIRECT_URI=https://your-app.railway.app/login/oauth2/code/google

# CORS
CORS_ALLOWED_ORIGINS=https://your-frontend.com,https://www.your-frontend.com
WEBSOCKET_ALLOWED_ORIGINS=https://your-frontend.com,https://www.your-frontend.com

# AI (Optional)
OPENAI_API_KEY=sk-your-openai-key
```

---

## 🧪 Testing After Deployment

### 1. Check Application Logs
```bash
# In Railway dashboard, check logs for:
✅ SendGrid Email Service initialized with from: noreply@meddelivery.com
✅ SendGrid Email Service configured successfully!
```

### 2. Test Email Sending
```bash
# Register a new user
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your-test-email@example.com",
    "fullName": "Test User",
    "phoneNumber": "+1234567890"
  }'

# Check logs for:
✅ Email sent successfully to: your-test-email@example.com (Status: 202)
```

### 3. Verify Email Received
- [ ] Check inbox for OTP email
- [ ] Email should be HTML formatted
- [ ] OTP code should be visible
- [ ] Email should come from `MAIL_FROM` address

---

## 🐛 Troubleshooting

### Issue: "SendGrid API Key not configured"
**Fix:** Add `SENDGRID_API_KEY` to Railway environment variables

### Issue: "401 Unauthorized"
**Fix:** 
1. Verify API key is correct
2. Regenerate API key in SendGrid
3. Update Railway environment variable

### Issue: "Email not received"
**Fix:**
1. Check SendGrid dashboard → Activity Feed
2. Verify sender email is verified in SendGrid
3. Check spam folder
4. Review Railway logs for errors

### Issue: "403 Forbidden"
**Fix:**
1. Verify sender email is verified in SendGrid
2. Check SendGrid account status
3. Ensure API key has "Mail Send" permission

---

## 📊 Monitoring

### SendGrid Dashboard
- Monitor email delivery rates
- Check bounce/spam rates
- View email activity logs
- Track API usage

### Railway Logs
```bash
# Look for these log patterns:
✅ Email sent successfully to: user@example.com (Status: 202)
❌ Failed to send email to user@example.com: [error message]
🔑 [EMAIL FAILED] OTP for user@example.com: 123456
```

---

## 🔄 Rollback Plan (If Needed)

### Quick Rollback to SMTP
1. In Railway, add SMTP environment variables:
   ```bash
   MAIL_USERNAME=your-email@gmail.com
   MAIL_PASSWORD=your-app-password
   ```

2. In `application.properties`, uncomment SMTP config:
   ```properties
   spring.mail.host=smtp.gmail.com
   spring.mail.port=587
   spring.mail.username=${MAIL_USERNAME}
   spring.mail.password=${MAIL_PASSWORD}
   spring.mail.properties.mail.smtp.auth=true
   spring.mail.properties.mail.smtp.starttls.enable=true
   ```

3. Comment out SendGrid config:
   ```properties
   # app.sendgrid.api-key=${SENDGRID_API_KEY}
   ```

4. Redeploy application

**Note:** SMTP may not work on Railway due to port restrictions!

---

## 📈 SendGrid Pricing

### Free Tier
- 100 emails/day
- Perfect for testing and small deployments

### Essentials Plan ($19.95/month)
- 50,000 emails/month
- Email API
- Email validation

### Pro Plan ($89.95/month)
- 100,000 emails/month
- Dedicated IP
- Advanced analytics

**Recommendation:** Start with Free tier, upgrade as needed

---

## ✅ Deployment Success Criteria

- [ ] Application starts without errors
- [ ] SendGrid service initializes successfully
- [ ] Test registration sends OTP email
- [ ] Email is received in inbox
- [ ] OTP verification works
- [ ] No SMTP-related errors in logs
- [ ] SendGrid dashboard shows email activity

---

## 🎉 You're Ready!

Your application is now configured to send emails via SendGrid Web API, which works perfectly on Railway without SMTP port restrictions.

**Next Steps:**
1. Set environment variables in Railway
2. Deploy application
3. Test email sending
4. Monitor SendGrid dashboard
5. Celebrate! 🎊
