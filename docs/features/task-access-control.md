# Task Access Control

## Description

Task access control provides privilege-based visibility and filtering of tasks in TermX. Different user roles see different subsets of tasks based on their privileges and resource access levels. This ensures users only see tasks relevant to their responsibilities.

**Role-based visibility:**

- **Admin** - Sees all tasks across all resources
- **Publisher** - Sees tasks they created/are assigned to, PLUS all tasks for resources they have publish access to
- **Editor** - Sees only tasks they created or are assigned to
- **Viewer** - Cannot see tasks in the task list (no task access)

**Key capabilities:**

- Automatic virtual privilege derivation at login (resource edit/publish privileges grant Task privileges)
- Privilege-based task filtering at the database level with OR logic (own tasks OR publisher access)
- Resource-level access control using context items
- Unseen changes tracking per user
- Automatic filtering in all API endpoints (query, load, widget)
- Support for complex privilege patterns (wildcards, resource-specific)

## Configuration

Task access control is automatically enabled and requires no configuration. It is built into the TaskForge module and uses the TermX privilege system.

### Privilege System

Task access is controlled through **virtual privilege derivation**. When a user logs in, their resource privileges are automatically translated into Task privileges:

**Privilege derivation at login:**
- Any `*.*.edit` privilege (e.g., `icd-10.CodeSystem.edit`) → automatically derives `code-system#icd-10.Task.view` and `code-system#icd-10.Task.edit`
- Any `*.*.publish` privilege (e.g., `*.ValueSet.publish`) → automatically derives `value-set#*.Task.view`, `value-set#*.Task.edit`, and `value-set#*.Task.publish`
- View-only privileges (e.g., `*.CodeSystem.view`) → no Task privileges, task list is hidden

**Task visibility rules:**

| User Privilege | Task List Accessible? | Derived Task Privileges | Tasks Visible |
|----------------|----------------------|------------------------|---------------|
| `*.CodeSystem.view` only | ❌ No | None | None (no Task privileges derived) |
| `icd-10.CodeSystem.edit` | ✅ Yes | `code-system#icd-10.Task.view`, `code-system#icd-10.Task.edit` | Tasks created by OR assigned to user |
| `icd-10.CodeSystem.publish` | ✅ Yes | `code-system#icd-10.Task.view`, `code-system#icd-10.Task.edit`, `code-system#icd-10.Task.publish` | Tasks created by OR assigned to user, PLUS all tasks with context `code-system\|icd-10` |
| `*.CodeSystem.publish` | ✅ Yes | `code-system#*.Task.view`, `code-system#*.Task.edit`, `code-system#*.Task.publish` | Tasks created by OR assigned to user, PLUS all CodeSystem tasks |
| `*.*.*` (admin) | ✅ Yes | (no derivation) | All tasks (no filter) |

### Default Role Configuration

Default roles in TermX:

- **admin**: `*.*.*` (all privileges)
- **publisher**: `*.*.publish` (publish on all resources)
- **editor**: `*.*.edit` (edit on all resources)
- **viewer**: `*.*.view` (view-only on all resources)

See [Mock Authentication](mock-auth.md) for testing with mock user profiles.

### Resource-Level Access

Tasks are linked to resources via context items (e.g., CodeSystem, ValueSet, MapSet). Access control considers:

1. **User's privileges** on the resource type
2. **Specific resource ID** from task context
3. **Task ownership** (created by or assigned to user)

Example: A user with privilege `icd-10.CodeSystem.edit` can see tasks with context `{type: "code-system", id: "icd-10"}` that they created or are assigned to.

## Use-Cases

### Scenario 1: Editor Creating and Viewing Own Task

**Context:** Junior editor needs to create a review task for ICD-10 concepts they're working on.

**Steps:**
1. Editor logs in with `icd-10.CodeSystem.edit` privilege
2. System automatically derives `code-system#icd-10.Task.view` and `code-system#icd-10.Task.edit` privileges at login
3. Task list becomes visible (gate check passes with Task.view privilege)
4. Creates task: "Review new diabetes concepts" with context `{type: "code-system", id: "icd-10"}`
5. Task is assigned to themselves
6. Query task list - sees the newly created task (creator match)
7. Another editor with different resource privileges queries task list - does NOT see this task (not their task, no publish privilege on icd-10)

**Outcome:** Editor can manage their own tasks without seeing unrelated tasks from other editors.

### Scenario 2: Publisher Monitoring All Resource Tasks

**Context:** Senior publisher needs to see all review tasks for ICD-10 and ICD-11 code systems.

**Steps:**
1. Publisher logs in with `icd-10.CodeSystem.publish` and `icd-11.CodeSystem.publish` privileges
2. System automatically derives:
   - `code-system#icd-10.Task.view`, `code-system#icd-10.Task.edit`, `code-system#icd-10.Task.publish`
   - `code-system#icd-11.Task.view`, `code-system#icd-11.Task.edit`, `code-system#icd-11.Task.publish`
3. Query task list - sees:
   - ALL tasks with context `code-system|icd-10` (from all users)
   - ALL tasks with context `code-system|icd-11` (from all users)
   - Plus any tasks they created or are assigned to (regardless of context)
4. Can view task details even if created by other users
5. Can reassign tasks and monitor progress

**Outcome:** Publisher has oversight of all tasks for resources they manage, enabling effective coordination.

### Scenario 3: Admin Full Visibility

**Context:** System administrator needs to troubleshoot task-related issues and view all tasks.

**Steps:**
1. Admin logs in with `*.*.*` privilege
2. Query task list - sees ALL tasks across all resources
3. Can view and edit any task regardless of assignee or creator
4. Can diagnose issues and reassign stuck tasks

**Outcome:** Admin has complete system visibility for support and troubleshooting.

### Scenario 4: Viewer Restricted Access

**Context:** Read-only user (reports/auditing role) should not see task management interface.

**Steps:**
1. Viewer logs in with only `*.*.view` privileges (no edit or publish on any resource)
2. System does NOT derive any Task privileges (no resource-specific Task privileges)
3. Query task list - receives 403 Forbidden (gate check fails - no Task.view privilege)
4. Task menu items hidden in UI (no Task privileges)

**Outcome:** Viewer cannot access task system, maintaining separation of concerns. The resource-specific privilege derivation ensures task access is automatically tied to edit/publish privileges on actual resources.

### Scenario 5: Resource-Specific Task Filtering

**Context:** Mixed publisher/editor privileges on different resources.

**Steps:**
1. User has privileges: `icd-10.CodeSystem.publish`, `disorders.ValueSet.edit`
2. System derives:
   - `code-system#icd-10.Task.view`, `code-system#icd-10.Task.edit`, `code-system#icd-10.Task.publish`
   - `value-set#disorders.Task.view`, `value-set#disorders.Task.edit`
3. Query task list - sees tasks where:
   - **(Rule A)** Created by user OR assigned to user (any resource)
   - **OR (Rule B)** Context matches `code-system#icd-10` (publisher access, sees all ICD-10 CodeSystem tasks)
4. Specifically:
   - ✅ Own tasks for disorders ValueSet (creator/assignee match)
   - ✅ All ICD-10 CodeSystem tasks (publisher context match: `code-system#icd-10.Task.publish`)
   - ❌ Other users' disorders tasks (no publish access on disorders, not own task)
   - ❌ Other resources' tasks (no access, not own task)

**Outcome:** Fine-grained, resource-specific access control with OR logic - users see their own work across all resources PLUS supervisory view of specific resources they publish.

## API

Task access control is transparent - all task endpoints automatically apply privilege-based filtering.

### Filtered Endpoints

All endpoints under `/api/tm` apply access control:

| Method | Path | Filtering Behavior |
|--------|------|-------------------|
| GET | `/tasks{?params*}` | Returns only tasks user can access based on privileges |
| GET | `/tasks/{number}` | Returns 403 if user cannot access the task |
| POST | `/tasks` | Validates user has edit access to resources in task context |
| PUT/PATCH | `/tasks/{number}` | Validates user can edit the task |

### Query Parameters

The `visibilityFilter` is automatically applied for non-admin users - it cannot be overridden via query parameters.

### Response Behavior

- **Admin**: All tasks (no filter)
- **Publisher/Editor**: Tasks matching visibility filter (own tasks OR publisher context match)
- **Viewer**: 403 Forbidden (no Task.view privilege)

## Testing

### Test privilege-based filtering

```bash
# Start with mock authentication enabled
MICRONAUT_ENVIRONMENTS=local ./gradlew :termx-app:run

# Query as admin (sees all tasks)
curl http://localhost:8200/api/tm/tasks
# Expected: All tasks in the system

# Query as publisher (sees all tasks for accessible resources)
curl -H "Authorization: Bearer publisher" http://localhost:8200/api/tm/tasks
# Expected: All tasks for resources with publish access

# Query as editor (sees only own/assigned tasks)
curl -H "Authorization: Bearer editor" http://localhost:8200/api/tm/tasks
# Expected: Only tasks created by or assigned to editor1

# Query as viewer (no task access)
curl -H "Authorization: Bearer viewer" http://localhost:8200/api/tm/tasks
# Expected: Empty result []
```

### Test resource-level access

```bash
# Create task as editor with icd-10 context
curl -X POST -H "Authorization: Bearer editor" \
     -H "Content-Type: application/json" \
     -d '{
       "title": "Review ICD-10 Mapping",
       "type": "concept-review",
       "workflow": "concept-review",
       "project": "termx",
       "priority": "routine",
       "context": [{"type": "code-system", "id": "icd-10"}]
     }' \
     http://localhost:8200/api/tm/tasks

# Same editor should see their own task
curl -H "Authorization: Bearer editor" http://localhost:8200/api/tm/tasks
# Expected: Task appears in results

# Different editor (no access to icd-10) should NOT see it
# (if they don't have icd-10.CodeSystem.edit privilege)

# Publisher with icd-10 publish access should see it
curl -H "Authorization: Bearer publisher" http://localhost:8200/api/tm/tasks
# Expected: Task appears (publisher has publish access)
```

### Test unseen changes tracking

```bash
# 1. Get task list and identify a task
curl http://localhost:8200/api/tm/tasks

# 2. Mark task as opened
curl -X POST http://localhost:8200/api/tm/tasks/TASK-123/opened

# 3. Update the task (triggers unseen change)
curl -X PATCH -H "Content-Type: application/json" \
     -d '{"title": "Updated Title"}' \
     http://localhost:8200/api/tm/tasks/TASK-123

# 4. Query with unseenChanges filter
curl "http://localhost:8200/api/tm/tasks?unseenChanges=true"
# Expected: Task appears in results
```

## Data Model

### TaskQueryParams

Query parameters used for filtering tasks at the API level.

| Field | Type | Description |
|-------|------|-------------|
| visibilityFilter | TaskVisibilityFilter | Combined filter (username + publisher contexts) |
| unseenChanges | Boolean | Filter for tasks updated since last opened |
| text | String | Full-text search |
| project | String | Project code filter |
| status | String | Task status filter |
| assignee | String | Assigned user filter |

**TaskVisibilityFilter (nested class):**

| Field | Type | Description |
|-------|------|-------------|
| username | String | User for creator/assignee matching |
| publisherContexts | List<String> | Context resources user has publish access to (null = all, empty = none) |

**Applied automatically by controller:**

- `visibilityFilter` - set for non-admin users with username and publisher contexts based on session privileges

### SessionInfo

Session information used for access control decisions.

| Field | Type | Description |
|-------|------|-------------|
| username | String | Current user's username |
| privileges | List<String> | User's privilege strings |
| authenticated | boolean | Whether user is authenticated |

**Key methods:**

- `hasPrivilege(String privilege)` - Check if user has specific privilege
- `hasAnyPrivilege(List<String> privileges)` - Check if user has any of the privileges
- `getPermittedResourceIds(String action)` - Get resource IDs user can perform action on

### Privilege Pattern

Privilege strings follow: `<resourceId>.<ResourceType>.<action>`

| Component | Examples | Wildcard |
|-----------|----------|----------|
| resourceId | `icd-10`, `disorders`, `snomed-ct` | `*` = all resources |
| ResourceType | `CodeSystem`, `ValueSet`, `MapSet`, `Task` | `*` = all types |
| action | `view`, `edit`, `publish` | `*` = all actions |

**Resource-specific Task privilege derivation:**

Resource privileges are automatically translated to resource-specific Task privileges at login using hash format:

| Resource Privilege | Derived Task Privileges | Effect |
|--------------------|------------------------|--------|
| `icd-10.CodeSystem.edit` | `code-system#icd-10.Task.view`, `code-system#icd-10.Task.edit` | Can access task list, see own ICD-10 tasks |
| `icd-10.CodeSystem.publish` | `code-system#icd-10.Task.view`, `code-system#icd-10.Task.edit`, `code-system#icd-10.Task.publish` | Can access task list, see own tasks + all ICD-10 CodeSystem tasks |
| `*.CodeSystem.edit` | `code-system#*.Task.view`, `code-system#*.Task.edit` | Can access task list, see own tasks for any CodeSystem |
| `*.CodeSystem.publish` | `code-system#*.Task.view`, `code-system#*.Task.edit`, `code-system#*.Task.publish` | Can access task list, see own tasks + all CodeSystem tasks |
| `*.*.view` only | None | Cannot access task list |
| `*.*.*` (admin) | (no derivation) | Sees all tasks |

**Privilege format:** `contextType#resourceId.Task.action`

Examples:
- `code-system#icd-10.Task.edit` - ICD-10 CodeSystem task editing
- `value-set#disorders.Task.publish` - Disorders ValueSet task publishing
- `map-set#icd10-to-snomed.Task.view` - MapSet task viewing
- `code-system#*.Task.edit` - All CodeSystem task editing (wildcard)

### TaskSearchParams (Backend)

Internal search parameters passed to TaskForge repository.

| Field | Type | Description |
|-------|------|-------------|
| visibilityFilter | TaskVisibilityFilter | Combined OR filter for task visibility |
| unseenChanges | Boolean | JOIN with task_read_log, filter where last_opened < updated_at |

**TaskVisibilityFilter:**
- `username` - Matches `created_by = ? OR assignee = ?`
- `publisherContexts` - Matches tasks whose context is in the list (null = all, empty = none)

**SQL logic:** `(created_by = username OR assignee = username) OR (context IN publisherContexts)`

These parameters are mapped from `TaskQueryParams` by the controller after applying privilege checks.

## Architecture

```mermaid
flowchart TD
    Login[User Login] --> SessionProvider[SessionProvider]
    SessionProvider --> DerivePrivs[deriveTaskPrivileges]
    DerivePrivs -->|*.*.edit or *.*.publish| AddTaskPrivs[Add *.Task.view/edit/publish]
    AddTaskPrivs --> SessionInfo[SessionInfo with derived privileges]
    
    Request[API Request] --> Auth[@Authorized Task.view]
    Auth -->|No Task.view| Forbidden[403 Forbidden]
    Auth -->|Has Task.view| Controller[TaskController]
    
    Controller --> CheckAdmin{Is Admin?}
    CheckAdmin -->|Yes| AllTasks[Query All Tasks]
    CheckAdmin -->|No| BuildFilter[Build TaskVisibilityFilter]
    
    BuildFilter --> SetUsername[username = session.username]
    BuildFilter --> GetPublisher[publisherContexts = getPermittedContexts publish]
    
    SetUsername --> ApplyFilter[Apply Visibility Filter]
    GetPublisher --> ApplyFilter
    
    ApplyFilter --> Repository[TaskRepository]
    AllTasks --> Repository
    
    Repository --> SQL[WHERE own tasks OR publisher context]
    SQL --> Database[(taskforge.task)]
    Database --> Results[Filtered Results]
```

**Privilege resolution flow:**

1. **Login**: SessionFilter calls `deriveTaskPrivileges()` - resource privileges derive resource-specific Task privileges
2. **Request arrives**: `@Authorized(privilege = Privilege.T_VIEW)` checks for any Task.view privilege (gate check)
3. **Controller logic**:
   - If admin (`*.*.*`): no filter
   - Otherwise: build `TaskVisibilityFilter` with username + publisher contexts extracted from hash-format privileges
4. **Repository SQL**: Apply OR filter at database level

**Database filtering:**

Filtering happens at the SQL level for performance with OR logic:

```sql
-- Visibility filter query with resource-specific privileges
SELECT * FROM taskforge.task t
WHERE (
  -- Rule A: Own tasks
  (t.created_by = ? OR t.assignee = ?)
  -- Rule B: Publisher context match (from code-system#icd-10.Task.publish privileges)
  OR EXISTS (
    SELECT 1 FROM jsonb_array_elements(t.context) ctx
    WHERE (ctx->>'type', ctx->>'id') IN (
      ('code-system', 'icd-10'),
      ('value-set', 'disorders'),
      ...
    )
  )
)
```

## Technical Implementation

### Key Components

**SessionFilter privilege derivation:**

```java
private void deriveTaskPrivileges(SessionInfo session) {
    if (session.getPrivileges() == null) return;
    Set<String> derived = new HashSet<>(session.getPrivileges());
    
    // Keep admin unchanged
    if (session.hasPrivilege("*.*.*")) {
        session.setPrivileges(derived);
        return;
    }
    
    // Map resource types to context types
    Map<String, String> resourceTypeToContextType = Map.of(
        "CodeSystem", "code-system",
        "ValueSet", "value-set",
        "MapSet", "map-set",
        "ConceptMap", "map-set"
    );
    
    // Derive Task privileges for each resource privilege
    for (String privilege : session.getPrivileges()) {
        String[] parts = splitPrivilege(privilege); // [resourceId, resourceType, action]
        if (parts == null || parts.length != 3) continue;
        
        String resourceId = parts[0];
        String resourceType = parts[1];
        String action = parts[2];
        
        String contextType = resourceTypeToContextType.get(resourceType);
        if (contextType == null) continue;
        
        if ("edit".equals(action)) {
            derived.add(contextType + "#" + resourceId + ".Task.view");
            derived.add(contextType + "#" + resourceId + ".Task.edit");
        } else if ("publish".equals(action)) {
            derived.add(contextType + "#" + resourceId + ".Task.view");
            derived.add(contextType + "#" + resourceId + ".Task.edit");
            derived.add(contextType + "#" + resourceId + ".Task.publish");
        }
    }
    
    session.setPrivileges(derived);
}
```

**TaskController filtering logic:**

```java
@Authorized(privilege = Privilege.T_VIEW)  // Gate check
@Get(uri = "/tasks{?params*}")
public QueryResult<Task> queryTasks(TaskQueryParams params) {
    SessionInfo session = SessionStore.require();
    
    if (!isAdmin(session)) {
        // Build visibility filter with OR semantics
        TaskVisibilityFilter filter = new TaskVisibilityFilter();
        filter.setUsername(session.getUsername());
        filter.setPublisherContexts(getPermittedContexts(session, "publish"));
        params.setVisibilityFilter(filter);
    }
    return taskService.queryTasks(params);
}

private List<String> getPermittedContexts(SessionInfo session, String action) {
    List<String> result = new ArrayList<>();
    for (String contextType : List.of("code-system", "value-set", "map-set")) {
        List<String> ids = getPermittedResourceIds(session, contextType, action);
        if (ids == null) continue; // wildcard
        for (String id : ids) {
            result.add(contextType + "|" + id);
        }
    }
    return result.isEmpty() ? null : result;
}

private List<String> getPermittedResourceIds(SessionInfo session, String contextType, String action) {
    // Check for wildcard: code-system#*.Task.publish
    if (session.hasPrivilege(contextType + "#*.Task." + action)) {
        return null; // all resources
    }
    // Extract IDs from privileges like code-system#icd-10.Task.publish
    String prefix = contextType + "#";
    String suffix = ".Task." + action;
    return session.getPrivileges().stream()
        .filter(p -> p.startsWith(prefix) && p.endsWith(suffix))
        .map(p -> p.substring(prefix.length(), p.length() - suffix.length()))
        .toList();
}
```

### Database Schema

**task_read_log table:**

```sql
CREATE TABLE taskforge.task_read_log (
  id                    bigint PRIMARY KEY,
  task_id               bigint NOT NULL REFERENCES taskforge.task(id),
  user_id               text NOT NULL,
  last_opened_time      timestamptz NOT NULL,
  CONSTRAINT task_read_log_ukey UNIQUE (task_id, user_id)
);
```

Used for tracking when users last opened tasks to show unseen changes indicator.

### Context Types and Resource Access

Task context items link tasks to resources:

| Context Type | Resource | Privilege Pattern |
|--------------|----------|-------------------|
| `code-system` | CodeSystem | `<resourceId>.CodeSystem.edit/publish` |
| `value-set` | ValueSet | `<resourceId>.ValueSet.edit/publish` |
| `map-set` | MapSet | `<resourceId>.MapSet.edit/publish` |
| `wiki` | Wiki space | `<resourceId>.Wiki.edit/publish` |

The system extracts resource IDs from task context and checks user privileges against those specific resources.

### Migration from Taskflow

TaskForge is an inlined version of the external `taskflow-service` library. Key changes:

- Package: `com.kodality.taskflow` → `org.termx.taskforge`
- Schema: `taskflow` → `taskforge`
- Module: `task-taskflow` → `task-taskforge`
- Added privilege-based filtering (not present in original taskflow)

Migration script `90-migrate-from-taskflow.sql` handles both fresh installations and migrations from existing taskflow schema.
