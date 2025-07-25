# Migration Guide: Moving from Connection ID to Token-Based Architecture

## Introduction
This guide walks you through the process of transitioning from the legacy system that used `connection_id` to the new, secure token-based architecture.

## Why Migrate?
- **Enhanced Security**: Tokens ensure that credentials are never exposed to AI models.
- **Fine-Grained Control**: Tokens provide precise control over permissions and usage limits.
- **Scalability**: Tokens can be easily managed, revoked, and rotated.

## Migration Steps

### Phase 1: Preparation
1. **Familiarize** yourself with the token-based API calls and session token management.
2. **Review** existing usages of `connection_id` in your codebase and API calls.
3. **Data Backup**: Backup any necessary configurations or settings from the old system.

### Phase 2: Implementation
1. **Token Setup**
   - Replace direct `connection_id` usage with session token creation via API.
   - Example to create a session token:
     ```bash
     POST /api/session/tokens
     Content-Type: application/json
     {
       "name": "Migration Session",
       "host": "your-db-host",
       "port": 5432,
       "database": "your-database",
       "username": "your-username",
       "password": "your-password",
       "expirationMinutes": 60,
       "allowedOperations": ["SCHEMA_DISCOVERY", "SELECT_QUERIES"]
     }
     ```

2. **Testing**
   - Use the tokens in your API calls and verify operations.
   - Adjust permissions and limits as needed based on usage requirements.

### Phase 3: Transition
1. **Dual Running**
   - For a short period, run both systems in parallel to ensure all functionality is preserved.
   - Monitor logs and access patterns to ensure stability.

2. **Full Migration**
   - Disable the `connection_id` system completely.
   - Ensure all clients and systems are updated to use the token-based system.

3. **Optimization**
   - Monitor token usage and tweak configurations for optimal performance.

## Final Recommendations
- Enable **audit logging** to keep track of token usage and ensure compliance.
- Regularly **review** and update your token policies for best security practices.

By following this guide, you'll ensure a smooth transition to a more secure and efficient system.
