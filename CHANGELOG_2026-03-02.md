# TermX Server - Changes for March 2, 2026

## Email Notification System for Terminology Imports

### New Features

#### 1. Multi-Recipient Import Notifications
- **Feature**: Automated email notifications sent when terminology imports complete, fail, or finish with warnings
- **Configuration**: New `SMTP_TO_IMPORT` environment variable supporting comma-separated recipient lists
- **Format**: Professional HTML-formatted emails with comprehensive import statistics
- **Coverage**: All import types (LOINC, SNOMED, CodeSystem, ValueSet, MapSet, Space)

#### 2. Email Service Enhancements
**File**: `termx-core/src/main/java/com/kodality/termx/core/sys/email/EmailService.java`
- Added multi-recipient support via `sendToMultiple()` method
- New helper methods: `getImportRecipients()`, `hasImportRecipients()`
- Smart recipient parsing with comma-separated list support
- Recipients automatically trimmed and validated

#### 3. Import Notification Service
**File**: `termx-core/src/main/java/com/kodality/termx/core/sys/job/logger/ImportNotificationService.java`
- Professional HTML email templates with modern design
- Status-specific badges (✓ Completed, ✗ Failed, ⚠ Warnings)
- Comprehensive statistics: duration, record counts, status summaries
- Detailed sections for errors, warnings, and success messages
- Automatic HTML escaping for security
- Responsive email design compatible with all email clients

#### 4. JobLog Service Integration
**File**: `termx-core/src/main/java/com/kodality/termx/core/sys/job/JobLogService.java`
- Automatic notification trigger on import completion
- Smart detection of import jobs (any job type containing "import" or "sync")
- Graceful error handling - notification failures don't break imports
- Works for all TermX internal imports (LOINC, CodeSystem, ValueSet, MapSet, Space)

#### 5. SNOMED Import Tracking & Polling
**New Files**:
- `termx-api/src/main/java/com/kodality/termx/snomed/rf2/SnomedImportTracking.java` - Tracking entity
- `snomed/src/main/java/com/kodality/termx/snomed/integration/SnomedImportTrackingRepository.java` - Repository
- `snomed/src/main/java/com/kodality/termx/snomed/integration/SnomedImportPollingService.java` - Background poller
- `termx-core/src/main/resources/sys/changelog/sys/70-snomed_import_tracking.sql` - Database migration

**Features**:
- Background polling service checks SNOMED import status every 30 seconds
- Tracks Snowstorm job ID, branch path, type, status, timestamps
- Sends HTML-formatted completion emails with module statistics
- Optimized database indexes for efficient querying
- Only activates when import recipients are configured

#### 6. Configuration Updates
**Application Configuration** (`termx-app/src/main/resources/application.yml`):
```yaml
micronaut:
  email:
    import:
      recipients: ${SMTP_TO_IMPORT:}
```

**Docker Environment** (`deployment/docker-compose/server.env`):
```bash
#SMTP_TO_IMPORT=admin@company.org,imports@company.org
```

#### 7. Integration Tests
**File**: `termx-integtest/src/test/groovy/com/kodality/termx/core/ImportNotificationTest.groovy`
- Tests for recipient configuration
- Tests for notification service functionality
- Tests for HTML email formatting
- Tests for JobLog integration

### Email Notification Details

#### Email Content Includes:
- Job type and source
- Job ID for tracking
- Start and finish timestamps
- Duration (formatted as hours/minutes/seconds)
- Success, warning, and error counts
- Detailed error messages (if any)
- Warning messages (if any)
- Success messages (limited to first 10)

#### Email Appearance:
- Clean, modern HTML design
- Color-coded status badges
- Professional table layout for statistics
- Responsive design for mobile devices
- TermX branding in header
- Automated footer with server information

### Configuration Examples

> **Important**: When using Google Workspace SMTP Relay or similar services with IP-based authentication, ensure the `SMTP_FROM` domain is registered in your SMTP relay service for your IP address. Using an unregistered domain will result in "Invalid credentials for relay" errors. Example: If your Google Workspace manages `termx.org`, use `noreply@termx.org` (not `noreply@termx.dev`).

#### Option 1: SMTP Relay with STARTTLS (Port 587) - Recommended

For SMTP relays that require encrypted connection (most modern services):

```yaml
# application.yml or application-local.yml
micronaut:
  email:
    enabled: true
    from:
      email: noreply@termx.org
    import:
      recipients: admin@company.org,imports@company.org

javamail:
  authentication:
    username: no-auth-required  # Or actual username if auth is needed
    password: no-auth-required  # Or actual password if auth is needed
  properties:
    mail:
      smtp:
        host: smtp-relay.gmail.com
        port: 587
        auth: false              # true if username/password required
        starttls:
          enable: true           # Required for port 587
```

**Environment variables equivalent**:
```bash
SMTP_ENABLED=true
SMTP_HOST=smtp-relay.gmail.com
SMTP_PORT=587
SMTP_FROM=noreply@termx.org
SMTP_AUTH=false
SMTP_STARTTLS=true
SMTP_TO_IMPORT=admin@company.org,imports@company.org
```

#### Option 2: Plain SMTP (Port 25) - For IP Whitelisting

For SMTP relays that use IP whitelisting without encryption:

```yaml
micronaut:
  email:
    enabled: true
    from:
      email: noreply@termx.org
    import:
      recipients: admin@company.org,imports@company.org

javamail:
  authentication:
    username: no-auth-required
    password: no-auth-required
  properties:
    mail:
      smtp:
        host: smtp-relay.gmail.com
        port: 25
        auth: false
        starttls:
          enable: false          # No encryption on port 25
```

**Environment variables equivalent**:
```bash
SMTP_ENABLED=true
SMTP_HOST=smtp-relay.gmail.com
SMTP_PORT=25
SMTP_FROM=noreply@termx.org
SMTP_AUTH=false
SMTP_STARTTLS=false
SMTP_TO_IMPORT=admin@company.org,imports@company.org
```

#### Option 3: Authenticated SMTP with STARTTLS (Port 587)

For SMTP servers requiring username/password authentication:

```yaml
micronaut:
  email:
    enabled: true
    from:
      email: noreply@termx.org
    import:
      recipients: admin@company.org,imports@company.org

javamail:
  authentication:
    username: your-smtp-username
    password: your-smtp-password
  properties:
    mail:
      smtp:
        host: smtp.gmail.com
        port: 587
        auth: true               # Authentication required
        starttls:
          enable: true
```

**Environment variables equivalent**:
```bash
SMTP_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-smtp-username
SMTP_PASSWORD=your-app-password
SMTP_FROM=noreply@termx.org
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_TO_IMPORT=admin@company.org,imports@company.org
```

### Testing SMTP Connectivity

Before configuring TermX, verify your SMTP server connectivity:

#### Test Port Connectivity
```bash
# Test port 25 (plain SMTP)
nc -zv smtp-relay.gmail.com 25

# Test port 587 (STARTTLS)
nc -zv smtp-relay.gmail.com 587

# Test port 465 (SSL/TLS - legacy)
nc -zv smtp.gmail.com 465
```

#### Test STARTTLS Capability
```bash
# Verify STARTTLS works on port 587
openssl s_client -connect smtp-relay.gmail.com:587 -starttls smtp

# Expected output should show:
# - CONNECTED
# - SSL handshake successful
# - 250 SMTPUTF8 (or similar success response)
```

#### Test Plain SMTP (Port 25)
```bash
# Manual SMTP test
telnet smtp-relay.gmail.com 25

# Commands to type after connection:
# EHLO localhost
# MAIL FROM:<test@example.com>
# RCPT TO:<your-email@example.com>
# QUIT
```

#### Common SMTP Ports
- **Port 25**: Plain SMTP, typically for server-to-server (may be blocked by ISPs)
- **Port 587**: Submission port with STARTTLS (recommended for most modern setups)
- **Port 465**: SMTP over SSL/TLS (legacy, use 587 instead)

### Troubleshooting SMTP Issues

#### Issue: `[EOF]` or Connection Timeout

**Cause**: Port mismatch or STARTTLS misconfiguration

**Solutions**:
1. Verify port is open: `nc -zv smtp-relay.gmail.com 587`
2. Enable STARTTLS for port 587: `SMTP_STARTTLS=true`
3. Try port 25 instead: `SMTP_PORT=25` and `SMTP_STARTTLS=false`
4. Check firewall/network policies

#### Issue: Authentication Failed

**Cause**: Credentials required but not provided

**Solutions**:
1. Set `SMTP_AUTH=true`
2. Provide valid credentials: `SMTP_USERNAME` and `SMTP_PASSWORD`
3. For Gmail: Use App Passwords, not regular password

#### Issue: Timeout on Port 25

**Cause**: Many ISPs block outbound port 25

**Solutions**:
1. Use port 587 with STARTTLS instead
2. Configure ISP/hosting provider to allow port 25
3. Use mail relay service

#### Issue: Certificate Validation Errors

**Cause**: SSL certificate trust issues

**Solutions**:
- TermX automatically trusts configured SMTP hosts when STARTTLS is enabled
- Verify hostname matches certificate: `openssl s_client -connect <host>:587 -starttls smtp`

#### Issue: Google Workspace "Invalid credentials for relay" (Error 550-5.7.1)

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

3. **Use SMTP authentication**: If you can't control the relay settings, use authenticated SMTP instead (Option 3 configuration above)

**Important**: The EHLO domain and FROM email domain must both be registered in your Google Workspace when using IP-based relay authentication.

### Behavior

- **No recipients configured**: Notifications are silently skipped (no errors)
- **SMTP not configured**: Notifications are logged but not sent
- **Import success**: Green badge with success statistics
- **Import with warnings**: Yellow badge with warning details
- **Import failure**: Red badge with error details
- **SNOMED imports**: Automatically polled and notified when complete

---

## Package Refactoring

### SNOMED Package Structure Improvement
**Changed**: `com.kodality.termx.snomed.snomed.*` → `com.kodality.termx.snomed.integration.*`

#### Rationale
- Eliminated redundant `snomed.snomed` naming
- Better reflects the purpose: integration with external Snowstorm server
- Aligns with existing package structure (`snomed.client`, `snomed.task`, `snomed.ts`)
- More professional and maintainable naming convention

#### Files Moved (11 files)
**Main Integration Services**:
- `SnomedService.java`
- `SnomedController.java`
- `SnomedInterceptor.java`
- `SnomedImportTrackingRepository.java`
- `SnomedImportPollingService.java`

**Translation Subpackage** (`integration/translation/`):
- `SnomedTranslationService.java`
- `SnomedTranslationRepository.java`
- `SnomedTranslationActionService.java`
- `SnomedTranslationProvenanceService.java`

**RF2 Subpackage** (`integration/rf2/`):
- `SnomedRF2Service.java`

**CSV Subpackage** (`integration/csv/`):
- `SnomedConceptCsvService.java`

#### Impact
- All import statements updated across codebase
- OpenAPI configuration updated in `TermxApplication.java`
- Full compilation and test suite verified
- **No breaking changes** to external APIs or database schema

---

## Technical Details

### Database Changes
- New table: `sys.snomed_import_tracking`
- Indexes on `status` and `notified` columns for efficient polling
- Automatic cleanup capability for old tracking records

### Performance Considerations
- SNOMED polling: 30-second intervals (configurable)
- Efficient database queries with targeted indexes
- Email sending runs asynchronously
- Notification failures don't impact import process

### Logging

Comprehensive import notification logging that integrates seamlessly with existing import logs:

```
21:01:29.058 [ForkJoinPool.commonPool-worker-18] INFO  c.k.t.t.t.c.CodeSystemImportService  - IMPORT FINISHED (0 sec)
21:01:29.069 [ForkJoinPool.commonPool-worker-18] INFO  c.k.t.t.f.c.CodeSystemFileImportController  - Code system file import took 0 seconds
21:01:29.070 [ForkJoinPool.commonPool-worker-18] INFO  c.k.t.c.s.j.l.ImportNotificationService - Sending import notification for CS-FILE-IMPORT (completed) to 2 recipient(s)
21:01:29.234 [ForkJoinPool.commonPool-worker-18] INFO  c.k.t.c.s.j.l.ImportNotificationService - Import notification sent (0 sec)
```

**Log Details**:
- Job type and status clearly indicated
- Number of recipients displayed
- Timing information matches existing TermX conventions
- Seamlessly integrated with import workflow logs

### Testing
- All modules compile successfully
- Integration tests pass
- No linter errors
- Full build verified (84 tasks)

---

## Migration Notes

### For Existing Deployments
1. **Database**: Run Liquibase migrations to create `sys.snomed_import_tracking` table
2. **Configuration**: Add `SMTP_TO_IMPORT` to environment variables if email notifications are desired
3. **Code**: No code changes required - all changes are backward compatible
4. **Docker**: Update `server.env` with new SMTP_TO_IMPORT parameter (optional)

### Bug Fixes
- **Format String Escaping**: Added proper escaping of `%` characters in user-provided data (job types, sources, etc.) to prevent `UnknownFormatConversionException` when generating HTML emails. Both `ImportNotificationService` and `SnomedImportPollingService` now include `escapeFormatString()` helper method to safely handle format strings.

### Breaking Changes
**None** - All changes are backward compatible. If SMTP or recipients are not configured, the system continues to function normally without sending notifications.

---

## Contributors
- Implementation completed on March 2, 2026
- Email notification system designed for production use
- Package refactoring for improved code organization

---

## Related Files

### New Files Created (6)
1. `termx-core/src/main/java/com/kodality/termx/core/sys/job/logger/ImportNotificationService.java`
2. `termx-api/src/main/java/com/kodality/termx/snomed/rf2/SnomedImportTracking.java`
3. `snomed/src/main/java/com/kodality/termx/snomed/integration/SnomedImportTrackingRepository.java`
4. `snomed/src/main/java/com/kodality/termx/snomed/integration/SnomedImportPollingService.java`
5. `termx-core/src/main/resources/sys/changelog/sys/70-snomed_import_tracking.sql`
6. `termx-integtest/src/test/groovy/com/kodality/termx/core/ImportNotificationTest.groovy`

### Modified Files (6)
1. `termx-app/src/main/resources/application.yml` - Added import.recipients config
2. `deployment/docker-compose/server.env` - Added SMTP_TO_IMPORT parameter
3. `termx-core/src/main/java/com/kodality/termx/core/sys/email/EmailService.java` - Multi-recipient support
4. `termx-core/src/main/java/com/kodality/termx/core/sys/job/JobLogService.java` - Notification integration
5. `snomed/src/main/java/com/kodality/termx/snomed/integration/SnomedService.java` - Tracking record creation
6. `termx-core/src/main/resources/sys/changelog/sys/changelog.xml` - Added new migration

### Refactored Files (11)
All files in `snomed.snomed` package moved to `snomed.integration` package.
