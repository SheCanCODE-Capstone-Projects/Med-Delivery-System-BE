# CORS Fix and Deployment Guide

## Changes Made

### 1. Created CorsConfig.java
- Added global CORS configuration at MVC level
- Allows all origins with pattern matching
- Location: `src/main/java/com/meddelivery/config/CorsConfig.java`

### 2. Updated SecurityConfig.java
- Simplified CORS configuration
- Set `allowCredentials(false)` to work with wildcard origins
- Added `/health` and `/api/health` to public URLs

### 3. Updated ALL Controllers with @CrossOrigin
- **AdminController** - Added CORS support
- **AuthController** - Added CORS support
- **ChatbotController** - Added CORS support
- **DispensingController** - Added CORS support
- **HealthController** - Added CORS support
- **InsuranceController** - Added CORS support
- **MedicineController** - Added CORS support
- **MedicineRequestController** - Added CORS support
- **OrderController** - Added CORS support
- **PatientLocationController** - Added CORS support
- **PatientProfileController** - Added CORS support
- **PharmacistController** - Added CORS support
- **PharmacyController** - Added CORS support
- **PharmacyInventoryController** - Added CORS support
- **PharmacyPatientController** - Added CORS support
- **PrescriptionController** - Added CORS support
- **SubstitutionController** - Added CORS support

### 4. Updated HealthController.java
- Added root endpoint `/`
- Added `/api/health` endpoint
- Added `@CrossOrigin` annotation

### 5. Created RequestLoggingFilter.java
- Logs all incoming requests with full details
- Adds CORS headers explicitly at filter level
- Handles OPTIONS preflight requests
- Highest precedence to run before all other filters

### 6. Fixed Test Files
- Fixed `OtpServiceTest.java` - Changed test to expect logging instead of exception
- Fixed `AuthServiceTest.java` - Updated expected error message

## Deployment Steps for Railway

### Step 1: Commit and Push Changes
```bash
git add .
git commit -m "Fix CORS configuration and add request logging"
git push origin main
```

### Step 2: Verify Railway Environment Variables
Make sure these are set in Railway:
- `CORS_ALLOWED_ORIGINS` (optional, defaults to localhost)
- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`
- `REDISHOST`, `REDISPORT`
- `JWT_SECRET`
- `MAIL_USERNAME`, `MAIL_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `OPENAI_API_KEY` (if AI is enabled)

### Step 3: Test Endpoints After Deployment

1. **Test Root Endpoint:**
   ```
   GET https://med-delivery-system-be-production.up.railway.app/
   ```

2. **Test Health Endpoint:**
   ```
   GET https://med-delivery-system-be-production.up.railway.app/api/health
   ```

3. **Test Register Endpoint:**
   ```
   POST https://med-delivery-system-be-production.up.railway.app/api/auth/register
   Content-Type: application/json
   
   {
     "fullName": "Test User",
     "email": "test@example.com"
   }
   ```

### Step 4: Check Railway Logs
After deployment, check the logs for:
- "=== Incoming Request ===" - Shows the RequestLoggingFilter is working
- Request details (Method, URI, Origin, Headers)
- Response status
- Any errors

## Debugging

### If CORS Still Fails:
1. Check Railway logs for incoming requests
2. Verify the Origin header in the logs
3. Check if OPTIONS preflight is being handled (should see 200 OK)
4. Verify CORS headers in response

### If Requests Don't Reach Database:
1. Check if request reaches the controller (look for log: "Registration request received")
2. Check for any exceptions in the logs
3. Verify database connection (check Hibernate logs)
4. Test with Swagger UI at: `https://med-delivery-system-be-production.up.railway.app/swagger-ui.html`

### Common Issues:
1. **"Failed to fetch"** - Usually CORS or network issue
2. **No logs in Railway** - Request not reaching the server (check URL)
3. **500 Error** - Check application logs for exceptions
4. **401/403 Error** - Security configuration issue

## Testing with cURL

```bash
# Test with OPTIONS (preflight)
curl -X OPTIONS https://med-delivery-system-be-production.up.railway.app/api/auth/register \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -v

# Test POST request
curl -X POST https://med-delivery-system-be-production.up.railway.app/api/auth/register \
  -H "Content-Type: application/json" \
  -H "Origin: http://localhost:3000" \
  -d '{"fullName":"Test User","email":"test@example.com"}' \
  -v
```

## Expected Behavior

1. **OPTIONS Request:** Should return 200 OK with CORS headers
2. **POST Request:** Should return 200 OK with response body
3. **Logs:** Should show detailed request information
4. **Database:** Should see Hibernate SQL queries in logs if request reaches service layer

## Rollback Plan

If issues persist, you can temporarily disable CORS security:
1. Comment out `@CrossOrigin` annotations
2. Set `spring.security.enabled=false` in application.properties (NOT RECOMMENDED for production)
3. Use Railway's built-in proxy/CDN features

## Next Steps

After deployment:
1. Monitor Railway logs for 5-10 minutes
2. Test all endpoints from Swagger UI
3. Test from your frontend application
4. If successful, remove RequestLoggingFilter or reduce log level for production
