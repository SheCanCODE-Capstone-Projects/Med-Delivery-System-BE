# Med-Delivery-System-BE

A scalable backend system for a smart pharmacy network platform that connects patients(users), pharmacies, pharmacists, and insurance workflows to enable efficient prescription processing and medicine delivery.


#  Project Overview

MedDelivery Backend is a Spring Boot-based system designed to:

* Manage patient(user) authentication and profiles
* Handle insurance verification workflow
* Process prescriptions and medicine requests
* Connect patients with nearby pharmacies
* Enable pharmacists to validate and dispense medicines
* Support intelligent pharmacy matching and substitution flow
  

#  System Architecture

The backend follows a modular layered architecture:

* Controller Layer (REST APIs + External API)
* Service Layer (Business Logic)
* Repository Layer (Data Access)
* Domain Layer (Entities)
* Security Layer (JWT + OAuth2)


#  Core User Roles

## 1. Patient

* Register/login (Google OAuth2 or phone OTP)
* Upload prescriptions
* Provide insurance details
* Place medicine requests
* Track order status

## 2. Pharmacy Manager

* Manage pharmacy profile
* Adds the pharmacists
* View and assign dispensing task
* Monitor inventory and orders

## 3. Pharmacist

* Receive assigned dispensing task
* Validate prescriptions
* Dispense medicines
* Suggest substitutions where needed
* Log dispensing activity

## 4. Super Admin

* Approve pharmacy registrations
* Monitor system-wide activity
* Manage platform governance


# 🔐 Authentication & Security

* JWT-based authentication
* OAuth2 Google login support
* OTP-based phone authentication
* Role-Based Access Control (RBAC)

Roles:

* ROLE_USER (referencing the patient)
* ROLE_PHARMACY_ADMIN
* ROLE_PHARMACIST
* ROLE_SUPER_ADMIN


#  Key Features

## Patient Features

* Google / phone authentication
* Insurance card upload (front/back)
* Location tracking for nearby pharmacy discovery
* Prescription upload (PDF/JPG/JPEG)
* Order tracking system

## Pharmacy Features

* Pharmacy registration & approval workflow
* Inventory management
* Prescription request handling
* Substitution recommendation

## Pharmacist Features

* Unique pharmacist ID generation

  * Format: `PHARMACYCODE-XXXXX`
* Secure login
* Prescription validation
* Medicine dispensing confirmation


# 📦 Core Modules

## 1. Authentication Module

* JWT token generation & validation
* OAuth2 Google login
* OTP-based login system

## 2. User Management Module

* Profile management
* Role management

## 3. Pharmacy Module

* Pharmacy registration
* Approval workflow
* Manager dashboard

## 4. Prescription Module

* Prescription upload
* Basic validation (date, format checks)
* Status tracking

## 5. Matching Engine Module

* Parallel pharmacy request dispatch
* Coverage calculation
* Pharmacy ranking system

Formula:

```
coverage = available_medicines / total_medicines
```

Decision rules:

* 100% match → auto-select pharmacy
* Partial match → highest coverage + nearest pharmacy

## 6. Order Module

* Order lifecycle management
* Status tracking
* Assignment to pharmacy

Order States:

* UPLOADED
* MATCHING
* ASSIGNED
* IN_PROGRESS
* READY_FOR_PICKUP
* COMPLETED

## 7. Insurance Module

* Insurance card upload
* Status tracking
* Pharmacy-based verification

Statuses:

* UNVERIFIED
* PENDING_VERIFICATION
* VERIFIED

---

#  System Workflow

## 1. Patient Flow

1. Login (Google / Phone OTP)
2. Setup profile
3. Upload insurance card
4. Enable location
5. Choose service:

   * Private medicine request
   * Prescription-based request

## 2. Prescription Flow

1. Upload prescription
2. System validates basic rules
3. Send to multiple pharmacies (parallel)
4. Collect responses
5. Rank pharmacies
6. Assign best match
7. Pharmacy confirms and processes order

## 3. Pharmacy Flow

1. Register pharmacy
2. Wait for admin approval
3. Add pharmacists
4. Receive prescriptions order
5. Confirm availability
6. Dispense medicines

## 4. Pharmacist Flow

1. Login using unique ID
2. View assigned prescriptions
3. Validate prescription
4. Dispense medicine
5. Log completion



#  Substitution Flow

* Triggered when medicine is unavailable
* Pharmacist suggests alternative
* Patient must approve
* System logs decision



# ⚙️ Tech Stack

* Java 21+
* Spring Boot
* Spring Security (JWT + OAuth2)
* Spring Data JPA
* PostgreSQL
* caching layer (Redis)
* WebSockets for real-time updates
* Maven
* Containerisation(Docker)


# 🔒 Security Design

* JWT authentication for all requests
* Role-based access control (RBAC)
* OTP verification for sensitive actions
* Audit logs for dispensing medicines


# 📈 Scalability Plan

* Future: Microservices (Auth, Pharmacy, Orders, Matching)
* Integrate the Payment API

# 📄 License

MIT License


