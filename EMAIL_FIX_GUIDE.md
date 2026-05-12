# Email Configuration Fix for Railway Deployment

## Problem Identified

The application was not sending verification emails because:
1. **Missing or incorrect SMTP credentials** in Railway environment variables
2. **Silent exception handling** - email failures were caught but not properly logged
3. **No SMTP configuration validation** on startup

## Root Cause

The `MAIL_USERNAME` and `MAIL_PASSWORD` environment variables were either:
- Not set in Railway
- Set incorrectly (using regular Gmail password instead of App Password)
- Not being read by Spring Boot mail configuration

## Solution Applied

### 1. Enhanced Logging
- Added comprehensive logging in `OtpService.sendOtpEmail()`
- Added logging in `AuthService.registerPatient()`
- Added logging in `AuthController.register()`
- All email-related operations now log before/after execution

### 2. SMTP Configuration Validation
- Created `MailConfig.java` that validates SMTP settings on startup
- Tests SMTP connection during application startup
- Logs detailed diagnostics about mail configuration

### 3. Better Exception Handling
- Full stack traces are now logged for email failures
- Configuration issues are clearly identified in logs

## Railway Environment Variables Setup

### Required Environment Variables

Set these in your Railway project:

```bash
# Gmail SMTP Configuration
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password-here

# Other existing variables
PGHOST=postgres.railway.internal
PGPORT=5432
PGDATABASE=railway
PGUSER=postgres
PGPASSWORD=your-postgres-password

REDISHOST=redis.railway.internal
REDISPORT=6379
REDISPASSWORD=

JWT_SECRET=your-jwt-secret

GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
OAUTH2_REDIRECT_URI=https://your-domain.railway.app/login/oauth2/code/google

CORS_ALLOWED_ORIGINS=https://your-frontend.com
WEBSOCKET_ALLOWED_ORIGINS=https://your-frontend.com
```

### How to Generate Gmail App Password

1. **Enable 2-Step Verification** on your Google Account:
   - Go to https://myaccount.google.com/security
   - Enable "2-Step Verification"

2. **Generate App Password**:
   - Go to https://myaccount.google.com/apppasswords
   - Select "Mail" and "Other (Custom name)"
   - Name it "MedDelivery Railway"
   - Click "Generate"
   - Copy the 16-character password (no spaces)

3. **Set in Railway**:
   ```
   MAIL_USERNAME=your-email@gmail.com
   MAIL_PASSWORD=abcd efgh ijkl mnop  (remove spaces: abcdefghijklmnop)
   ```

### Alternative: Use SendGrid or AWS SES

For production, consider using a dedicated email service:

#### SendGrid Configuration
```properties
spring.mail.host=smtp.sendgrid.net
spring.mail.port=587
spring.mail.username=apikey
spring.mail.password=YOUR_SENDGRID_API_KEY
```

#### AWS SES Configuration
```properties
spring.mail.host=email-smtp.us-east-1.amazonaws.com
spring.mail.port=587
spring.mail.username=YOUR_SMTP_USERNAME
spring.mail.password=YOUR_SMTP_PASSWORD
```

## Expected Logs After Fix

### On Application Startup
```
============================================================
📧 EMAIL CONFIGURATION VALIDATION
============================================================
📧 Environment Variables:
   - MAIL_USERNAME: ✅ SET
   - MAIL_PASSWORD: ✅ SET (length: 16)
📧 Spring Mail Properties:
   - spring.mail.host: smtp.gmail.com
   - spring.mail.port: 587
   - spring.mail.username: your-email@gmail.com
   - spring.mail.password: SET (length: 16)
📧 Testing SMTP connection...
✅ SMTP connection test SUCCESSFUL!
============================================================
```

### During Registration
```
👥 Registration request received for: user@example.com
👥 Full name: John Doe
👥 Calling authService.registerPatient()...
👥 Starting patient registration process...
👥 Creating new user account...
👥 Saving user to database...
✅ New patient registered with ID: 123 (John Doe)
👥 Calling otpService.sendOtp() for new user: user@example.com
🔐 sendOtp() called for username: user@example.com
🔐 OTP generated: 123456
🔐 OTP saved to cache/Redis
🔑 [OTP GENERATED] Username: user@example.com | OTP: 123456 (expires in 5 minutes)
🔐 Detected email address, sending OTP via email...
📧 Attempting to send verification email to: user@example.com
📧 Mail sender configured: YES
📧 Creating email message...
📧 From email: your-email@gmail.com
📧 Connecting to SMTP server...
📧 SMTP Host: smtp.gmail.com
📧 SMTP Port: 587
✅ OTP email sent successfully to: user@example.com
🔐 sendOtp() completed for: user@example.com
✅ OTP sending process completed
✅ Patient registration completed successfully
✅ Registration successful: Registration successful. OTP sent to your email
```

### If Email Fails
```
❌ FAILED to send OTP email to: user@example.com
❌ Exception type: org.springframework.mail.MailAuthenticationException
❌ Error message: Authentication failed
❌ Full stack trace: [detailed stack trace]
🔑 [EMAIL FAILED] OTP for user@example.com: 123456 (expires in 5 minutes)
❌ SMTP Configuration Check:
   - MAIL_USERNAME env: NOT SET
   - MAIL_PASSWORD env: NOT SET
```

## Testing the Fix

### 1. Deploy to Railway
```bash
git add .
git commit -m "Fix: Add comprehensive email logging and SMTP validation"
git push
```

### 2. Check Railway Logs
```bash
railway logs
```

Look for:
- Email configuration validation on startup
- SMTP connection test results
- Detailed email sending logs during registration

### 3. Test Registration
```bash
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "fullName": "Test User"
  }'
```

### 4. Check Logs for OTP
If email fails, the OTP will be logged:
```
🔑 [EMAIL FAILED] OTP for test@example.com: 123456 (expires in 5 minutes)
```

## Troubleshooting

### Issue: "MAIL_USERNAME env: NOT SET"
**Solution**: Set `MAIL_USERNAME` in Railway environment variables

### Issue: "Authentication failed"
**Solution**: 
- Use App Password, not regular Gmail password
- Ensure 2-Step Verification is enabled
- Remove spaces from App Password

### Issue: "Connection refused"
**Solution**: 
- Check Railway firewall allows outbound SMTP (port 587)
- Try port 465 with SSL instead of STARTTLS

### Issue: "SMTP connection test FAILED"
**Solution**:
- Verify credentials are correct
- Check Gmail account isn't locked
- Try generating a new App Password

## Files Modified

1. `src/main/java/com/meddelivery/service/OtpService.java`
   - Enhanced `sendOtpEmail()` with detailed logging
   - Enhanced `sendOtp()` with flow logging
   - Added SMTP configuration diagnostics

2. `src/main/java/com/meddelivery/service/AuthService.java`
   - Enhanced `registerPatient()` with detailed logging
   - Better exception handling and logging

3. `src/main/java/com/meddelivery/controller/AuthController.java`
   - Enhanced `register()` endpoint with logging

4. `src/main/java/com/meddelivery/config/MailConfig.java` (NEW)
   - Validates SMTP configuration on startup
   - Tests SMTP connection
   - Provides detailed diagnostics

## Next Steps

1. Set `MAIL_USERNAME` and `MAIL_PASSWORD` in Railway
2. Redeploy the application
3. Check startup logs for email configuration validation
4. Test user registration
5. Verify emails are being sent

## Production Recommendations

1. **Use a dedicated email service** (SendGrid, AWS SES, Mailgun)
2. **Enable email rate limiting** (already implemented)
3. **Add email templates** with HTML formatting
4. **Implement email queue** for async processing
5. **Monitor email delivery rates**
6. **Set up email bounce handling**
