#!/bin/bash

echo "Deploying CORS fixes to Railway..."
echo "=================================="

# Add all changes
git add .

# Commit
git commit -m "Fix CORS on all endpoints - disable RequestLoggingFilter temporarily"

# Push
git push origin main

echo ""
echo "Deployment initiated!"
echo "Check Railway dashboard for build status"
echo "Wait 2-3 minutes for deployment to complete"
