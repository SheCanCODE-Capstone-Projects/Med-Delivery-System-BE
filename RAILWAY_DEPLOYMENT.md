# Railway Deployment Checklist

## Environment Variables to Set in Railway

Make sure these are configured in your Railway project settings:

### Database (PostgreSQL)
```
PGHOST=postgres.railway.internal
PGPORT=5432
PGDATABASE=railway
PGUSER=postgres
PGPASSWORD=<your-railway-postgres-password>
```

### Redis
```
REDISHOST=redis.railway.internal
REDISPORT=6379
REDISPASSWORD=<your-railway-redis-password>
```

### JWT
```
JWT_SECRET=<your-jwt-secret>
```

### Email (Gmail SMTP)
```
MAIL_USERNAME=<your-gmail>
MAIL_PASSWORD=<your-app-password>
```

### OAuth2
```
GOOGLE_CLIENT_ID=<your-google-client-id>
GOOGLE_CLIENT_SECRET=<your-google-client-secret>
OAUTH2_REDIRECT_URI=https://your-app.railway.app/login/oauth2/code/google
```

### WebSocket & CORS
```
WEBSOCKET_ALLOWED_ORIGINS=https://your-frontend.com
CORS_ALLOWED_ORIGINS=https://your-frontend.com
```

### Admin Seed
```
APP_SEED_ADMIN_EMAIL=admin@meddelivery.com
APP_SEED_ADMIN_PASSWORD=<strong-password>
APP_SEED_ENABLED=true
```

### OpenAI (Optional)
```
OPENAI_API_KEY=<your-openai-key>
```

## Important Notes

1. **Don't commit `.env` to Git** - Railway reads environment variables from its dashboard
2. **Redis is now optional** - If Redis fails, the app uses in-memory fallback
3. **Update OAuth redirect URI** - Must match your Railway domain
4. **Update CORS origins** - Must include your frontend domain
5. **Firebase service account** - Upload `firebase-service-account.json` to Railway or use base64 encoded env var

## Deployment Steps

1. Push code to GitHub
2. Connect Railway to your GitHub repo
3. Add PostgreSQL and Redis services in Railway
4. Set all environment variables in Railway dashboard
5. Deploy

## Verify Deployment

Check logs for:
- ✅ Redis connection successful
- ✅ Database migrations completed
- ✅ Application started on port 8080
