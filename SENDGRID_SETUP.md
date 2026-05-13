# SendGrid Setup for Railway

## Step 1: Create SendGrid Account
1. Go to https://sendgrid.com/
2. Click "Start for Free"
3. Sign up (100 emails/day free forever)
4. Verify your email

## Step 2: Create API Key
1. Login to SendGrid dashboard
2. Go to **Settings** → **API Keys**
3. Click **Create API Key**
4. Name: `MedDelivery-Railway`
5. Permissions: Select **Full Access** or **Mail Send** (restricted)
6. Click **Create & View**
7. **COPY THE KEY NOW** (you won't see it again!)

Example key format:
```
SG.xxxxxxxxxxxxxxxxxxxxxxxx.yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
```

## Step 3: Verify Sender Email
1. Go to **Settings** → **Sender Authentication**
2. Click **Verify a Single Sender**
3. Fill in the form:
   - From Name: `MedDelivery`
   - From Email: `samillah.mutoni@gmail.com` (or your email)
   - Reply To: Same as above
   - Company Address: Your address
4. Click **Create**
5. Check your email and click verification link

## Step 4: Set Railway Environment Variables

In Railway dashboard, add these variables:

```bash
SENDGRID_API_KEY=SG.xxxxxxxxxxxxxxxxxxxxxxxx.yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
MAIL_FROM=samillah.mutoni@gmail.com
```

**Important:** `MAIL_FROM` must match the verified sender email from Step 3!

## Step 5: Deploy

Push your code to Railway. The app will now use SendGrid.

## Testing

1. Deploy to Railway
2. Call the OTP endpoint:
```bash
POST https://your-app.railway.app/api/auth/otp/send
{
  "username": "test@example.com"
}
```
3. Check SendGrid dashboard → **Activity** to see email delivery status

## Troubleshooting

**Emails not sending?**
- Check SendGrid dashboard → Activity for errors
- Verify `MAIL_FROM` matches verified sender
- Check API key has "Mail Send" permission
- Look at Railway logs for errors

**Emails going to spam?**
- Add SPF/DKIM records (SendGrid → Sender Authentication → Domain Authentication)
- Use a custom domain instead of Gmail

**Still using Gmail locally?**
Create `application-local.properties`:
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
```

Run with: `mvn spring-boot:run -Dspring.profiles.active=local`

## SendGrid Dashboard

Monitor your emails:
- **Activity**: See all sent emails and delivery status
- **Statistics**: Open rates, click rates, bounces
- **Suppressions**: Bounced/blocked emails

## Free Tier Limits

- 100 emails/day
- 3,000 emails/month
- No credit card required
- Upgrade anytime for more volume

## Alternative: SendGrid Web API

If SMTP still doesn't work, use SendGrid's HTTP API instead (requires code changes).
