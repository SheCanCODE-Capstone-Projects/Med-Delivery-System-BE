# API Testing Guide – Med-Delivery Platform

**Purpose:** Manual API testing via Swagger UI at `http://localhost:8080/swagger-ui.html`

---

## 🔐 Authentication Setup

### 1. Obtain JWT Token
```
POST /api/auth/login
```
**Body:**
```json
{
  "email": "patient@example.com",
  "password": "Patient123!"
}
```
**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "...",
  "user": { ... }
}
```

### 2. Authorize in Swagger
- Click **"Authorize"** button in top-right
- Enter: `Bearer <your-token>`
- All endpoints will now use your user context

---

## 👤 TEST USERS (Seed Data)

Use these pre-seeded accounts for testing:

| Role | Email | Password | User ID |
|------|-------|----------|---------|
| **Patient** | `patient@example.com` | `Patient123!` | `1` |
| **Pharmacist** | `pharmacist@example.com` | `Pharmacist123!` | `2` |
| **Manager** | `manager@example.com` | `Manager123!` | `3` |
| **Admin** | `admin@example.com` | `Admin123!` | `4` |

---

## 🧑‍⚕️ PATIENT FLOW – Complete Order Journey

### Step 1: Get/Update Patient Profile

**GET** `/api/patient/profile`
```json
{
  "success": true,
  "message": "Profile retrieved successfully",
  "data": {
    "id": 1,
    "userId": 1,
    "fullName": "John Doe",
    "email": "patient@example.com",
    "phoneNumber": "+1234567890",
    "profileImageUrl": null,
    "emailNotifications": true,
    "smsNotifications": true,
    "dateOfBirth": "1990-05-15",
    "gender": "Male",
    "allergies": "Penicillin",
    "medicalNotes": "Asthma",
    "hasLocation": true,
    "hasInsurance": false,
    "createdAt": "2026-05-01T10:00:00"
  }
}
```

**PUT** `/api/patient/profile`
```json
{
  "fullName": "John Doe Updated",
  "phoneNumber": "+1234567890",
  "profileImageUrl": "https://cdn.example.com/profile.jpg",
  "emailNotifications": true,
  "smsNotifications": false,
  "dateOfBirth": "1990-05-15",
  "gender": "Male",
  "allergies": "Penicillin",
  "medicalNotes": "Astma"
}
```

### Step 2: Add Insurance Card

**POST** `/api/patient/profile/insurance`
```json
{
  "providerName": "BlueCross",
  "memberId": "BC123456789",
  "frontImageUrl": "https://cdn.example.com/insurance_front.jpg",
  "backImageUrl": "https://cdn.example.com/insurance_back.jpg",
  "coveragePercentage": 80.0
}
```
**Response:**
```json
{
  "success": true,
  "message": "Insurance card added. Pending verification.",
  "data": {
    "id": 1,
    "providerName": "BlueCross",
    "memberId": "BC123456789",
    "frontImageUrl": "...",
    "backImageUrl": "...",
    "status": "PENDING_VERIFICATION",
    "coveragePercentage": 80.0,
    "createdAt": "2026-05-06T10:00:00"
  }
}
```

### Step 3: Get Medicines (Search)

**GET** `/api/medicines/search?q=parac&limit=10`
```json
{
  "success": true,
  "message": "Found 2 medicines",
  "data": [
    "Paracetamol 500mg",
    "Paracetamol + Codeine"
  ]
}
```

**GET** `/api/medicines/1`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Paracetamol 500mg",
    "genericName": "Paracetamol",
    "requiresPrescription": true
  }
}
```

### Step 4: Create Order – PRESCRIPTION_BASED (With Insurance)

**POST** `/api/orders`
```json
{
  "orderType": "PRESCRIPTION_BASED",
  "fulfillmentType": "DELIVERY",
  "deliveryAddress": "123 Main Street, Apt 4B, Springfield, IL 62701",
  "prescriptionId": 1,
  "insuranceCardId": 1,
  "items": [
    {
      "medicineId": 1,
      "quantity": 2
    },
    {
      "medicineId": 2,
      "quantity": 1
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": 1,
    "status": "UPLOADED",
    "orderType": "PRESCRIPTION_BASED",
    "fulfillmentType": "DELIVERY",
    "deliveryAddress": "123 Main Street, Apt 4B, Springfield, IL 62701",
    "coveragePercentage": 80.0,
    "createdAt": "2026-05-06T10:05:00",
    "patientName": "John Doe",
    "pharmacyName": "Central Pharmacy",
    "items": [
      {
        "id": 1,
        "medicineId": 1,
        "medicineName": "Paracetamol 500mg",
        "quantity": 2,
        "unitPrice": 5.99,
        "status": "AVAILABLE"
      }
    ],
    "totalAmount": 50.00,
    "patientPayableAmount": 10.00,
    "insurancePayableAmount": 40.00,
    "paymentStatus": "PENDING",
    "paymentMethod": "INSURANCE"
  }
}
```
**Note:** `totalAmount` = sum of (price × qty) from pharmacy inventory.

### Step 5: Get My Orders

**GET** `/api/orders/my-orders?page=0&size=10`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "status": "UPLOADED",
        "orderType": "PRESCRIPTION_BASED",
        "fulfillmentType": "DELIVERY",
        "deliveryAddress": "...",
        "coveragePercentage": 80.0,
        "createdAt": "...",
        "patientName": "John Doe",
        "pharmacyName": "Central Pharmacy",
        "items": [ ... ],
        "totalAmount": 50.00,
        "patientPayableAmount": 10.00,
        "insurancePayableAmount": 40.00,
        "paymentStatus": "PENDING",
        "paymentMethod": "INSURANCE"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1
  }
}
```

### Step 6: Get Order Details

**GET** `/api/orders/1`
```json
{
  "success": true,
  "data": { ...same as above... }
}
```

### Step 7: Confirm Payment

**POST** `/api/orders/1/pay`
```json
{
  "success": true,
  "message": "Payment confirmed successfully",
  "data": {
    "id": 1,
    "status": "MATCHING",
    "paymentStatus": "INSURANCE_PENDING",
    "paymentMethod": "INSURANCE",
    "patientPayableAmount": 10.00,
    "insurancePayableAmount": 40.00,
    ...
  }
}
```

**GET** `/api/orders/1/payment`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "orderId": 1,
    "totalAmount": 50.00,
    "insuranceAmount": 40.00,
    "patientAmount": 10.00,
    "status": "INSURANCE_PENDING",
    "paymentMethod": "INSURANCE",
    "insuranceProvider": "BlueCross",
    "transactionId": null,
    "failureReason": null,
    "createdAt": "2026-05-06T10:05:00",
    "paidAt": null
  }
}
```

---

## 🏥 PHARMACIST FLOW

### Step 1: Get Assigned Pharmacy Orders

**GET** `/api/pharmacies/{pharmacyId}/pharmacists/my-orders`
*(Replace `{pharmacyId}` with your pharmacist's pharmacy ID)*

**Response:**
```json
[
  {
    "id": 1,
    "status": "MATCHING",
    "orderType": "PRESCRIPTION_BASED",
    "fulfillmentType": "DELIVERY",
    "deliveryAddress": "123 Main St...",
    "coveragePercentage": 80.0,
    "createdAt": "2026-05-06T10:05:00",
    "patientName": "John Doe",
    "pharmacyName": "Central Pharmacy",
    "items": [ ... ],
    "totalAmount": 50.00,
    "patientPayableAmount": 10.00,
    "insurancePayableAmount": 40.00,
    "paymentStatus": "INSURANCE_PENDING",
    "paymentMethod": "INSURANCE"
  }
]
```

### Step 2: Validate Prescription

**PUT** `/api/pharmacies/{pharmacyId}/prescriptions/{prescriptionId}/validate?isValid=true`

**Response:**
```json
{
  "id": 2,
  "userId": 2,
  "fullName": "Dr. Smith",
  "email": "pharmacist@example.com",
  "phoneNumber": "...",
  "pharmacyId": 1,
  "licenseNumber": "PH12345",
  "isVerified": true
}
```

### Step 3: Update Order Status

**PUT** `/api/pharmacies/{pharmacyId}/orders/{orderId}/status?status=IN_PROGRESS`

**Valid status values:** `UPLOADED`, `MATCHING`, `ASSIGNED`, `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`

**Response:** Updated order object with new status.

---

## 👨‍💼 ADMIN FLOW – Insurance Claims & Verification

### Step 1: List Pending Insurance Claims

**GET** `/api/admin/insurance-claims?status=INSURANCE_PENDING&page=0&size=10`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "orderId": 1,
        "totalAmount": 50.00,
        "insuranceAmount": 40.00,
        "patientAmount": 10.00,
        "status": "INSURANCE_PENDING",
        "paymentMethod": "INSURANCE",
        "insuranceProvider": "BlueCross",
        "createdAt": "2026-05-06T10:05:00",
        "paidAt": null
      }
    ],
    "totalElements": 1
  }
}
```

### Step 2: Process Claim – APPROVE

**POST** `/api/admin/insurance-claims/1/process?action=APPROVE`

**Response:**
```json
{
  "success": true,
  "message": "Claim approve successfully",
  "data": {
    "id": 1,
    "orderId": 1,
    "status": "PAID",
    "transactionId": "INS-1-1715038400000",
    "paidAt": "2026-05-06T10:15:00",
    "insuranceAmount": 40.00,
    "patientAmount": 10.00
  }
}
```

### Step 3: Verify Insurance Card (Set Coverage %)

**POST** `/api/admin/insurance-cards/1/verify?coveragePercentage=80.0`

**Response:**
```json
{
  "success": true,
  "message": "Insurance card verified successfully",
  "data": {
    "id": 1,
    "providerName": "BlueCross",
    "memberId": "BC123456789",
    "status": "VERIFIED",
    "coveragePercentage": 80.0,
    "createdAt": "2026-05-06T10:00:00"
  }
}
```

---

## 🤖 AI CHATBOT – Patient Assistant

**POST** `/api/chatbot/ask`
**Headers:** `X-Conversation-Id: 123e4567-e89b-12d3-a456-426614174000` *(optional)*

**Body:**
```json
{
  "message": "What is paracetamol used for?",
  "maxTokens": 150,
  "temperature": 0.7
}
```

**Response:**
```json
{
  "success": true,
  "message": "Response generated",
  "data": {
    "reply": "Paracetamol (acetaminophen) is a common pain reliever and fever reducer. " +
             "It's used for headaches, muscle aches, arthritis, backaches, toothaches, " +
             "colds, and fevers. Always follow your doctor's instructions and do not " +
             "exceed the recommended dosage. If you have liver problems or drink alcohol " +
             "frequently, consult your doctor before taking paracetamol.",
    "conversationId": "123e4567-e89b-12d3-a456-426614174000",
    "tokenUsage": 87,
    "model": "gpt-3.5-turbo"
  }
}
```

**Follow-up conversation:**
```json
{
  "message": "Can I take it with aspirin?",
  "maxTokens": 150
}
```
*(Same `conversationId` header – conversation history maintained)*

---

## 🔄 SUBSTITUTION FLOW

### Step 1: Pharmacist Creates Substitution Request

**POST** `/api/substitutions/requests`
```json
{
  "orderItemId": 1,
  "substituteMedicineId": 2,
  "pharmacistReason": "Original out of stock, generic equivalent available"
}
```
**Response:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "orderItemId": 1,
    "orderId": 1,
    "originalMedicineId": 1,
    "originalMedicineName": "Paracetamol 500mg",
    "substituteMedicineId": 2,
    "substituteMedicineName": "Paracetamol Extra 500mg",
    "pharmacistReason": "...",
    "patientReason": null,
    "status": "PENDING",
    "requestedAt": "2026-05-06T10:10:00",
    "respondedAt": null
  }
}
```

### Step 2: Patient Approves

**PUT** `/api/substitutions/requests/1/approve?patientId=1`
```json
{
  "success": true,
  "data": {
    "id": 1,
    "status": "APPROVED",
    "respondedAt": "2026-05-06T10:12:00"
  }
}
```

---

## 📦 MEDICINE SEARCH – Quick Tests

| Query | Expected Result |
|-------|-----------------|
| `q=amox` | List of amoxicillin variants |
| `q=zyr` | Zyrtec, Zyrtex |
| `q=abc123` | Empty list `[]` |

---

## 🧪 Manual Test Cases Checklist

### Order Creation
- [x] Create PRESCRIPTION_BASED order (requires prescription + AI validation)
- [x] Create PRIVATE_PURCHASE order (no prescription)
- [ ] **Fail:** Order without items → 400
- [ ] **Fail:** Unknown medicine ID → 404
- [ ] **Fail:** Prescription not owned by patient → 403
- [ ] **Fail:** Insurance card not verified → 400
- [ ] Create order with `fulfillmentType=DELIVERY` and `deliveryAddress` → success
- [ ] **Fail:** Create order with `DELIVERY` but missing `deliveryAddress` → 400

### Payment
- [x] Confirm payment for private order → status PAID
- [x] Confirm payment for insurance order → status INSURANCE_PENDING
- [ ] **Fail:** Pay twice → 400 ("already processed")
- [x] Get payment details after confirm

### Admin Insurance
- [x] List all insurance claims
- [x] Filter by status (`?status=INSURANCE_PENDING`)
- [x] Approve claim → payment.status = PAID
- [x] Reject claim → payment.status = FAILED
- [x] Verify insurance card (set coverage %)

### Profile
- [x] Upload profile image → `profileImageUrl` saved
- [x] Add insurance card with coveragePercentage
- [x] Update notification preferences via profile update

### Medicine
- [x] Search by partial name (case-insensitive)
- [x] Get medicine by ID

### AI Chatbot
- [ ] Ask medication question → receives informative answer
- [ ] Ask about order status → should direct to order tracking
- [ ] Ask non-medical question → friendly response
- [ ] Conversation continuity with `X-Conversation-Id`

### Pharmacist
- [x] Get pharmacy orders
- [x] Validate prescription
- [x] Update order status to IN_PROGRESS → COMPLETED

### WebSocket (Manual via Frontend)
- [ ] Subscribe to `/topic/orders/{userId}` → receive updates on order status change
- [ ] Subscribe to `/topic/substitutions/{userId}` → receive substitution notifications
- [ ] Subscribe to `/topic/pharmacy/{pharmacyId}/orders` → pharmacy receives new order alert

---

## 🚨 Important Notes for Tester

1. **JWT Expiry:** Tokens are short-lived. Re-login if 401 Unauthorized.
2. **AI Validation:** By default `app.ai.enabled=false` in `application.properties`.  
   To enable: set `app.ai.enabled=true` and provide OpenAI API key.
3. **Insurance Card:** Must be verified (status=VERIFIED) before it can be used for an order.
4. **Delivery Address:** Required **only** if `fulfillmentType=DELIVERY`.
5. **Pharmacy Matching:** Returns first match with 100% coverage. If none, patient sees "Manual review required" UI.
6. **Payment Confirmation:** Triggers insurance claim submission if coverage > 0.
7. **Order Status Flow:**  
   `UPLOADED → MATCHING → ASSIGNED → IN_PROGRESS → READY_FOR_PICKUP → COMPLETED`
8. **IDOR Protection:** Patients can only access their own orders/pharmacists own assigned pharmacy orders.

---

*End of Test Guide*
