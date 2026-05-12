#!/bin/bash

echo "Testing MedDelivery API Registration Endpoint"
echo "=============================================="
echo ""

# Test 1: Health Check
echo "1. Testing Health Check..."
curl -X GET "http://med-delivery-system-be-production.up.railway.app/actuator/health" \
  -H "accept: application/json" \
  -w "\nStatus Code: %{http_code}\n\n"

# Test 2: Registration
echo "2. Testing Registration Endpoint..."
curl -X POST "http://med-delivery-system-be-production.up.railway.app/api/auth/register" \
  -H "accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{
  "fullName": "Test User",
  "email": "test@example.com",
  "phoneNumber": "+1234567890"
}' \
  -w "\nStatus Code: %{http_code}\n\n"

echo "=============================================="
echo "Test Complete"
