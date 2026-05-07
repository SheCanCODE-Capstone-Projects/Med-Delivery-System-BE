# Deployment Guide - MedDelivery Spring Boot Application

This guide covers the complete deployment process for the MedDelivery Spring Boot backend application to Railway production environments.

## Overview

The MedDelivery application is a Spring Boot 4.0.5 backend service for medical prescription management and delivery coordination. It uses PostgreSQL 15 for data persistence, Redis 7 for caching and session management, and includes email/SMS notification capabilities.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Railway Project Setup](#railway-project-setup)
- [Environment Variables](#environment-variables)
- [GitHub Secrets Configuration](#github-secrets-configuration)
- [Deployment Process](#deployment-process)
- [Database Migrations](#database-migrations)
- [Post-Deployment Verification](#post-deployment-verification)
- [Rollback Procedures](#rollback-procedures)
- [Monitoring & Logs](#monitoring--logs)
- [CI/CD Pipeline](#cicd-pipeline)

---

## Prerequisites

### Required Accounts & Tools

- [x] [Railway](https://railway.app) account (with payment method configured for production)
- [x] [GitHub](https://github.com) account with repository admin access
- [x] PostgreSQL 15 database (managed via Railway plugin)
- [x] Redis 7 instance (managed via Railway plugin)
- [x] Email service provider (Gmail SMTP or SendGrid recommended)
- [x] Firebase Admin SDK credentials (for phone OTP)
- [x] SSL/TLS certificates (handled by Railway automatically)

### Required Software Versions

| Component | Minimum Version | Production Version |
|-----------|----------------|-------------------|
| Java JDK | 21 | 21 (Temurin) |
| Maven | 3.8.6 | 3.9.x |
| Spring Boot | 4.0.0 | 4.0.5 |
| PostgreSQL | 14 | 15 |
| Redis | 7.0 | 7.x |
| Node.js | 18+ | - (for build tools) |

---

## Railway Project Setup

### Step 1: Create Railway Project

1. Log in to [Railway](https://railway.app)
2. Click **New Project** → **Deploy from GitHub Repository**
3. Select the `meddelivery-be` repository
4. Choose the branch to deploy (main for production)
5. Railway will automatically detect it's a Java/Maven project

### Step 2: Add PostgreSQL Plugin

1. Navigate to your project dashboard
2. Click **Add Plugin**
3. Search for **PostgreSQL**
4. Select **PostgreSQL by Railway**
5. Configure:
   - Plan: **Hobby** ($0) or **Pro** ($8/mo) - start with Hobby
   - Database Name: `meddelivery`
   - PostgreSQL Version: **15**
6. Click **Add**
7. Note the connection details - Railway provides:
   - `DATABASE_URL` (auto-generated)
   - `POSTGRES_USER`
   - `POSTGRES_PASSWORD`
   - `POSTGRES_HOST`
   - `POSTGRES_PORT`

### Step 3: Add Redis Plugin

1. Click **Add Plugin** again
2. Search for **Redis**
3. Select **Redis by Railway** (Upstash or Redis Cloud)
4. Configure:
   - Plan: **Free** (Upstash) or **Pro** ($15/mo)
   - Region: Same as PostgreSQL (lower latency)
5. Click **Add**
6. Note the Redis connection URL

### Step 4: Configure Domain (Optional)

1. Click **Settings** → **Domains**
2. Add custom domain (e.g., `api.meddelivery.com`)
3. Configure SSL (Railway provides automatic HTTPS)
4. Update DNS records (CNAME to Railway endpoint)

### Step 5: Set Service Resources

1. Click **Settings** → **Service**
2. Configure:
   - **CPU**: 0.5 vCPU (min)
   - **Memory**: 1 GB (min)
   - **Disk**: 1 GB SSD
   - **Auto-redeploy**: Enabled
3. For production, recommend:
   - **CPU**: 1-2 vCPU
   - **Memory**: 2-4 GB
   - **Max Instances**: 3 (horizontal scaling)

---

## Environment Variables

All environment variables should be configured in Railway → **Settings** → **Variables**.

### Production Environment Variables

| Variable | Description | Example Value | Required |
|----------|-------------|---------------|----------|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/meddelivery` | ✅ Yes |
| `DB_USERNAME` | Database username | `postgres` | ✅ Yes |
| `DB_PASSWORD` | Database password | `super-secret-password` | ✅ Yes |
| `REDIS_HOST` | Redis hostname | `localhost` | ✅ Yes |
| `REDIS_PORT` | Redis port | `6379` | ✅ Yes |
| `JWT_SECRET` | JWT signing secret (min 32 chars) | `super-secret-jwt-key-1234567890-abc` | ✅ Yes |
| `MAIL_USERNAME` | SMTP username | `noreply@meddelivery.com` | ✅ Yes |
| `MAIL_PASSWORD` | SMTP password/App password | `smtp-password-or-app-token` | ✅ Yes |
| `GOOGLE_CLIENT_ID` | OAuth2 Google Client ID | `123456-abc.apps.googleusercontent.com` | ✅ Yes |
| `GOOGLE_CLIENT_SECRET` | OAuth2 Google Client Secret | `GOCSPX-secret-key` | ✅ Yes |
| `MICROSOFT_CLIENT_ID` | OAuth2 Microsoft Client ID | `12345678-1234-1234-...` | ❌ No |
| `MICROSOFT_CLIENT_SECRET` | OAuth2 Microsoft Client Secret | `secret` | ❌ No |
| `OPENAI_API_KEY` | OpenAI API key (for AI features) | `sk-proj-...` | ❌ No |
| `OAUTH2_REDIRECT_URI` | OAuth2 redirect URI | `https://api.meddelivery.com/login/oauth2/code/google` | ❌ No |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins | `https://app.meddelivery.com,https://admin.meddelivery.com` | ❌ No |
| `WEBSOCKET_ALLOWED_ORIGINS` | WebSocket allowed origins | `https://app.meddelivery.com` | ❌ No |
| `APP_CORS_ALLOWED_ORIGINS` | CORS origins (Spring format) | `https://app.meddelivery.com` | ❌ No |
| `RAILWAY_ENVIRONMENT` | Railway environment | `production` | ✅ Yes |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `prod` | ❌ No |
| `LOGGING_LEVEL_COM_MEDDELIVERY` | App log level | `INFO` | ❌ No |
| `RATELIMIT_OTP_SEND_MAX` | OTP rate limit max | `3` | ❌ No |
| `OTP_EXPIRATION_MINUTES` | OTP expiration | `5` | ❌ No |

### Security-Sensitive Variables

These require special attention:

#### JWT Secret (Critical)
```bash
# Generate a secure random secret (32+ characters)
openssl rand -base64 32
# Example output: kx9vN2pQ8rT4wY6zA1bC3dE5fG7hJ9mN2pQ4rT6vX8z
```

Store this in Railway variables - **never commit to git**.

#### Database Password
- Use Railway's auto-generated password or create a strong one
- Minimum 16 characters with mixed case, numbers, symbols
- Rotate every 90 days

#### SMTP Credentials
- Use app-specific password for Gmail (not your main password)
- For production, use SendGrid or Mailgun instead of Gmail
- Enable 2FA on the email account

### Railway Variable Configuration

1. Go to Railway → Project → Settings → Variables
2. Click **Add Variable** for each required variable
3. Set Environment (Production/Staging)
4. For secrets, use Railway's encrypted variable storage

**Tip**: Use `.env.example` file as a template for local development.

---

## GitHub Secrets Configuration

The CI/CD pipeline requires the following GitHub repository secrets:

### Required Secrets

| Secret Name | Description | Value Example |
|-------------|-------------|---------------|
| `RAILWAY_TOKEN` | Railway API token for deployment | `eyJhbGciOiJIUzI1NiIsInR5cCI6...` |
| `RAILWAY_PROJECT_ID` | Railway project ID | `12345678-abcd-1234-efgh-123456789012` |
| `RAILWAY_STAGING_PROJECT_ID` | Staging project ID (optional) | `87654321-hgfedcba-4321-...` |

### Optional Secrets

| Secret Name | Description | Purpose |
|-------------|-------------|---------|
| `DOCKERHUB_TOKEN` | Docker Hub access token | Alternative to GHCR |
| `SENTRY_DSN` | Sentry error tracking | Error monitoring |
| `DATADOG_API_KEY` | Datadog monitoring | Performance metrics |

### How to Set GitHub Secrets

**Option 1: Via GitHub UI**

1. Navigate to your repository on GitHub
2. Go to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Enter name (e.g., `RAILWAY_TOKEN`) and value
5. Click **Add secret**

**Option 2: Via GitHub CLI**

```bash
gh secret set RAILWAY_TOKEN --body "your-railway-token"
gh secret set RAILWAY_PROJECT_ID --body "your-project-id"
```

### Getting Railway Token

1. Log in to Railway
2. Go to [Account Settings](https://railway.app/account)
3. Scroll to **API Tokens**
4. Click **Create Token**
5. Name it (e.g., "GitHub Actions CI/CD")
6. Copy the token (you won't see it again!)
7. Add to GitHub secrets

### Getting Railway Project ID

1. Go to your Railway project
2. Click **Settings** → **General**
3. Copy the **Project ID** (UUID format)
4. Add to GitHub secrets as `RAILWAY_PROJECT_ID`

---

## Deployment Process

### Automated Deployment (Recommended)

The CI/CD pipeline automatically deploys when:

1. Code is pushed to `main` branch → Production deployment
2. Code is pushed to `develop` branch → Staging deployment
3. Pull request to `main` → Tests run (no deployment)

### Manual Deployment

If you need to deploy manually:

```bash
# Deploy to production
gh workflow run deploy-prod.yml --ref main

# Deploy to staging
gh workflow run deploy-staging.yml --ref develop
```

### Step-by-Step Deployment Flow

#### 1. Development Phase
- Developers work on `feature/*` branches
- Code is merged to `develop` via pull requests
- Tests run on PR creation

#### 2. Staging Deployment
- Push to `develop` triggers staging deployment
- Staging environment deployed to Railway
- QA team tests in staging
- Database migrations applied automatically

#### 3. Production Deployment
- After staging approval, merge `develop` → `main`
- Push to `main` triggers production deployment
- Production environment updated
- Health checks verify deployment

#### 4. Post-Deployment
- Monitor logs and metrics
- Verify API endpoints
- Run smoke tests

### Deployment Checklist

- [ ] All tests passing on develop branch
- [ ] Code review completed
- [ ] Database migrations reviewed
- [ ] Environment variables configured
- [ ] GitHub secrets verified
- [ ] Backup created (if needed)
- [ ] Rollback plan ready
- [ ] Team notified of deployment
- [ ] Maintenance window scheduled (if required)

---

## Database Migrations

The application uses Flyway for database migration management.

### Migration Files

Migrations are located in: `src/main/resources/db/migration/`

| File | Description |
|------|-------------|
| `V1__init.sql` | Initial schema creation |
| `V2__add_missing_columns.sql` | Add missing columns |
| `V3__add_license_number_to_pharmacies.sql` | Pharmacy license support |
| `V4__add_extended_fields_to_patient_profiles.sql` | Patient profile extensions |
| `V5__add_timestamps_to_patient_profiles.sql` | Audit timestamps |
| `V6__convert_location_to_multiple.sql` | Multi-location support |
| `V7__add_prescription_validation_columns.sql` | Prescription validation |
| `V8__add_payment_tables_and_insurance_coverage.sql` | Payment system |
| `V9__add_user_profile_fields.sql` | User profile enhancements |
| `V10__add_prescription_expiry_date.sql` | Prescription expiry |

### Migration Best Practices

#### Creating a New Migration

1. Create new SQL file following naming convention:
   ```
   V{version}__{description}.sql
   ```
   Example: `V11__add_medication_history.sql`

2. Write idempotent SQL:
   ```sql
   -- Good: Check if column exists before adding
   DO $$ BEGIN
       IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                      WHERE table_name='patients' AND column_name='new_column') THEN
           ALTER TABLE patients ADD COLUMN new_column VARCHAR(255);
       END IF;
   END $$;
   ```

3. Test locally:
   ```bash
   mvn flyway:migrate -Dspring.profiles.active=dev
   ```

4. Verify rollback (if needed):
   ```sql
   -- Create undo migration if complex
   V11__add_medication_history_undo.sql
   ```

5. Commit migration with application code

### Running Migrations

#### Automatic (Production)

Migrations run automatically on application startup:
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    validate-on-migrate: true
```

#### Manual (Staging/Testing)

```bash
# Run migrations
mvn flyway:migrate

# Validate migrations
mvn flyway:validate

# Check migration status
mvn flyway:info

# Undo last migration (use with caution!)
mvn flyway:undo
```

#### Via CI/CD

The pipeline automatically validates and applies migrations:

```bash
# In CI/CD pipeline
mvn flyway:validate -DskipTests
mvn flyway:migrate -DskipTests
```

### Migration Validation in CI/CD

The pipeline performs these checks:

1. **Migration Validation**: Ensures migrations can be applied
   ```bash
   mvn flyway:validate
   ```

2. **Migration Application**: Applies migrations to test DB
   ```bash
   mvn flyway:migrate
   ```

3. **Integration Tests**: Runs tests with migrated schema
   ```bash
   mvn test
   ```

### Migration Safety

#### DOs

✅ Test migrations on staging first  
✅ Backup production database before major changes  
✅ Use idempotent SQL where possible  
✅ Keep migrations small and focused  
✅ Add indexes concurrently for large tables  
✅ Document breaking changes  
✅ Version control all migrations  

#### DON'Ts

❌ Don't modify existing migrations (create new ones)  
❌ Don't drop columns without migration path  
❌ Don't run destructive migrations without backup  
❌ Don't forget to update test schemas  
❌ Don't run migrations during peak hours  

### Rollback Strategy

If a migration causes issues:

1. **Immediate Rollback**:
   ```bash
   # Restore from backup
   pg_restore -h hostname -U username -d database backup_file.dump
   ```

2. **Code Rollback**:
   ```bash
   # Revert to previous version
   git revert <bad-commit>
   # Redeploy
   ```

3. **Flyway Repair** (if metadata corrupted):
   ```bash
   mvn flyway:repair
   ```

### Monitoring Migrations

Check migration status:
```bash
# Via Flyway
mvn flyway:info

# Via PostgreSQL
SELECT * FROM flyway_schema_history;
```

---

## Post-Deployment Verification

After deployment, verify the following:

### Health Checks

```bash
# Basic health
curl https://api.meddelivery.com/health

# Expected: {"status": "UP"}

# Database connectivity
curl https://api.meddelivery.com/health/db

# Expected: {"status": "UP", "database": "PostgreSQL", "connected": true}

# Redis connectivity
curl https://api.meddelivery.com/health/redis

# Expected: {"status": "UP", "redis": "connected": true}
```

### API Endpoints

Verify critical endpoints:

```bash
# Swagger documentation
curl https://api.meddelivery.com/swagger-ui.html

# Actuator info
curl https://api.meddelivery.com/actuator/info

# Application readiness
curl https://api.meddelivery.com/actuator/health

# API docs
curl https://api.meddelivery.com/api-docs
```

### Smoke Tests

Run basic functionality tests:

```bash
# Test authentication
curl -X POST https://api.meddelivery.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"testpass"}'

# Test public endpoints
curl https://api.meddelivery.com/api/public/medicines

# Test WebSocket connection
# (Use WebSocket client to connect)
```

### Log Verification

Check application logs:

```bash
# Via Railway CLI
railway logs --tail=100

# Via GitHub Actions
# Check deployment workflow logs
```

Look for:
- No ERROR or FATAL log entries
- Successful startup messages
- Database connection established
- Redis connection established
- No migration errors

### Performance Checks

```bash
# Response time check
curl -w "@curl-format.txt" -o /dev/null -s https://api.meddelivery.com/api/health

# Expected: Response time < 500ms
```

---

## Rollback Procedures

### When to Rollback

- Critical bugs in production
- Performance degradation
- Database migration failures
- Security vulnerabilities
- Service unavailability

### Rollback Methods

#### 1. Railway Rollback

```bash
# Rollback to previous deployment
railway rollback

# Or via Railway dashboard:
# Project → Deployments → Click previous deployment → Rollback
```

#### 2. Git Rollback

```bash
# Revert to previous commit
git revert <bad-commit-hash>
git push origin main

# Or reset and force push (careful!)
git reset --hard <good-commit-hash>
git push --force-with-lease origin main
```

#### 3. Database Rollback

```bash
# Restore from backup
# Via Railway:
# Project → PostgreSQL → Backups → Restore

# Via CLI:
railway postgresql:backup:download
pg_restore -h hostname -U user -d database backup.dump
```

#### 4. Emergency Rollback

If automated deployment causes outage:

1. **Stop automatic deployments**:
   ```bash
   # Disable GitHub Actions workflow temporarily
   gh workflow disable ci-cd.yml
   ```

2. **Rollback code**:
   ```bash
   git checkout main
   git revert HEAD
   git push origin main
   ```

3. **Redeploy previous version**:
   ```bash
   railway rollback
   ```

4. **Investigate and fix**:
   - Review deployment logs
   - Identify root cause
   - Fix and test
   - Redeploy

5. **Re-enable deployments**:
   ```bash
   gh workflow enable ci-cd.yml
   ```

### Rollback Communication

1. Notify team immediately
2. Update status page (if available)
3. Communicate to stakeholders
4. Document incident
5. Conduct post-mortem

---

## Monitoring & Logs

### Railway Monitoring

Railway provides built-in monitoring:

- **Metrics**: CPU, Memory, Disk, Network
- **Logs**: Application logs in real-time
- **Deployments**: Deployment history and status
- **Domains**: SSL certificate status

### Application Metrics

Monitor these key metrics:

| Metric | Warning Threshold | Critical Threshold |
|--------|------------------|-------------------|
| Response Time (p95) | 500ms | 1000ms |
| Error Rate | 1% | 5% |
| CPU Usage | 70% | 90% |
| Memory Usage | 75% | 90% |
| Database Connections | 70% | 90% |
| Redis Memory | 75% | 90% |

### Log Levels

Configure appropriate log levels:

```properties
# Production
logging.level.root=WARN
logging.level.com.meddelivery=INFO
logging.level.org.springframework.web=WARN
logging.level.org.hibernate=WARN

# Staging/Development
logging.level.root=INFO
logging.level.com.meddelivery=DEBUG
logging.level.org.springframework=DEBUG
```

### Alert Setup

Set up alerts for:

1. **Service Down**: Application returns 5xx errors
2. **High Error Rate**: >5% error rate for 5 minutes
3. **High Latency**: p95 response time >1s
4. **Database Issues**: Connection failures
5. **Redis Issues**: Connection failures
6. **Deployment Failures**: Failed deployments
7. **Memory Usage**: >90% memory utilization

---

## CI/CD Pipeline

### Pipeline Overview

The CI/CD pipeline is defined in `.github/workflows/ci-cd.yml` and includes:

1. **Test Job**: Runs on every push and PR
   - Starts PostgreSQL 15 and Redis 7
   - Sets up JDK 21 (Temurin)
   - Configures test properties
   - Runs Maven tests
   - Validates Flyway migrations

2. **Build Job**: Runs on push to main/develop
   - Builds Docker image
   - Multi-architecture support (amd64, arm64)
   - Pushes to GitHub Container Registry
   - Uses layer caching

3. **Deploy to Production**: Runs on push to main
   - Deploys to Railway production
   - Waits for health checks
   - Verifies deployment

4. **Deploy to Staging**: Runs on push to develop
   - Deploys to Railway staging
   - Waits for health checks

### Pipeline Features

✅ Multi-stage pipeline  
✅ Parallel test execution  
✅ Docker layer caching  
✅ Multi-arch image builds  
✅ Automatic rollbacks on failure  
✅ Health check verification  
✅ Test result archiving  
✅ Environment-specific deployments  

### Pipeline Triggers

| Event | Jobs Executed |
|-------|---------------|
| PR to main | Test only |
| Push to develop | Test + Build + Deploy (Staging) |
| Push to main | Test + Build + Deploy (Production) |
| Manual trigger | Configurable |

### Pipeline Security

- GitHub token for registry access
- Railway token stored in secrets
- No hardcoded credentials
- Minimal required permissions
- Encrypted variables

### Performance Optimization

- Maven dependency caching
- Docker layer caching
- Parallel test execution
- Incremental builds
- Buildx for multi-arch

---

## Troubleshooting

### Common Issues

#### 1. Deployment Fails

**Symptoms**: Railway deployment fails or times out

**Solutions**:
- Check GitHub Actions logs
- Verify Railway token has correct permissions
- Ensure Railway project ID is correct
- Check database connectivity
- Review application logs:
  ```bash
  railway logs --tail=100
  ```

#### 2. Database Migration Fails

**Symptoms**: Application fails to start, migration errors in logs

**Solutions**:
- Check migration SQL syntax
- Verify database permissions
- Rollback to previous version
- Restore from backup
- Run migrations manually:
  ```bash
  mvn flyway:repair
  mvn flyway:migrate
  ```

#### 3. Redis Connection Error

**Symptoms**: Cache operations fail, session storage issues

**Solutions**:
- Verify Redis host and port
- Check Redis addon is running
- Test connectivity:
  ```bash
  redis-cli -h hostname -p port ping
  ```

#### 4. Out of Memory

**Symptoms**: Application crashes, OOM errors

**Solutions**:
- Increase Railway service memory
- Optimize application memory usage
- Add JVM memory limits:
  ```
  JAVA_OPTS="-Xmx1g -Xms512m"
  ```

#### 5. High Latency

**Symptoms**: Slow API responses

**Solutions**:
- Check database query performance
- Review Redis cache hit rate
- Scale Railway service
- Add database indexes
- Enable query logging temporarily

### Debug Commands

```bash
# Check application status
railway status

# View real-time logs
railway logs --tail=50

# Run one-off command
railway run bash

# Check environment variables
railway variables

# Scale service
railway up --scale service=2

# Restart service
railway restart

# Database shell
railway postgresql:shell

# Run database query
railway postgresql:query "SELECT COUNT(*) FROM users;"
```

### Support Resources

- [Railway Documentation](https://docs.railway.app)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Flyway Documentation](https://flywaydb.org/documentation)
- [MedDelivery README](../README.md)
- [GitHub Issues](https://github.com/org/repo/issues)

---

## Best Practices

### Deployment Best Practices

1. ✅ Always test in staging before production
2. ✅ Keep deployments small and focused
3. ✅ Automate everything possible
4. ✅ Monitor deployments in real-time
5. ✅ Have rollback plans ready
6. ✅ Document all changes
7. ✅ Use feature flags for risky changes
8. ✅ Perform database backups before migrations

### Security Best Practices

1. ✅ Never commit secrets to git
2. ✅ Use environment variables for configuration
3. ✅ Rotate secrets regularly
4. ✅ Use least privilege principle
5. ✅ Enable 2FA on all accounts
6. ✅ Scan dependencies for vulnerabilities
7. ✅ Keep all software up to date
8. ✅ Use HTTPS everywhere

### Performance Best Practices

1. ✅ Use connection pooling
2. ✅ Implement caching strategically
3. ✅ Optimize database queries
4. ✅ Use indexes appropriately
5. ✅ Monitor and alert on metrics
6. ✅ Scale horizontally when needed
7. ✅ Use CDN for static assets
8. ✅ Compress responses

---

## Maintenance Schedule

### Daily
- [ ] Monitor application health
- [ ] Review error logs
- [ ] Check performance metrics

### Weekly
- [ ] Review deployment logs
- [ ] Check for security updates
- [ ] Verify backup integrity
- [ ] Review monitoring alerts

### Monthly
- [ ] Rotate secrets
- [ ] Update dependencies
- [ ] Test disaster recovery
- [ ] Review access logs
- [ ] Performance tuning

### Quarterly
- [ ] Security audit
- [ ] Load testing
- [ ] Disaster recovery drill
- [ ] Infrastructure review
- [ ] Backup restoration test

---

## Appendices

### Appendix A: Quick Reference

| Command | Description |
|---------|-------------|
| `gh workflow list` | List all workflows |
| `gh workflow run ci-cd.yml` | Run CI/CD workflow |
| `gh run list` | List workflow runs |
| `gh run view <run-id>` | View specific run |
| `railway login` | Login to Railway CLI |
| `railway link` | Link project |
| `railley logs` | View logs |
| `mvn clean test` | Run tests locally |
| `mvn spring-boot:run` | Run application locally |

### Appendix B: Environment Templates

See [.env.example](.env.example) for complete environment variable template.

### Appendix C: Contact Information

- **DevOps Team**: devops@meddelivery.com
- **Engineering Lead**: eng-lead@meddelivery.com
- **On-Call**: +1-800-MED-DEV

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-05-06 | DevOps Team | Initial deployment guide |
| 1.0.1 | 2026-05-15 | DevOps Team | Added migration best practices |
| 1.0.2 | 2026-05-20 | DevOps Team | Updated Railway CLI commands |

---

**Last Updated**: 2026-05-06  
**Document Version**: 1.0.0  
**Status**: Production Ready  🚀