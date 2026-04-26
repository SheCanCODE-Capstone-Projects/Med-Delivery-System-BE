# Swagger Testing Guide

## Access Swagger UI

After starting the application:

```
http://localhost:8080/swagger-ui.html
```

---

## Authentication Endpoints

### 1. POST /api/auth/login
**For:** SUPER_ADMIN, MANAGER, PHARMACIST

**Request Body:**
```json
{
  "username": "admin@meddelivery.com",
  "password": "Admin@4321"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbG...",
    "refreshToken": "abc123...",
    "role": "SUPER_ADMIN",
    "email": "admin@meddelivery.com",
    "fullName": "Super Admin"
  }
}
```

**Usage:** Copy `token` from response and click "Authorize" button in Swagger UI. Enter: `Bearer <token>`

---

### 2. POST /api/auth/register
**For:** PATIENT

**Request Body:**
```json
{
  "fullName": "John Doe",
  "email": "john@example.com",
  "phoneNumber": null
}
```

**Response:**
```json
{
  "success": true,
  "message": "OTP sent to your email"
}
```

---

### 3. POST /api/auth/send-otp
**For:** PATIENT (resend OTP)

**Parameter:**
- `username` - email or phone number

**Response:**
```json
{
  "success": true,
  "message": "OTP sent successfully"
}
```

---

### 4. POST /api/auth/verify-otp
**For:** PATIENT (activate account)

**Request Body:**
```json
{
  "username": "john@example.com",
  "otp": "123456"
}
```

**Response:**
```json
{
  "success": true,
  "message": "OTP verified successfully",
  "data": {
    "token": "eyJhbG...",
    "refreshToken": "abc123...",
    "role": "PATIENT",
    "email": "john@example.com",
    "fullName": "John Doe"
  }
}
```

---

### 5. POST /api/auth/set-password
**For:** MANAGER, PHARMACIST

**Request Body:**
```json
{
  "username": "pharmacist@pharmacy.com",
  "otp": "123456",
  "password": "SecurePass@123"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Password set successfully",
  "data": {
    "token": "eyJhbG...",
    "refreshToken": "abc123...",
    "role": "PHARMACIST"
  }
}
```

---

### 6. POST /api/auth/refresh
**For:** ALL users (refresh expired token)

**Parameter:**
- `refreshToken` - the refresh token from login

**Response:**
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "token": "eyJhbG...",
    "refreshToken": "abc123..."
  }
}
```

---

### 7. POST /api/auth/logout
**For:** ALL users

**Parameter:**
- `refreshToken` - the refresh token to revoke

**Response:**
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

### 8. POST /api/auth/firebase-phone-login
**For:** PATIENT (Firebase phone auth)

**Parameter:**
- `firebaseToken` - Firebase ID token from client

**Response:**
```json
{
  "success": true,
  "message": "Phone login successful",
  "data": {
    "token": "eyJhbG...",
    "refreshToken": "abc123...",
    "role": "PATIENT",
    "phoneNumber": "+1234567890",
    "fullName": "Patient"
  }
}
```

---

## OAuth2 Endpoints

### Google Login
Navigate to:
```
/oauth2/authorization/google
```

This redirects to Google, then returns with JWT token in response.

### Microsoft Login
Navigate to:
```
/oauth2/authorization/microsoft
```

---

## Testing Protected Endpoints

After authenticating:

1. Click **Authorize** button in Swagger UI
2. Enter: `Bearer <your_token>`
3. Click **Authorize** and close
4. Now test protected endpoints like `/api/admin/**`

---

## Response Format

All responses follow this structure:

```json
{
  "success": true,
  "message": "Operation description",
  "data": { ... }
}
```

---

## Error Responses

```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

---

## Default Test Credentials

| Role | Email | Password |
|------|-------|----------|
| SUPER_ADMIN | admin@meddelivery.com | Admin@4321 |

> ⚠️ Change password after first login!