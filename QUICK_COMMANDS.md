# Quick Command Reference

## 🚀 Deployment Commands

### Deploy to Railway
```bash
git add .
git commit -m "Fix: Add comprehensive email logging and SMTP validation"
git push
```

### Check Railway Logs
```bash
railway logs
```

### Follow Railway Logs (real-time)
```bash
railway logs --follow
```

### Set Environment Variables
```bash
railway variables set MAIL_USERNAME=your-email@gmail.com
railway variables set MAIL_PASSWORD=your-app-password
```

---

## 🔍 Log Filtering Commands

### Check Email Configuration on Startup
```bash
railway logs | grep "EMAIL CONFIGURATION"
```

### Check SMTP Connection Test
```bash
railway logs | grep "SMTP connection test"
```

### Check Registration Flow
```bash
railway logs | grep "Registration request received"
```

### Check OTP Generation
```bash
railway logs | grep "OTP GENERATED"
```

### Check Email Sending
```bash
railway logs | grep "OTP email sent"
```

### Check Email Failures
```bash
railway logs | grep "EMAIL FAILED"
```

### Check All Email-Related Logs
```bash
railway logs | grep -E "(📧|🔐|👥|✅|❌)"
```

---

## 🧪 Testing Commands

### Test Registration (Email)
```bash
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "fullName": "Test User"
  }'
```

### Test Registration (Phone)
```bash
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "+1234567890",
    "fullName": "Test User"
  }'
```

### Test OTP Verification
```bash
curl -X POST https://your-app.railway.app/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test@example.com",
    "otp": "123456"
  }'
```

### Test Send OTP (Existing User)
```bash
curl -X POST https://your-app.railway.app/api/auth/send-otp \
  -H "Content-Type: application/json" \
  -d 'username=test@example.com'
```

### Health Check
```bash
curl https://your-app.railway.app/api/health
```

---

## 🔐 Gmail App Password Setup

### Step 1: Enable 2-Step Verification
```
Open: https://myaccount.google.com/security
Click: 2-Step Verification → Get Started
Follow: Setup instructions
```

### Step 2: Generate App Password
```
Open: https://myaccount.google.com/apppasswords
Select: Mail
Select: Other (Custom name)
Enter: MedDelivery Railway
Click: Generate
Copy: 16-character password (e.g., abcd efgh ijkl mnop)
```

### Step 3: Format Password (Remove Spaces)
```bash
# Original: abcd efgh ijkl mnop
# Formatted: abcdefghijklmnop
```

---

## 🔧 Railway CLI Commands

### Login to Railway
```bash
railway login
```

### Link to Project
```bash
railway link
```

### List Environment Variables
```bash
railway variables
```

### Set Variable
```bash
railway variables set KEY=VALUE
```

### Delete Variable
```bash
railway variables delete KEY
```

### Open Railway Dashboard
```bash
railway open
```

### Check Service Status
```bash
railway status
```

---

## 📊 Verification Commands

### Check if MAIL_USERNAME is Set
```bash
railway variables | grep MAIL_USERNAME
```

### Check if MAIL_PASSWORD is Set
```bash
railway variables | grep MAIL_PASSWORD
```

### Check All Mail-Related Variables
```bash
railway variables | grep MAIL
```

### Check Application Status
```bash
railway status
```

### Check Recent Deployments
```bash
railway deployments
```

---

## 🐛 Debugging Commands

### Get Last 100 Log Lines
```bash
railway logs --lines 100
```

### Get Logs from Last Hour
```bash
railway logs --since 1h
```

### Search Logs for Error
```bash
railway logs | grep -i error
```

### Search Logs for Exception
```bash
railway logs | grep -i exception
```

### Search Logs for Authentication
```bash
railway logs | grep -i authentication
```

### Get Full Stack Trace
```bash
railway logs | grep -A 20 "Full stack trace"
```

---

## 📧 Email Testing Tools

### Send Test Email via curl
```bash
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"your-email@gmail.com","fullName":"Test"}' \
  -v
```

### Check Email Headers (if received)
```
Open email → More options → Show original
Look for: SPF, DKIM, DMARC status
```

### Test SMTP Connection (local)
```bash
telnet smtp.gmail.com 587
```

---

## 🔄 Rollback Commands

### Rollback to Previous Deployment
```bash
railway rollback
```

### View Deployment History
```bash
railway deployments
```

---

## 📝 Git Commands

### Check Status
```bash
git status
```

### View Changes
```bash
git diff
```

### View Modified Files
```bash
git diff --name-only
```

### Commit All Changes
```bash
git add .
git commit -m "Your message"
```

### Push to Railway
```bash
git push
```

### View Commit History
```bash
git log --oneline
```

---

## 🎯 Quick Verification Checklist

### 1. Check Environment Variables
```bash
railway variables | grep -E "(MAIL_USERNAME|MAIL_PASSWORD)"
```

### 2. Check Startup Logs
```bash
railway logs | grep "SMTP connection test"
```

### 3. Test Registration
```bash
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","fullName":"Test"}'
```

### 4. Check Email Logs
```bash
railway logs | grep "OTP email sent"
```

### 5. Verify Email Received
```
Check inbox for: "MedDelivery - Your OTP Code"
```

---

## 🆘 Emergency Commands

### Restart Application
```bash
railway restart
```

### View Service Logs (real-time)
```bash
railway logs --follow
```

### Check Service Health
```bash
curl https://your-app.railway.app/api/health
```

### Force Redeploy
```bash
railway redeploy
```

---

## 📚 Documentation Links

- Railway Docs: https://docs.railway.app
- Gmail App Passwords: https://myaccount.google.com/apppasswords
- Spring Boot Mail: https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.email
- JavaMail API: https://javaee.github.io/javamail/

---

## 💡 Pro Tips

### Tip 1: Watch Logs During Registration
```bash
# Terminal 1: Watch logs
railway logs --follow

# Terminal 2: Test registration
curl -X POST https://your-app.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","fullName":"Test"}'
```

### Tip 2: Save OTP from Logs
```bash
railway logs | grep "OTP GENERATED" | tail -1
```

### Tip 3: Check Last Email Attempt
```bash
railway logs | grep -E "(Attempting to send|OTP email sent|FAILED to send)" | tail -5
```

### Tip 4: Monitor Email Success Rate
```bash
railway logs | grep -c "OTP email sent successfully"
railway logs | grep -c "FAILED to send OTP email"
```

---

## 🎉 Success Indicators

### ✅ Configuration Valid
```
Log contains: "✅ SMTP connection test SUCCESSFUL!"
```

### ✅ Email Sent
```
Log contains: "✅ OTP email sent successfully to: user@example.com"
```

### ✅ User Registered
```
Log contains: "✅ New patient registered with ID: 123"
```

### ✅ Full Flow Working
```
All three above indicators present in logs
```

---

## ❌ Failure Indicators

### ❌ Missing Configuration
```
Log contains: "❌ CRITICAL: MAIL_USERNAME environment variable is not set!"
```

### ❌ Authentication Failed
```
Log contains: "❌ Exception type: MailAuthenticationException"
```

### ❌ Connection Failed
```
Log contains: "❌ SMTP connection test FAILED"
```

### ❌ Email Not Sent
```
Log contains: "❌ FAILED to send OTP email to: user@example.com"
```
