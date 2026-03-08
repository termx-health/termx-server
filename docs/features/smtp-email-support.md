---
name: Add SMTP Email Support
overview: Add SMTP email sending functionality using Micronaut Email, with configuration management, graceful fallback when unconfigured, test endpoint accessible only from tests, and shell script for testing.
todos:
  - id: add-dependency
    content: Add io.micronaut.email:micronaut-email-javamail dependency to termx-app/build.gradle
    status: completed
  - id: create-email-service
    content: Create EmailService with SMTP sending logic and configuration checking
    status: completed
  - id: create-dtos
    content: Create EmailConfigStatus, EmailTestRequest, and EmailTestResult DTOs
    status: completed
  - id: create-controller
    content: Create EmailManagementController with status and test endpoints
    status: completed
  - id: update-app-config
    content: Add email SMTP configuration to application.yml
    status: completed
  - id: update-docker-env
    content: Add commented SMTP parameters to deployment/docker-compose/server.env
    status: completed
  - id: create-test-script
    content: Create test-email.sh shell script for testing
    status: completed
  - id: create-integration-test
    content: Create EmailServiceTest.groovy integration test
    status: completed
isProject: false
---

# Add SMTP Email Support to TermX

## Overview

Implement SMTP email sending functionality using Micronaut Email (native Micronaut module with Jakarta Mail). The implementation will include configuration management, graceful handling when SMTP is not configured, a test-only endpoint, and testing utilities.

## Technology Choice

**Micronaut Email** with Jakarta Mail backend

- Native Micronaut integration
- Industry-standard Jakarta Mail (JavaMail) under the hood
- Built-in templating support
- Configuration through application.yml
- Async sending support

## Implementation Components

### 1. Add Dependency

**File:** `[termx-app/build.gradle](termx-app/build.gradle)`

Add to dependencies section:

```gradle
implementation("io.micronaut.email:micronaut-email-javamail")
```

### 2. Create Email Service

**File:** `[termx-core/src/main/java/com/kodality/termx/core/sys/email/EmailService.java](termx-core/src/main/java/com/kodality/termx/core/sys/email/EmailService.java)`

**Structure:**

```java
@Singleton
@RequiredArgsConstructor
public class EmailService {
  @Property(name = "email.smtp.enabled")
  private Optional<Boolean> emailEnabled;
  
  private final Optional<EmailSenderProvider> emailSender;
  
  public void sendEmail(String to, String subject, String body);
  public boolean isConfigured();
  public List<String> getRequiredConfiguration();
}
```

**Key Features:**

- Check if SMTP is configured before sending
- Return gracefully if not configured (log warning, don't throw exception)
- Provide `isConfigured()` method to check status
- Provide `getRequiredConfiguration()` to list missing parameters
- Support both HTML and plain text emails

### 3. Configuration in application.yml

**File:** `[termx-app/src/main/resources/application.yml](termx-app/src/main/resources/application.yml)`

Add after line 79 (after termx section):

```yaml
email:
  smtp:
    enabled: ${SMTP_ENABLED:false}
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    from: ${SMTP_FROM:noreply@termx.org}
    auth: ${SMTP_AUTH:true}
    starttls:
      enable: ${SMTP_STARTTLS:true}
```

**Rationale:**

- Default `enabled: false` - no emails sent unless explicitly configured
- Environment variable override support
- Standard SMTP settings (host, port, credentials)
- TLS/STARTTLS support for secure connections
- Configurable "from" address

### 4. Update Docker Environment Files

**File:** `[deployment/docker-compose/server.env](deployment/docker-compose/server.env)`

Add at the end (commented out):

```bash
# SMTP Email Configuration (optional)
# Uncomment and configure to enable email sending
#SMTP_ENABLED=true
#SMTP_HOST=smtp.gmail.com
#SMTP_PORT=587
#SMTP_USERNAME=your-email@gmail.com
#SMTP_PASSWORD=your-app-password
#SMTP_FROM=noreply@termx.org
#SMTP_AUTH=true
#SMTP_STARTTLS=true
```

**Documentation comment to add:**

```bash
# Email configuration is optional. When not configured, the system will
# function normally but email notifications will be silently skipped.
# To check configuration status: GET /management/email/status
```

### 5. Create Email Configuration Status Endpoint

**File:** `[termx-core/src/main/java/com/kodality/termx/core/sys/email/EmailManagementController.java](termx-core/src/main/java/com/kodality/termx/core/sys/email/EmailManagementController.java)`

**Purpose:** Internal endpoint for checking email configuration status

```java
@Controller("/management/email")
@RequiredArgsConstructor
public class EmailManagementController {
  private final EmailService emailService;
  
  @Get("/status")
  public EmailConfigStatus getStatus();
  
  @Post("/test")
  @Requires(property = "auth.dev.allowed", value = StringUtils.TRUE)
  public EmailTestResult sendTestEmail(@Body EmailTestRequest request);
}
```

**Security:**

- `/status` endpoint - No `@Authorized`, but not in public endpoints list (requires auth token)
- `/test` endpoint - Only available when `auth.dev.allowed=true` (development mode)
- NOT exposed via REST API in production (requires dev flag)

### 6. Create Test Controller for Integration Tests

**File:** `[termx-integtest/src/test/groovy/com/kodality/termx/core/EmailServiceTest.groovy](termx-integtest/src/test/groovy/com/kodality/termx/core/EmailServiceTest.groovy)`

**Purpose:** Groovy test class that can call the test endpoint

```groovy
class EmailServiceTest extends TermxIntegTest {
  def "should return configuration status"() {
    when:
    def response = client.GET("/management/email/status").join()
    
    then:
    response.statusCode() == 200
  }
  
  def "should send test email when configured"() {
    // Only runs if SMTP configured in test environment
  }
}
```

### 7. Create Shell Script for Testing

**File:** `[termx-app/test-email.sh](termx-app/test-email.sh)`

**Purpose:** Test email sending when application is running

```bash
#!/usr/bin/env bash

# Test SMTP Email Configuration for TermX Server
# Usage: ./test-email.sh [recipient-email]

TERMX_URL="${TERMX_URL:-http://localhost:8200}"
RECIPIENT="${1:-test@example.com}"

echo "Testing TermX Email Configuration..."
echo "Server: $TERMX_URL"
echo ""

# Check email configuration status
echo "1. Checking email configuration status..."
STATUS=$(curl -s -X GET "$TERMX_URL/management/email/status")
echo "Status: $STATUS"
echo ""

# Send test email (only works in dev mode)
echo "2. Sending test email to: $RECIPIENT"
RESULT=$(curl -s -X POST "$TERMX_URL/management/email/test" \
  -H "Content-Type: application/json" \
  -d "{\"recipient\": \"$RECIPIENT\", \"subject\": \"TermX Test Email\", \"body\": \"Test email from TermX server\"}")

echo "Result: $RESULT"
```

**Features:**

- Checks configuration status first
- Sends test email with customizable recipient
- Clear output and error messages
- Environment variable support for server URL

### 8. Email Service Implementation Details

**EmailConfigStatus Response:**

```json
{
  "configured": true/false,
  "missingParameters": ["SMTP_HOST", "SMTP_USERNAME", "SMTP_PASSWORD"],
  "from": "noreply@termx.org",
  "smtpHost": "smtp.gmail.com" // only if configured
}
```

**EmailTestRequest:**

```java
public class EmailTestRequest {
  private String recipient;
  private String subject;
  private String body;
}
```

**EmailTestResult:**

```json
{
  "sent": true/false,
  "error": "SMTP not configured" or null,
  "message": "Email sent successfully to test@example.com"
}
```

## Security Considerations

**1. Test Endpoint Protection:**

- Requires `@Requires(property = "auth.dev.allowed", value = StringUtils.TRUE)`
- Only available in development mode
- Never accessible in production

**2. Management Endpoint:**

- Path: `/management/email/`*
- NOT in `auth.public.endpoints` list
- Requires valid OAuth token (same as other endpoints)
- Cannot be accessed via public REST API

**3. No Direct Email API:**

- No public endpoint for sending arbitrary emails
- Test endpoint only for SMTP verification
- Actual email sending only through internal service calls

## Configuration Management

**Graceful Degradation:**

```java
if (!emailService.isConfigured()) {
  log.warn("SMTP not configured, skipping email notification");
  return;
}
```

**When SMTP Not Configured:**

- Service returns `isConfigured() = false`
- Email sending methods log warning and return gracefully
- No exceptions thrown
- Application continues normal operation

**Required Parameters:**

1. `SMTP_HOST` - SMTP server address
2. `SMTP_USERNAME` - Authentication username
3. `SMTP_PASSWORD` - Authentication password

**Optional Parameters:**

- `SMTP_PORT` (default: 587)
- `SMTP_FROM` (default: [noreply@termx.org](mailto:noreply@termx.org))
- `SMTP_AUTH` (default: true)
- `SMTP_STARTTLS` (default: true)
- `SMTP_ENABLED` (default: false)

## Example SMTP Providers Configuration

**Gmail:**

```bash
SMTP_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password  # Use App Password, not account password
SMTP_FROM=your-email@gmail.com
```

**AWS SES:**

```bash
SMTP_ENABLED=true
SMTP_HOST=email-smtp.us-east-1.amazonaws.com
SMTP_PORT=587
SMTP_USERNAME=your-ses-smtp-username
SMTP_PASSWORD=your-ses-smtp-password
SMTP_FROM=verified@yourdomain.com
```

**SendGrid:**

```bash
SMTP_ENABLED=true
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USERNAME=apikey
SMTP_PASSWORD=your-sendgrid-api-key
SMTP_FROM=verified@yourdomain.com
```

## Testing Flow

```mermaid
graph TD
    Start[Start Application] --> CheckConfig{SMTP Configured?}
    CheckConfig -->|No| Disabled[Email Service Disabled]
    CheckConfig -->|Yes| Enabled[Email Service Enabled]
    
    Disabled --> StatusCheck[GET /management/email/status]
    StatusCheck --> StatusResp[Returns: configured=false, missingParameters]
    
    Enabled --> StatusCheck2[GET /management/email/status]
    StatusCheck2 --> StatusResp2[Returns: configured=true]
    
    StatusResp2 --> DevMode{Dev Mode?}
    DevMode -->|Yes| TestEndpoint[POST /management/email/test]
    DevMode -->|No| NoTest[Test endpoint not available]
    
    TestEndpoint --> SendEmail[Send test email]
    SendEmail --> Success[Returns: sent=true, message]
    
    TestEndpoint --> Failed[SMTP Error]
    Failed --> ErrorResp[Returns: sent=false, error]
```



## Files to Create

1. **EmailService.java** - Main email service with SMTP logic
2. **EmailManagementController.java** - Status and test endpoints
3. **EmailConfigStatus.java** - Response DTO for status
4. **EmailTestRequest.java** - Request DTO for test
5. **EmailTestResult.java** - Response DTO for test result
6. **EmailServiceTest.groovy** - Integration tests
7. **test-email.sh** - Shell script for manual testing

## Files to Modify

1. **termx-app/build.gradle** - Add Micronaut Email dependency
2. **termx-app/src/main/resources/application.yml** - Add email configuration
3. **deployment/docker-compose/server.env** - Add commented SMTP variables

## Integration Points

**Future Usage Examples:**

```java
@RequiredArgsConstructor
public class SomeNotificationService {
  private final EmailService emailService;
  
  public void sendNotification(User user, String message) {
    if (emailService.isConfigured()) {
      emailService.sendEmail(user.getEmail(), "Notification", message);
    } else {
      log.info("Email not configured, notification not sent");
    }
  }
}
```

## Testing Instructions

**1. Check Status (No Config):**

```bash
curl http://localhost:8200/management/email/status
# Expected: {"configured": false, "missingParameters": ["SMTP_HOST", ...]}
```

**2. Configure SMTP (Dev Mode):**

```bash
export SMTP_ENABLED=true
export SMTP_HOST=smtp.gmail.com
export SMTP_USERNAME=your-email@gmail.com
export SMTP_PASSWORD=your-app-password
# Restart application
```

**3. Check Status (With Config):**

```bash
curl http://localhost:8200/management/email/status
# Expected: {"configured": true, "from": "noreply@termx.org", ...}
```

**4. Send Test Email (Dev Mode Only):**

```bash
./termx-app/test-email.sh test@example.com
# Or using script directly
```

**5. Try Test Endpoint in Production:**

```bash
curl -X POST http://localhost:8200/management/email/test
# Expected: 404 Not Found (endpoint not available without auth.dev.allowed=true)
```

## Dependencies Impact

**New Dependency:**

- `io.micronaut.email:micronaut-email-javamail` - ~500KB
- Includes Jakarta Mail API and SMTP transport

**Runtime Impact:**

- No impact when disabled (lazy initialization)
- Minimal memory footprint when enabled
- Async sending prevents blocking

## Configuration Validation

The service will validate:

1. Host is not empty
2. Port is valid (1-65535)
3. Username/password provided if auth enabled
4. From address is valid email format

If validation fails, service reports as "not configured" with specific missing parameters.

## Testing SMTP Connectivity

Before configuring TermX, verify your SMTP server connectivity:

### Test Port Connectivity
```bash
# Test port 25 (plain SMTP)
nc -zv smtp-relay.gmail.com 25

# Test port 587 (STARTTLS)
nc -zv smtp-relay.gmail.com 587

# Test port 465 (SSL/TLS - legacy)
nc -zv smtp.gmail.com 465
```

### Test STARTTLS Capability
```bash
# Verify STARTTLS works on port 587
openssl s_client -connect smtp-relay.gmail.com:587 -starttls smtp

# Expected output should show:
# - CONNECTED
# - SSL handshake successful
# - 250 SMTPUTF8 (or similar success response)
```

### Test Plain SMTP (Port 25)
```bash
# Manual SMTP test
telnet smtp-relay.gmail.com 25

# Commands to type after connection:
# EHLO localhost
# MAIL FROM:<test@example.com>
# RCPT TO:<your-email@example.com>
# QUIT
```

### Common SMTP Ports
- **Port 25**: Plain SMTP, typically for server-to-server (may be blocked by ISPs)
- **Port 587**: Submission port with STARTTLS (recommended for most modern setups)
- **Port 465**: SMTP over SSL/TLS (legacy, use 587 instead)

## Troubleshooting SMTP Issues

### Issue: `[EOF]` or Connection Timeout

**Cause**: Port mismatch or STARTTLS misconfiguration

**Solutions**:
1. Verify port is open: `nc -zv smtp-relay.gmail.com 587`
2. Enable STARTTLS for port 587: `SMTP_STARTTLS=true`
3. Try port 25 instead: `SMTP_PORT=25` and `SMTP_STARTTLS=false`
4. Check firewall/network policies

### Issue: Authentication Failed

**Cause**: Credentials required but not provided

**Solutions**:
1. Set `SMTP_AUTH=true`
2. Provide valid credentials: `SMTP_USERNAME` and `SMTP_PASSWORD`
3. For Gmail: Use App Passwords, not regular password

### Issue: Timeout on Port 25

**Cause**: Many ISPs block outbound port 25

**Solutions**:
1. Use port 587 with STARTTLS instead
2. Configure ISP/hosting provider to allow port 25
3. Use mail relay service

### Issue: Certificate Validation Errors

**Cause**: SSL certificate trust issues

**Solutions**:
- TermX automatically trusts configured SMTP hosts when STARTTLS is enabled
- Verify hostname matches certificate: `openssl s_client -connect <host>:587 -starttls smtp`

### Issue: Google Workspace "Invalid credentials for relay" (Error 550-5.7.1)

**Cause**: The `FROM` email domain doesn't match domains registered in Google Workspace SMTP Relay

**Error message example**:
```
550-5.7.1 Invalid credentials for relay [YOUR_IP]. The IP address you've
registered in your Workspace SMTP Relay service doesn't match the
domain of the account this email is being sent from.
```

**Solutions**:
1. **Use a registered domain** (Recommended): Update `SMTP_FROM` to use a domain that's registered in your Google Workspace
   ```bash
   # If your Workspace manages termx.org, use:
   SMTP_FROM=noreply@termx.org
   
   # NOT: noreply@termx.dev (if termx.dev isn't in your Workspace)
   ```

2. **Add domain to Google Workspace**: Go to [Google Admin Console → Apps → Google Workspace → Gmail → Routing → SMTP relay service](https://admin.google.com/ac/apps/gmail/smtprelay) and add the sender domain to allowed domains for your IP address

3. **Use SMTP authentication**: If you can't control the relay settings, use authenticated SMTP instead

**Important**: The EHLO domain and FROM email domain must both be registered in your Google Workspace when using IP-based relay authentication.