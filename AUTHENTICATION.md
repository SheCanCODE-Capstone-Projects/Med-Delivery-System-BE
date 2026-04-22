# Med-Delivery System - Authentication Guide

This document explains how authentication and authorization work in the Med-Delivery backend system.

## Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 4.0.5 |
| Language | Java 21 |
| Database | PostgreSQL 17 |
| Cache/OTP | Redis 7 |
| Authentication | JWT, OAuth2 (Google/Microsoft), Firebase |
| API Documentation | Swagger OpenAPI 3.0.3 |
| Containerization | Docker + Docker Compose |

## Project Structure

```
src/main/java/com/meddelivery/
├── config/           # Configuration classes (Security, JWT, Redis, etc.)
├── controller/       # REST controllers
├── service/          # Business logic
├── repository/       # Data access
├── model/            # JPA entities
├── dto/              # Data Transfer Objects
└── exception/        # Exception handling
```

---

## Authentication Methods

The system supports **5 authentication methods** for different user types:

### 1. Email/Password Login
- **Users:** SUPER_ADMIN, MANAGER, PHARMACIST
- **Endpoint:** `POST /api/auth/login`
- **Flow:** Credentials validated via BCrypt → JWT issued (24h)

### 2. OTP-Based Activation (Patient)
- **Users:** PATIENT (registration/login)
- **Endpoints:**
  - `POST /api/auth/register` - Register & send OTP
  - `POST /api/auth/verify-otp` - Verify & activate account
  - `POST /api/auth/send-otp` - Resend OTP
- **Flow:** Register → OTP sent to email/phone → Verify → Account activated + JWT

### 3. OAuth2 (Google/Microsoft)
- **Users:** PATIENT
- **Endpoints:** `/oauth2/authorization/google`, `/oauth2/authorization/microsoft`
- **Flow:** Redirect to provider → Callback → Auto-create patient account → JWT

### 4. Firebase Phone Login
- **Users:** PATIENT
- **Endpoint:** `POST /api/auth/firebase-phone-login`
- **Flow:** Firebase token verified → User created/found → JWT issued

### 5. Set Password (Post-Activation)
- **Users:** MANAGER, PHARMACIST
- **Endpoint:** `POST /api/auth/set-password`
- **Flow:** Admin creates account → User receives email → OTP verification → Password set → Login enabled

---

## JWT Token Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION FLOW                      │
└─────────────────────────────────────────────────────────────┘

  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
  │   Request    │────▶│  JwtAuthFilter│────▶│ Security     │
  │   (JWT)      │     │  (Validates)  │     │  Context     │
  └──────────────┘     └──────────────┘     └──────────────┘
                              │
                              ▼
  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
  │   User       │◀────│ JwtService   │◀────│  Signing     │
  │   Authenticated    │  (Generates/  │     │  Key (HMAC)  │
  └──────────────┘     │  Validates)   │     └──────────────┘
                       └──────────────┘

  JWT Payload:
  {
    "sub": "user@example.com",  // username
    "role": "PATIENT",
    "iat": 1699999999,
    "exp": 1700086399           // 24 hours
  }
```

### How JWT Authentication Works

1. **Request Arrives:** Client sends request with `Authorization: Bearer <token>`
2. **JwtAuthFilter Intercepts:** Extracts token from header
3. **Token Validation:** JwtService validates signature and expiration
4. **User Loading:** CustomUserDetailsService loads user by username
5. **Security Context:** Authentication set in SecurityContextHolder
6. **Authorization:** SecurityConfig checks role-based permissions

---

## Role-Based Access Control

| Endpoint | Required Role |
|----------|---------------|
| /api/admin/** | SUPER_ADMIN |
| /api/manager/** | MANAGER |
| /api/pharmacist/** | PHARMACIST |
| /api/patient/** | PATIENT |

### User Roles

| Role | Permissions |
|------|-------------|
| SUPER_ADMIN | Full system access, user management |
| MANAGER | Pharmacy management, order oversight |
| PHARMACIST | Prescription verification, inventory |
| PATIENT | Place orders, view prescriptions |

---

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/auth/login | Email/password login |
| POST | /api/auth/register | Patient registration |
| POST | /api/auth/send-otp | Resend OTP |
| POST | /api/auth/verify-otp | Verify OTP |
| POST | /api/auth/set-password | Set password (post-activation) |
| POST | /api/auth/refresh | Refresh access token |
| POST | /api/auth/logout | Logout |
| POST | /api/auth/firebase-phone-login | Firebase phone login |

### OAuth2

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /oauth2/authorization/google | Google OAuth2 login |
| GET | /oauth2/authorization/microsoft | Microsoft OAuth2 login |

---

## Key Configuration Files

### SecurityConfig.java
- JWT filter chain configuration
- CORS settings
- OAuth2 login setup
- Role-based authorization rules

### JwtService.java
- Token generation with HMAC-SHA256
- Token validation
- Claim extraction

### JwtAuthFilter.java
- Intercepts every request
- Validates Bearer token
- Sets authentication in security context

### CustomUserDetailsService.java
- Loads user by email or phone number
- Returns Spring Security UserDetails

### OAuth2SuccessHandler.java
- Handles OAuth2 login success
- Creates/updates user in database
- Generates JWT after OAuth2 login

---

## Redis Usage

| Key Pattern | Purpose | TTL |
|-------------|---------|-----|
| otp:{username} | OTP storage | 5 minutes |
| refresh:{token} | Refresh tokens | 7 days |

---

## Configuration

### Environment Variables (.env)

Create `.env` from `.env.example`:

```bash
# Database
DB_URL=jdbc:postgresql://postgres:5432/meddelivery
DB_USERNAME=your_username
DB_PASSWORD=your_password

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# JWT
JWT_SECRET=your_256_bit_secret_key

# Email (for OTP)
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password

# OAuth2 (optional)
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
MICROSOFT_CLIENT_ID=your_microsoft_client_id
MICROSOFT_CLIENT_SECRET=your_microsoft_client_secret

# Firebase (for phone OTP)
FIREBASE_SERVICE_ACCOUNT_PATH=classpath:firebase-service-account.json
```

---

## Running the Application

### Using Docker (Recommended)

```bash
# 1. Create .env file
cp .env.example .env

# 2. Edit .env with your credentials

# 3. Build and run
docker-compose up --build
```

### Local Development

```bash
# Run PostgreSQL and Redis via Docker
docker-compose up -d postgres redis

# Run Spring Boot
mvn spring-boot:run
```

## Default Super Admin

On first startup, a default super admin account is created with the following default email:

- **Email:** admin@meddelivery.com

**Password setup** is handled securely via one of these options:

1. **Environment variable** (recommended for production):
   ```bash
   ADMIN_INITIAL_PASSWORD=YourSecurePassword123!
   ```

2. **Generated one-time password** (development only):
   The system generates a one-time password logged to the console on first startup. The password must be changed on first login.

3. **Forced setup flow**:
   If no password is configured via env var, the super admin account is created in a disabled state and must be enabled via an OTP flow or manual DB update by a trusted operator.

No reusable credentials are published in this documentation. Ensure `ADMIN_INITIAL_PASSWORD` is set via a secret management system (e.g., HashiCorp Vault, AWS Secrets Manager) before deploying.


---

## Swagger API Documentation

After starting the application:

```
http://localhost:8080/swagger-ui.html
```

---

## Security Features

- JWT tokens with HMAC-SHA256 signing
- BCrypt password encoding
- Stateless session management
- Role-based access control (RBAC)
- CORS configuration
- Rate limiting on OTP endpoints
- Refresh token rotation
- OAuth2 support for Google & Microsoft

---

## Testing

```bash
# Run unit tests
mvn test
```