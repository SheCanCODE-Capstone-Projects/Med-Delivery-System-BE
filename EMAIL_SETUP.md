# Email Setup Guide for Railway

## Problem
Gmail SMTP (smtp.gmail.com:587) is timing out on Railway because Railway's network may block outbound SMTP connections to Gmail.

## Solution: Use SendGrid (Recommended)

### Step 1: Create SendGrid Account
1. Go to https://sendgrid.com/
2. Sign up for free account (100 emails/day free)
3. Verify your email address

### Step 2: Create API Key
1. Go to Settings → API Keys
2. Click "Create API Key"
3. Name it: `MedDelivery-Railway`
4. Select "Full Access" or "Mail Send" only
5. Copy the API key (you'll only see it once!)

### Step 3: Verify Sender Identity
1. Go to Settings → Sender Authentication
2. Click "Verify a Single Sender"
3. Enter your email (e.g., `samillah.mutoni@gmail.com`)
4. Fill in the form and verify via email

### Step 4: Update Railway Environment Variables
In Railway dashboard, set:
```
SENDGRID_API_KEY=SG.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### Step 5: Update application.properties
Already done! The config now uses SendGrid:
```properties
spring.mail.host=smtp.sendgrid.net
spring.mail.port=587
spring.mail.username=apikey
spring.mail.password=${SENDGRID_API_KEY}
```

### Step 6: Update OtpService (if needed)
The "from" email must match your verified sender:

```java
message.setFrom("samillah.mutoni@gmail.com"); // Must be verified in SendGrid
```

## Alternative Solutions

### Option 2: Use Mailgun
1. Sign up at https://www.mailgun.com/ (free tier: 5,000 emails/month)
2. Get SMTP credentials
3. Update Railway env vars:
```
MAIL_HOST=smtp.mailgun.org
MAIL_PORT=587
MAIL_USERNAME=postmaster@your-domain.mailgun.org
MAIL_PASSWORD=your-mailgun-password
```

### Option 3: Use AWS SES
1. Sign up for AWS SES
2. Verify your email/domain
3. Get SMTP credentials
4. Update Railway env vars:
```
MAIL_HOST=email-smtp.us-east-1.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=your-ses-username
MAIL_PASSWORD=your-ses-password
```

### Option 4: Gmail with Port 465 (SSL)
Try Gmail with SSL instead of TLS:
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=465
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.ssl.enable=true
spring.mail.properties.mail.smtp.socketFactory.port=465
spring.mail.properties.mail.smtp.socketFactory.class=javax.net.ssl.SSLSocketFactory
```

## Testing

After setup, test by:
1. Deploy to Railway
2. Try OTP login endpoint
3. Check Railway logs for email success
4. Check SendGrid dashboard for delivery stats

## For Local Development

Keep Gmail for local:
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
```

## Troubleshooting

**SendGrid emails not arriving?**
- Check SendGrid dashboard for delivery status
- Verify sender email is authenticated
- Check spam folder
- Ensure API key has "Mail Send" permission

**Still timing out?**
- Railway might be blocking all SMTP ports
- Use SendGrid's Web API instead of SMTP (requires code changes)
- Contact Railway support about SMTP restrictions
