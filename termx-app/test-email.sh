#!/usr/bin/env bash

# Test SMTP Email Configuration for TermX Server
# Usage: ./test-email.sh [recipient-email]

set -e

TERMX_URL="${TERMX_URL:-http://localhost:8200}"
RECIPIENT="${1:-test@example.com}"

echo "=========================================="
echo "Testing TermX Email Configuration"
echo "=========================================="
echo "Server: $TERMX_URL"
echo "Recipient: $RECIPIENT"
echo ""

# Check email configuration status
echo "1. Checking email configuration status..."
echo "------------------------------------------"
STATUS=$(curl -s -X GET "$TERMX_URL/management/email/status" -H "Accept: application/json")
echo "$STATUS" | python3 -m json.tool 2>/dev/null || echo "$STATUS"
echo ""

# Extract configured status (basic parsing)
IS_CONFIGURED=$(echo "$STATUS" | grep -o '"configured":[^,}]*' | cut -d: -f2 | tr -d ' ')

if [ "$IS_CONFIGURED" != "true" ]; then
  echo "⚠️  Email is not fully configured."
  echo "Please configure the required SMTP parameters and restart the server."
  echo ""
  exit 1
fi

# Send test email (only works in dev mode)
echo "2. Sending test email..."
echo "------------------------------------------"
RESULT=$(curl -s -X POST "$TERMX_URL/management/email/test" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d "{\"recipient\": \"$RECIPIENT\", \"subject\": \"TermX Test Email\", \"body\": \"This is a test email from TermX server at $(date). If you receive this, SMTP is configured correctly.\"}")

echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT"
echo ""

# Check if email was sent
WAS_SENT=$(echo "$RESULT" | grep -o '"sent":[^,}]*' | cut -d: -f2 | tr -d ' ')

if [ "$WAS_SENT" = "true" ]; then
  echo "✅ Test email sent successfully!"
  echo "Please check $RECIPIENT inbox."
else
  echo "❌ Failed to send test email."
  echo "Note: Test endpoint only works in development mode (auth.dev.allowed=true)"
fi
echo ""
