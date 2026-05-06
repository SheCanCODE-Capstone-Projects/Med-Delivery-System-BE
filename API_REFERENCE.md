# Med-Delivery API Documentation

**Base URL:** `http://localhost:8080/api`  
**Authentication:** JWT Bearer Token (OAuth2 also supported)  
**Content-Type:** `application/json` (except multipart endpoints)  
**Swagger UI:** `http://localhost:8080/swagger-ui.html`

---

## 🔐 Authentication

All endpoints (except register/login) require JWT authentication.

**Header:**
```
Authorization: Bearer <jwt_token>
```

### Login Endpoint
**POST** `/api/auth/login`
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
  "user": {
    "id": 1,
    "fullName": "John Doe",
    "email": "patient@example.com",
    "role": "PATIENT"
  }
}
```

---

## 📋 Role-Based Access Control

| Role | Description |
|------|-------------|
| `PATIENT` | Can manage own orders, prescriptions, profile, insurance, chat |
| `PHARMACIST` | Can view pharmacy orders, validate prescriptions, update status, manage inventory |
| `MANAGER` | Can manage pharmacy staff, inventory, view pharmacy patients |
| `SUPER_ADMIN` | Full system access, approve pharmacies, process insurance claims |

---

# 🏥 PATIENT ENDPOINTS

## 1. Profile Management

### 1.1 Get My Profile
**GET** `/api/patient/profile`  
**Auth:** PATIENT  
**Description:** Retrieves logged-in patient's full profile including preferences

**Response 200:**
```json
{
  "success": true,
  "message": "Profile retrieved successfully",
  "data": {
    "id": 1,
    "userId": 1,
    "fullName": "John Doe",
    "email": "john@example.com",
    "phoneNumber": "+1234567890",
    "profileImageUrl": "https://cdn.example.com/profile.jpg",
    "emailNotifications": true,
    "smsNotifications": true,
    "dateOfBirth": "1990-05-15",
    "gender": "Male",
    "allergies": "Penicillin, Aspirin",
    "medicalNotes": "Asthma condition",
    "hasLocation": true,
    "hasInsurance": true,
    "createdAt": "2026-05-01T10:00:00Z",
    "updatedAt": "2026-05-05T14:30:00Z"
  }
}
```

### 1.2 Create/Update Profile
**PUT** `/api/patient/profile`  
**Auth:** PATIENT  
**Description:** Updates patient profile fields and notification preferences

**Request:**
```json
{
  "fullName": "John Doe",
  "phoneNumber": "+1234567890",
  "profileImageUrl": "https://cdn.example.com/profile.jpg",
  "emailNotifications": true,
  "smsNotifications": false,
  "dateOfBirth": "1990-05-15",
  "gender": "Male",
  "allergies": "Penicillin",
  "medicalNotes": "Updated medical info"
}
```

**Response 200:** Updated `PatientProfileResponse`

### 1.3 Upload Profile Image
**POST** `/api/patient/profile/image`  
**Auth:** PATIENT  
**Content-Type:** `multipart/form-data`  
**Description:** Uploads profile picture, returns updated profile

**Form Data:**
| Field | Type | Required |
|-------|------|----------|
| `file` | File (image) | Yes |

**Response 200:**
```json
{
  "success": true,
  "message": "Profile image uploaded successfully",
  "data": { ...PatientProfileResponse with profileImageUrl set }
}
```

---

## 2. Insurance Card Management

### 2.1 Add Insurance Card
**POST** `/api/patient/profile/insurance`  
**Auth:** PATIENT  
**Description:** Adds a new insurance card (requires admin verification before use)

**Request:**
```json
{
  "providerName": "BlueCross BlueShield",
  "memberId": "BC123456789",
  "frontImageUrl": "https://cdn.example.com/insurance_front.jpg",
  "backImageUrl": "https://cdn.example.com/insurance_back.jpg",
  "coveragePercentage": 80.0
}
```

**Response 201:**
```json
{
  "success": true,
  "message": "Insurance card added. Pending verification.",
  "data": {
    "id": 1,
    "providerName": "BlueCross BlueShield",
    "memberId": "BC123456789",
    "frontImageUrl": "...",
    "backImageUrl": "...",
    "status": "PENDING_VERIFICATION",
    "coveragePercentage": 80.0,
    "createdAt": "2026-05-06T10:00:00Z"
  }
}
```

### 2.2 Get My Insurance Cards
**GET** `/api/patient/profile/insurance`  
**Auth:** PATIENT  
**Description:** List all insurance cards for logged-in patient

**Response 200:**
```json
{
  "success": true,
  "message": "Insurance cards retrieved successfully",
  "data": [
    {
      "id": 1,
      "providerName": "BlueCross",
      "memberId": "BC123456789",
      "status": "VERIFIED",
      "coveragePercentage": 80.0,
      "createdAt": "2026-05-06T10:00:00Z"
    }
  ]
}
```

### 2.3 Get Insurance Card by ID
**GET** `/api/patient/profile/insurance/{id}`  
**Auth:** PATIENT  
**Description:** Retrieve single insurance card

**Response 200:** `InsuranceCardResponse`

### 2.4 Update Insurance Card
**PUT** `/api/patient/profile/insurance/{id}`  
**Auth:** PATIENT  
**Description:** Update insurance card details (images, provider, coverage %)

**Request:**
```json
{
  "providerName": "Aetna",
  "memberId": "AET987654321",
  "frontImageUrl": "https://cdn.example.com/new_front.jpg",
  "backImageUrl": "https://cdn.example.com/new_back.jpg",
  "coveragePercentage": 70.0
}
```

**Response 200:** Updated `InsuranceCardResponse`

### 2.5 Delete Insurance Card
**DELETE** `/api/patient/profile/insurance/{id}`  
**Auth:** PATIENT  
**Description:** Remove insurance card (only if not used in orders)

**Response 200:**
```json
{
  "success": true,
  "message": "Insurance card removed"
}
```

### 2.6 Upload Insurance Card (Multipart)
**POST** `/api/patient/insurance/upload`  
**Auth:** PATIENT  
**Content-Type:** `multipart/form-data`  
**Description:** Upload both front/back images and create card

**Form Data:**
| Field | Type | Required |
|-------|------|----------|
| `frontImage` | File (image) | Yes |
| `backImage` | File (image) | Yes |
| `providerName` | String | Yes |
| `memberId` | String | Yes |

**Response 201:** `InsuranceCardResponse` with status `PENDING_VERIFICATION`

---

## 3. Prescription Management

### 3.1 Upload Prescription
**POST** `/api/patient/prescriptions`  
**Auth:** PATIENT  
**Content-Type:** `multipart/form-data`  
**Description:** Upload prescription file (PDF/JPG)

**Form Data:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | Yes | Prescription image or PDF |
| `fileType` | String (PDF/IMAGE) | No | Auto-detected if not provided |
| `notes` | String | No | Additional notes |
| `prescriptionDate` | String (YYYY-MM-DD) | No | Date on prescription |
| `hasStamp` | Boolean | No | Does prescription have official stamp |
| `hasSignature` | Boolean | No | Does prescription have doctor signature |

**Response 201:**
```json
{
  "success": true,
  "message": "Prescription uploaded successfully",
  "data": {
    "id": 1,
    "fileUrl": "/api/files/prescriptions/abc123.jpg",
    "fileType": "IMAGE",
    "notes": "Take once daily",
    "prescriptionDate": "2026-05-01",
    "expiryDate": null,
    "hasStamp": true,
    "hasSignature": true,
    "status": "PENDING",
    "uploadedAt": "2026-05-06T10:00:00Z",
    "validatedByPharmacist": null,
    "validationStatus": null
  }
}
```

### 3.2 Get All My Prescriptions
**GET** `/api/patient/prescriptions`  
**Auth:** PATIENT  
**Description:** List all prescriptions uploaded by patient (paginated not implemented)

**Response 200:** Array of `PrescriptionResponse`

### 3.3 Get Prescription by ID
**GET** `/api/patient/prescriptions/{id}`  
**Auth:** PATIENT  
**Description:** Get single prescription details

**Response 200:** `PrescriptionResponse`

### 3.4 Delete Prescription
**DELETE** `/api/patient/prescriptions/{id}`  
**Auth:** PATIENT  
**Description:** Delete prescription (only if not used in order)

**Response 200:**
```json
{
  "success": true,
  "message": "Prescription deleted successfully"
}
```

---

## 4. Order Management

### 4.1 Create Order
**POST** `/api/orders`  
**Auth:** PATIENT  
**Description:** Creates a new order (prescription-based or private purchase). Triggers pharmacy matching and AI validation (if prescription-based).

**Request:**
```json
{
  "orderType": "PRESCRIPTION_BASED", // or "PRIVATE_PURCHASE"
  "fulfillmentType": "DELIVERY", // or "PICKUP"
  "deliveryAddress": "123 Main St, City, State 12345", // REQUIRED if fulfillmentType=DELIVERY
  "prescriptionId": 1, // REQUIRED for PRESCRIPTION_BASED, omit for PRIVATE
  "insuranceCardId": 1, // OPTIONAL – if provided, insurance coverage applied
  "items": [
    {
      "medicineId": 1,
      "quantity": 2
    }
  ]
}
```

**Validation Rules:**
- `items`: At least 1 item required
- `prescriptionId`: Required if `orderType=PRESCRIPTION_BASED`
- `deliveryAddress`: Required if `fulfillmentType=DELIVERY`
- `insuranceCardId`: Must belong to patient and be `VERIFIED`

**Business Logic:**
1. If `PRESCRIPTION_BASED`: AI validates medicines match prescription
2. Matches pharmacy using distance + coverage algorithm
3. Fetches prices from matched pharmacy's inventory
4. Calculates totals with insurance coverage (if card provided)
5. Creates `Payment` record with split amounts
6. Sends WebSocket notifications

**Response 201:**
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": 1,
    "status": "UPLOADED",
    "orderType": "PRESCRIPTION_BASED",
    "fulfillmentType": "DELIVERY",
    "deliveryAddress": "123 Main St...",
    "coveragePercentage": 80.0,
    "createdAt": "2026-05-06T10:05:00Z",
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

### 4.2 Get My Orders (List)
**GET** `/api/orders/my-orders?page=0&size=10`  
**Auth:** PATIENT  
**Description:** Paginated list of patient's own orders (most recent first)

**Query Parameters:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Page number (0-based) |
| `size` | int | 10 | Items per page (max 100) |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      { ...OrderResponse... }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 5
  }
}
```

### 4.3 Get Order Details
**GET** `/api/orders/{id}`  
**Auth:** PATIENT  
**Description:** Get full order details with items, payment info

**Response 200:** `OrderResponse` (see above)

### 4.4 Confirm Payment
**POST** `/api/orders/{id}/pay`  
**Auth:** PATIENT  
**Description:** Triggers payment processing. For insurance orders, sets status to `INSURANCE_PENDING` (admin must approve claim later). For private orders, marks as `PAID`.

**Response 200:**
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

### 4.5 Get Payment Details
**GET** `/api/orders/{id}/payment`  
**Auth:** PATIENT  
**Description:** Retrieve payment record for an order (shows split between insurance/patient)

**Response 200:**
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
    "transactionId": null,
    "insuranceProvider": "BlueCross",
    "failureReason": null,
    "createdAt": "2026-05-06T10:05:00Z",
    "paidAt": null
  }
}
```

---

## 5. Medicine Search

### 5.1 Search Medicines (Autocomplete)
**GET** `/api/medicines/search?q=parac&limit=10`  
**Auth:** Optional (public)  
**Description:** Search medicines by name (case-insensitive partial match). Returns only medicine names for autocomplete.

**Query Parameters:**
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `q` | String | Yes | Search query (at least 1 char) |
| `limit` | int | No | Max results (default: 10) |

**Response 200:**
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

### 5.2 Get Medicine by ID
**GET** `/api/medicines/{id}`  
**Auth:** Optional  
**Description:** Get medicine details (name, generic name, requiresPrescription flag)

**Response 200:**
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

---

## 6. Substitution Workflow

### 6.1 Create Substitution Request (Pharmacist only – shown here for context)
**POST** `/api/substitutions`  
**Auth:** PHARMACIST  
**Description:** Pharmacist suggests alternative medicine (out of stock)

---

### 6.2 Get Substitutions by Order
**GET** `/api/substitutions/order/{orderId}`  
**Auth:** PATIENT | PHARMACIST | MANAGER  
**Description:** List all substitution requests for a given order

**Response 200:** Array of `SubstitutionResponse`

### 6.3 Get Pending Substitutions (Patient)
**GET** `/api/substitutions/pending`  
**Auth:** PATIENT  
**Description:** List all pending substitution requests awaiting patient approval

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "orderItemId": 1,
      "orderId": 1,
      "originalMedicineName": "Paracetamol 500mg",
      "substituteMedicineName": "Paracetamol Extra 500mg",
      "pharmacistReason": "Original out of stock",
      "status": "PENDING",
      "requestedAt": "2026-05-06T10:10:00Z"
    }
  ]
}
```

### 6.4 Approve Substitution
**PUT** `/api/substitutions/{substitutionId}/approve?patientId=1`  
**Auth:** PATIENT  
**Description:** Patient approves substitution; order item's medicine updated

**Response 200:**
```json
{
  "success": true,
  "message": "Substitution approved",
  "data": {
    "id": 1,
    "status": "APPROVED",
    "respondedAt": "2026-05-06T10:12:00Z"
  }
}
```

### 6.5 Reject Substitution
**PUT** `/api/substitutions/{substitutionId}/reject?reason=Not+suitable`  
**Auth:** PATIENT  
**Description:** Patient rejects substitution with reason

**Response 200:**
```json
{
  "success": true,
  "message": "Substitution rejected",
  "data": {
    "id": 1,
    "status": "REJECTED",
    "reason": "Not suitable",
    "respondedAt": "2026-05-06T10:15:00Z"
  }
}
```

---

## 7. Pharmacy Information

### 7.1 Get Active Pharmacies
**GET** `/api/pharmacies/active`  
**Auth:** PATIENT | PHARMACIST  
**Description:** List all active (verified) pharmacies with their details

**Response 200:** Array of `PharmacyResponse`

---

## 8. AI Chatbot

### 8.1 Ask Question
**POST** `/api/chatbot/ask`  
**Auth:** PATIENT  
**Headers:** `X-Conversation-Id: <uuid>` *(optional – include for conversation continuity)*  
**Description:** Ask medical assistant about medications, orders, platform usage

**Request:**
```json
{
  "message": "What is paracetamol used for?",
  "maxTokens": 150,
  "temperature": 0.7
}
```

**Response 200:**
```json
{
  "success": true,
  "message": "Response generated",
  "data": {
    "reply": "Paracetamol (acetaminophen) is a common pain reliever and fever reducer...",
    "conversationId": "123e4567-e89b-12d3-a456-426614174000",
    "tokenUsage": 87,
    "model": "gpt-3.5-turbo"
  }
}
```

---

# 🏪 PHARMACIST ENDPOINTS

## 1. Pharmacy-Specific Context

All pharmacist endpoints are prefixed with the pharmacy they belong to:
```
/api/pharmacies/{pharmacyId}/...
```

Pharmacist must be associated with that pharmacy (verified via security service).

---

## 2. Order Management (Pharmacist)

### 2.1 Get Pharmacy Orders
**GET** `/api/pharmacies/{pharmacyId}/pharmacists/my-orders`  
**Auth:** PHARMACIST  
**Description:** List all orders assigned to the pharmacist's pharmacy

**Response 200:** Array of `OrderResponse` (includes payment details)

### 2.2 Get Order Details (Missing – needs implementation)
**GET** `/api/pharmacies/{pharmacyId}/orders/{orderId}`  
**Auth:** PHARMACIST  
**Description:** View full details of a specific order (including all items, delivery address, payment status)

**Response 200:** `OrderResponse`

### 2.3 Update Order Status
**PUT** `/api/pharmacies/{pharmacyId}/orders/{orderId}/status?status=READY_FOR_PICKUP`  
**Auth:** PHARMACIST  
**Description:** Update the status of an order. Only orders assigned to pharmacist's pharmacy can be updated.

**Valid Status Values:**
- `UPLOADED`
- `MATCHING`
- `ASSIGNED`
- `IN_PROGRESS`
- `READY_FOR_PICKUP`
- `COMPLETED`
- `CANCELLED`

**Response 200:** Updated `OrderResponse`

---

## 3. Prescription Validation

### 3.1 Validate Prescription
**PUT** `/api/pharmacies/{pharmacyId}/prescriptions/{prescriptionId}/validate?isValid=true`  
**Auth:** PHARMACIST  
**Description:** Mark prescription as valid or rejected after manual review

**Response 200:** `PharmacistResponse` (updated pharmacist profile with validation timestamp)

---

## 4. Substitution Management

### 4.1 Create Substitution Request
**POST** `/api/substitutions`  
**Auth:** PHARMACIST  
**Description:** Propose medicine substitution for an order item

**Request:**
```json
{
  "orderItemId": 1,
  "substituteMedicineId": 2,
  "pharmacistReason": "Original medicine out of stock"
}
```

**Response 201:** `SubstitutionResponse` with status `PENDING`

### 4.2 Get Substitutions by Order
**GET** `/api/substitutions/order/{orderId}`  
**Auth:** PHARMACIST | PATIENT | MANAGER  
**Description:** View all substitution requests for an order

**Response 200:** Array of `SubstitutionResponse`

### 4.3 Get Pending Substitutions for Pharmacy (Missing – needs implementation)
**GET** `/api/pharmacies/{pharmacyId}/substitutions/pending`  
**Auth:** PHARMACIST  
**Description:** List all pending substitution requests across all orders of the pharmacy

**Response 200:** Array of `SubstitutionResponse`

---

## 5. Inventory Management

### 5.1 Get Pharmacy Inventory
**GET** `/api/pharmacies/{pharmacyId}/inventory`  
**Auth:** PHARMACIST | MANAGER  
**Description:** List all medicines in pharmacy inventory with prices and quantities

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "medicineId": 1,
      "medicineName": "Paracetamol 500mg",
      "quantity": 50,
      "price": 5.99,
      "dosageInstructions": "Take 1 tablet every 4-6 hours",
      "lastUpdated": "2026-05-05T10:00:00Z"
    }
  ]
}
```

### 5.2 Add/Update Inventory Item
**POST** `/api/pharmacies/{pharmacyId}/inventory`  
**Auth:** PHARMACIST | MANAGER  
**Description:** Add new medicine to inventory or update existing (upsert by medicine name)

**Request:**
```json
{
  "medicineName": "Paracetamol 500mg",
  "quantity": 100,
  "price": 5.49,
  "dosageInstructions": "Take 1 tablet every 4-6 hours"
}
```

**Response 201:** `PharmacyInventoryResponse`

### 5.3 Delete Inventory Item
**DELETE** `/api/pharmacies/{pharmacyId}/inventory/{itemId}`  
**Auth:** PHARMACIST | MANAGER  
**Description:** Remove medicine from inventory

**Response 200:**
```json
{
  "success": true,
  "message": "Inventory item removed"
}
```

### 5.4 Low Stock Alerts (Pharmacist View) (Missing – needs implementation)
**GET** `/api/pharmacies/{pharmacyId}/inventory/low-stock?threshold=10`  
**Auth:** PHARMACIST  
**Description:** List inventory items where quantity < threshold

**Response 200:** Array of `PharmacyInventoryResponse`

---

## 6. Patient Management (Pharmacist)

### 6.1 Get Pharmacy Patients
**GET** `/api/pharmacies/{pharmacyId}/patients`  
**Auth:** PHARMACIST | MANAGER  
**Description:** List patients who have orders or prescriptions from this pharmacy

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "fullName": "John Doe",
      "email": "john@example.com",
      "phoneNumber": "+1234567890",
      "hasInsurance": true
    }
  ]
}
```

### 6.2 Get Patient Medical Summary (Missing – needs implementation)
**GET** `/api/pharmacies/{pharmacyId}/patients/{patientId}/summary`  
**Auth:** PHARMACIST | MANAGER  
**Description:** View limited patient medical info (allergies, notes) – with privacy consent

**Response 200:**
```json
{
  "success": true,
  "data": {
    "patientId": 1,
    "fullName": "John Doe",
    "allergies": ["Penicillin", "Aspirin"],
    "medicalNotes": "Asthma condition",
    "activePrescriptionsCount": 2,
    "lastOrderDate": "2026-05-05"
  }
}
```

---

## 7. Pharmacist Dashboard Stats (Missing – needs implementation)

**GET** `/api/pharmacies/{pharmacyId}/dashboard/stats`  
**Auth:** PHARMACIST  
**Description:** Get summary statistics for pharmacist dashboard

**Response 200:**
```json
{
  "success": true,
  "data": {
    "totalOrdersToday": 12,
    "pendingPrescriptions": 5,
    "pendingSubstitutions": 3,
    "ordersInProgress": 4,
    "readyForPickup": 2,
    "completedToday": 6,
    "lowStockItems": 8
  }
}
```

---

# 👨‍💼 MANAGER ENDPOINTS

Managers inherit pharmacist access plus additional management functions.

## 1. Pharmacy Management

### 1.1 Register Pharmacy
**POST** `/api/pharmacies/register`  
**Auth:** MANAGER (new pharmacy)  
**Description:** Register a new pharmacy (requires admin approval)

**Request:**
```json
{
  "pharmacyCode": "CENTRAL001",
  "name": "Central Pharmacy",
  "licenseNumber": "LIC123456",
  "contactInfo": "contact@central.com",
  "address": "123 Main St",
  "latitude": 40.7128,
  "longitude": -74.0060
}
```

**Response 201:** `PharmacyResponse` with status `PENDING_APPROVAL`

### 1.2 Get My Pharmacy
**GET** `/api/pharmacies/me`  
**Auth:** MANAGER  
**Description:** Get the pharmacy managed by logged-in manager

**Response 200:** `PharmacyResponse`

### 1.3 Transfer Manager
**POST** `/api/pharmacies/transfer-manager`  
**Auth:** MANAGER  
**Description:** Transfer pharmacy ownership to another manager

---

## 2. Pharmacist Management

### 2.1 Add Pharmacist
**POST** `/api/pharmacies/{pharmacyId}/pharmacists`  
**Auth:** MANAGER  
**Description:** Add a new pharmacist to the pharmacy

**Request:**
```json
{
  "fullName": "Dr. Sarah Smith",
  "email": "sarah@example.com",
  "phoneNumber": "+1234567890"
}
```

**Response 201:** `PharmacistResponse` with unique pharmacist ID

### 2.2 Get Pharmacist
**GET** `/api/pharmacies/{pharmacyId}/pharmacists/{id}`  
**Auth:** MANAGER | SUPER_ADMIN  
**Description:** Get pharmacist details

**Response 200:** `PharmacistResponse`

### 2.3 List Pharmacists
**GET** `/api/pharmacies/{pharmacyId}/pharmacists`  
**Auth:** MANAGER | SUPER_ADMIN  
**Description:** Get all pharmacists in a pharmacy

**Response 200:** Array of `PharmacistResponse`

---

## 3. Inventory Management (Manager has full access – same as pharmacist)

See Section 5 above (Pharmacist Inventory) – Manager can also perform all operations.

---

# 👑 SUPER ADMIN ENDPOINTS

## 1. Dashboard & Analytics

### 1.1 Dashboard Stats
**GET** `/api/admin/dashboard/stats`  
**Auth:** SUPER_ADMIN  
**Description:** System-wide metrics (users, orders, revenue)

**Response 200:**
```json
{
  "success": true,
  "data": {
    "totalUsers": 150,
    "totalPharmacies": 25,
    "totalOrders": 1200,
    "totalRevenue": 45000.00,
    "pendingPharmacyApprovals": 3,
    "pendingInsuranceClaims": 5
  }
}
```

### 1.2 Generate Analytics Report
**GET** `/api/admin/reports/analytics?period=MONTHLY`  
**Auth:** SUPER_ADMIN  
**Description:** Revenue, delivery times, cancellation rates

**Response 200:** `AnalyticsReportResponse`

---

## 2. User Management

### 2.1 Search Users
**POST** `/api/admin/users/search`  
**Auth:** SUPER_ADMIN  
**Description:** Advanced user search with filters (role, status, date range)

**Request:**
```json
{
  "role": "PHARMACIST",
  "isActive": true,
  "page": 0,
  "size": 20
}
```

**Response 200:** Paged `AdminUserResponse`

### 2.2 Toggle User Status
**PUT** `/api/admin/users/{id}/status`  
**Auth:** SUPER_ADMIN  
**Description:** Activate/deactivate a user

**Request:**
```json
{
  "isActive": false
}
```

**Response 200:** Empty

---

## 3. Pharmacy Approval & Management

### 3.1 Get Pending Pharmacy Details
**GET** `/api/admin/pharmacies/pending/{id}`  
**Auth:** SUPER_ADMIN  
**Description:** Get pharmacy details for admin review/approval

**Response 200:** `PharmacyApprovalDetailResponse`

### 3.2 Approve/Reject Pharmacy
**POST** `/api/admin/pharmacies/{id}/approve`  
**Auth:** SUPER_ADMIN  
**Request:**
```json
{
  "action": "APPROVE" // or "REJECT"
}
```

**Response 200:** Empty

### 3.3 Suspend Pharmacy
**POST** `/api/admin/pharmacies/{id}/suspend?reason=Violation`  
**Auth:** SUPER_ADMIN  
**Description:** Temporarily suspend a pharmacy

**Response 200:** `PharmacyResponse` with status `SUSPENDED`

### 3.4 Replace Pharmacy Manager
**PUT** `/api/admin/pharmacies/{id}/manager`  
**Auth:** SUPER_ADMIN  
**Description:** Force-transfer pharmacy to new manager

**Request:**
```json
{
  "managerEmail": "newmanager@example.com",
  "managerName": "New Manager",
  "managerPhone": "+1234567890"
}
```

**Response 200:** Empty

---

## 4. Insurance Claims Management

### 4.1 List Insurance Claims
**GET** `/api/admin/insurance-claims?status=INSURANCE_PENDING&page=0&size=10`  
**Auth:** SUPER_ADMIN  
**Description:** View all insurance payment claims

**Response 200:**
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
        "createdAt": "2026-05-06T10:05:00Z"
      }
    ],
    "totalElements": 5
  }
}
```

### 4.2 Process Insurance Claim
**POST** `/api/admin/insurance-claims/{id}/process?action=APPROVE`  
**Auth:** SUPER_ADMIN  
**Description:** Approve or reject an insurance claim

**Valid Actions:** `APPROVE`, `REJECT`

**Response 200:**
```json
{
  "success": true,
  "message": "Claim approved successfully",
  "data": {
    "id": 1,
    "status": "PAID",
    "transactionId": "INS-1-1715038400000",
    "paidAt": "2026-05-06T10:15:00Z"
  }
}
```

### 4.3 Verify Insurance Card & Set Coverage
**POST** `/api/admin/insurance-cards/{id}/verify?coveragePercentage=80.0`  
**Auth:** SUPER_ADMIN  
**Description:** Verify an insurance card and set its coverage percentage

**Response 200:**
```json
{
  "success": true,
  "message": "Insurance card verified successfully",
  "data": {
    "id": 1,
    "status": "VERIFIED",
    "coveragePercentage": 80.0
  }
}
```

---

## 5. Order Interventions

### 5.1 Force Cancel Order
**POST** `/api/admin/orders/{id}/cancel`  
**Auth:** SUPER_ADMIN  
**Request:**
```json
{
  "reason": "Suspicious activity"
}
```

**Response 200:** Empty

### 5.2 Reassign Order
**POST** `/api/admin/orders/{id}/reassign`  
**Auth:** SUPER_ADMIN  
**Request:**
```json
{
  "newPharmacyId": 2,
  "reason": "Patient requested different pharmacy"
}
```

**Response 200:** Empty

---

## 6. Inventory & Stock Alerts

### 6.1 Get Low Stock Alerts
**GET** `/api/admin/inventory/low-stock?threshold=5`  
**Auth:** SUPER_ADMIN  
**Description:** System-wide low stock items (across all pharmacies)

**Response 200:** Array of `PharmacyInventoryResponse`

---

## 7. Audit & Reporting

### 7.1 Get Audit Logs (Placeholder)
**GET** `/api/admin/audit-logs`  
**Auth:** SUPER_ADMIN  
**Description:** System audit trail (not fully implemented)

**Response 200:** Empty array

### 7.2 Generate Report
**GET** `/api/admin/reports/analytics?period=MONTHLY`  
**Auth:** SUPER_ADMIN  
**Description:** System analytics report

**Response 200:** `AnalyticsReportResponse`

---

# 📊 DATA MODELS (DTOs)

## OrderResponse
```json
{
  "id": 1,
  "status": "MATCHING",
  "orderType": "PRESCRIPTION_BASED",
  "fulfillmentType": "DELIVERY",
  "deliveryAddress": "...",
  "coveragePercentage": 80.0,
  "createdAt": "2026-05-06T10:05:00Z",
  "patientName": "John Doe",
  "pharmacyName": "Central Pharmacy",
  "items": [ ... OrderItemResponse ... ],
  "totalAmount": 50.00,
  "patientPayableAmount": 10.00,
  "insurancePayableAmount": 40.00,
  "paymentStatus": "PENDING",
  "paymentMethod": "INSURANCE"
}
```

## OrderItemResponse
```json
{
  "id": 1,
  "medicineId": 1,
  "medicineName": "Paracetamol 500mg",
  "quantity": 2,
  "unitPrice": 5.99,
  "status": "AVAILABLE"
}
```

## PaymentResponse
```json
{
  "id": 1,
  "orderId": 1,
  "totalAmount": 50.00,
  "insuranceAmount": 40.00,
  "patientAmount": 10.00,
  "status": "INSURANCE_PENDING",
  "paymentMethod": "INSURANCE",
  "transactionId": null,
  "insuranceProvider": "BlueCross",
  "failureReason": null,
  "createdAt": "2026-05-06T10:05:00Z",
  "paidAt": null
}
```

## InsuranceCardResponse
```json
{
  "id": 1,
  "providerName": "BlueCross",
  "memberId": "BC123456789",
  "frontImageUrl": "...",
  "backImageUrl": "...",
  "status": "VERIFIED",
  "coveragePercentage": 80.0,
  "createdAt": "2026-05-06T10:00:00Z"
}
```

## PrescriptionResponse
```json
{
  "id": 1,
  "fileUrl": "/api/files/prescriptions/abc.jpg",
  "fileType": "IMAGE",
  "notes": "Take once daily",
  "prescriptionDate": "2026-05-01",
  "expiryDate": "2026-06-01",
  "hasStamp": true,
  "hasSignature": true,
  "status": "VALIDATED",
  "uploadedAt": "2026-05-06T10:00:00Z"
}
```

## SubstitutionResponse
```json
{
  "id": 1,
  "orderItemId": 1,
  "orderId": 1,
  "originalMedicineId": 1,
  "originalMedicineName": "Paracetamol 500mg",
  "substituteMedicineId": 2,
  "substituteMedicineName": "Paracetamol Extra 500mg",
  "pharmacistReason": "Original out of stock",
  "patientReason": null,
  "status": "PENDING",
  "requestedAt": "2026-05-06T10:10:00Z",
  "respondedAt": null
}
```

## PharmacyResponse
```json
{
  "id": 1,
  "pharmacyCode": "CENTRAL001",
  "name": "Central Pharmacy",
  "address": "123 Main St",
  "contactInfo": "contact@central.com",
  "licenseNumber": "LIC123456",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "status": "ACTIVE",
  "managerProfile": { ... },
  "createdAt": "2026-05-01T10:00:00Z"
}
```

---

# 🌐 WEBSOCKET NOTIFICATIONS

Connect to WebSocket endpoint:  
`ws://localhost:8080/ws` (or `wss://` in production)

**Subscribe Topics:**

| Topic | Description | Payload |
|-------|-------------|---------|
| `/topic/orders/{userId}` | Order status updates | `{type: "ORDER_STATUS_UPDATE", orderId, status, message, order}` |
| `/topic/substitutions/{userId}` | Substitution request | `{type: "SUBSTITUTION_REQUEST", orderId, originalMedicine, substituteMedicine}` |
| `/topic/pharmacy/{pharmacyId}/orders` | New order for pharmacy | `{type: "NEW_ORDER", orderId, patientName, itemCount}` |
| `/topic/insurance/{userId}` | Insurance verification | `{type: "INSURANCE_VERIFICATION", insuranceCardId, approved}` |

---

# 🧪 TEST USERS

| Role | Email | Password | User ID |
|------|-------|----------|---------|
| Patient | `patient@example.com` | `Patient123!` | 1 |
| Pharmacist | `pharmacist@example.com` | `Pharmacist123!` | 2 |
| Manager | `manager@example.com` | `Manager123!` | 3 |
| Admin | `admin@example.com` | `Admin123!` | 4 |

---

# ⚠️ ERROR RESPONSES

All endpoints return standardized error format:

**400 Bad Request:**
```json
{
  "success": false,
  "message": "Validation failed: Delivery address is required for delivery orders"
}
```

**401 Unauthorized:**
```json
{
  "success": false,
  "message": "Unauthorized"
}
```

**403 Forbidden:**
```json
{
  "success": false,
  "message": "Access Denied: This order is not assigned to your pharmacy"
}
```

**404 Not Found:**
```json
{
  "success": false,
  "message": "Order not found with id: 999"
}
```

---

# 📁 FILE UILESHOSTING

File uploads are stored under `/uploads/` and served via:

**GET** `/api/files/{path}`  
Example: `GET /api/files/prescriptions/abc123.jpg` returns the image

---

*End of API Documentation*
