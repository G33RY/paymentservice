# Payment Service

A robust, production-ready payment transfer service built with Spring Boot that handles multi-currency transfers with
built-in resilience, concurrency control, and reliability features.

## Table of Contents

- [Overview](#overview)
- [Architecture & Design Decisions](#architecture--design-decisions)
- [Edge Case Handling](#edge-case-handling)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Testing](#testing)

## Overview

This payment service provides a RESTful API for managing accounts and executing money transfers between accounts with
support for multiple currencies. The system is designed with enterprise-grade patterns to ensure data consistency,
prevent duplicate transactions, and handle failures gracefully.

### Key Features

- Multi-currency account management (USD, EUR, GBP, HUF)
- Secure money transfers with exchange rate conversion
- Idempotency support to prevent duplicate transactions
- Transactional outbox pattern for reliable event publishing
- Pessimistic locking for concurrency control
- Comprehensive error handling and validation
- In-memory H2 database for easy development and testing

## Architecture & Design Decisions

### 1. **Layered Architecture**

The application follows a clean layered architecture with clear separation of concerns:

```
Controller Layer → Service Layer → Repository Layer
                ↓
         Idempotency Layer
```

- **Controllers**: Handle HTTP requests/responses and validation
- **Services**: Contain business logic and orchestrate operations
- **Repositories**: Manage data persistence using Spring Data JDBC
- **Event System**: Asynchronous event publishing via transactional outbox
- **Idempotency Layer**: Request deduplication and caching

### 2. **Spring Data JDBC over JPA**

**Decision**: Use Spring Data JDBC instead of JPA/Hibernate.

**Rationale**:

- Simpler and more predictable database interactions
- Better control over SQL queries
- Less overhead and complexity
- Explicit transaction boundaries
- No lazy loading or proxy complications
- Easier to reason about in high-concurrency scenarios

### 3. **Transactional Outbox Pattern**

**Implementation**: `EventService` + `OutboxScheduler`

The service uses the transactional outbox pattern to ensure reliable event publishing:

```java

@Transactional
public void createTransfer(CreateTransferDto dto) {
    // 1. Perform transfer (update accounts)
    // 2. Save transfer record
    // 3. Write event to outbox table (same transaction)
}
```

A scheduled task polls the outbox table every 2 seconds and publishes pending events:

```java

@Scheduled(fixedRate = 2000)
public void publishPendingMessages() {
    // 1. SELECT top 100 pending messages FOR UPDATE
    // 2. Publish to external system
    // 3. Mark as PUBLISHED or FAILED
}
```

**Benefits**:

- Guarantees events are published if transaction commits
- No lost events due to system failures
- Decouples business logic from message broker availability
- Supports at-least-once delivery semantics

**Production Considerations**:

- Currently logs events (mock implementation)
- Would integrate with RabbitMQ, Kafka, or AWS SQS in production
- Requires leader election or distributed scheduling for multiple instances

### 4. **Idempotency Implementation**

**Implementation**: Custom annotation + Interceptor + Response caching

The system prevents duplicate requests using the `X-Idempotency-Key` header:

```java

@PostMapping
@IdempotentEndpoint(expireSeconds = 300)
public TransferResponse createTransfer(@RequestBody CreateTransferDto dto) {
    // Implementation
}
```

**Flow**:

1. **Pre-request** (`IdempotencyInterceptor`):
    - Check if idempotency key exists
    - If PROCESSING → return 409 Conflict
    - If COMPLETED → return cached response
    - If new → insert with PROCESSING status (separate transaction)

2. **Request Processing**:
    - Business logic executes normally

3. **Post-response** (`IdempotentResponseAdvice`):
    - Capture response body and status code
    - Update record to COMPLETED with cached response
    - Uses `@Transactional(propagation = REQUIRES_NEW)` for separate transaction

4. **Failure Handling**:
    - If exception or 4xx/5xx status → delete idempotency record
    - Allows client to retry failed requests

**Key Design Decisions**:

- **Separate Transactions**: Idempotency state managed in independent transactions to avoid rollback cascading
- **Expiration**: Records expire after configurable time (default 300s) to prevent unbounded growth
- **Early Return**: Returns cached responses immediately without re-executing business logic
- **REQUIRES_NEW Propagation**: Ensures idempotency state persists even if business transaction fails

### 5. **Concurrency Control**

**Strategy**: Pessimistic locking with `SELECT FOR UPDATE`

```java

@Query("""
            SELECT * FROM account
            WHERE id = :id
            FOR UPDATE
        """)
Optional<Account> findByIdAndLock(Long id);
```

**Why Pessimistic Locking**:

- Prevents race conditions in concurrent transfers
- Guarantees balance consistency
- Simpler than optimistic locking for this use case
- Database handles lock management and deadlock detection

**Deadlock Prevention**: Accounts are always locked in order by id to prevent circular waits.

### 6. **Exchange Rate Service**

**Implementation**: Mock service with simulated API calls

```java
public BigDecimal getExchangeRate(Currency from, Currency to) {
    // Simulates 1-second API call
    // 10% failure rate for resilience testing
    // Returns calculated rate based on base currency (EUR)
}
```

**Production Considerations**:

- Would integrate with external service (e.g., fixer.io, exchangerate-api.io)
- Should implement caching to reduce API calls
- Implement circuit breaker to handle service outages
- Store historical rates for audit purposes

### 7. **Data Model**

**Decimal Precision**: `DECIMAL(19,4)` chosen for financial amounts

- 19 total digits: Supports values up to 999,999,999,999,999.9999
- 4 decimal places: Standard for currency precision (supports fractional cents)
- Prevents floating-point rounding errors
- Compliant with financial industry standards

**Schema Design**:

```sql
account: Stores account balances and currency
transfer: Records all transfer transactions with exchange rate
idempotent_item: Tracks idempotency keys and cached responses
outbox_message: Transactional outbox for event publishing
```

## Edge Case Handling

### Resilience

#### 1. **External Service Failures**

- **Exchange Rate API failures** are handled gracefully:
  ```java
  @ExceptionHandler({ExchangeRateApiException.class})
  public ResponseEntity<ApiError> handleExchangeRateApiException() {
      return ResponseEntity.status(424).body(new ApiError("..."));
  }
  ```
- Returns HTTP 424 (Failed Dependency) to indicate external service issue
- Client can retry with exponential backoff

#### 2. **Database Connection Issues**

- Spring Boot auto-configuration handles connection pooling
- Transaction manager automatically rolls back on failures
- Idempotency ensures failed requests can be safely retried

#### 3. **Outbox Publishing Failures**

- Events marked as FAILED in outbox table
- Scheduler retries failed events on next run
- Manual intervention possible via database access

### Concurrency

#### 1. **Race Conditions in Transfers**

- **Pessimistic locking** prevents double-spending:
  ```java
  Account fromAccount = accountService.getAccount(id, true); // Locks row
  ```
- Database enforces serialization of concurrent updates
- One transaction succeeds, others wait or timeout

#### 2. **Deadlock Prevention**

- Always acquire locks in consistent order (by account ID)
- Database detects and breaks remaining deadlocks
- Clients receive 409 Conflict and can retry

#### 3. **Concurrent Idempotent Requests**

- Database unique constraint on idempotency key prevents duplicates
- First request inserts PROCESSING status
- Subsequent requests receive 409 Conflict immediately
- After completion, return cached response instantly

### Reliability

#### 1. **Duplicate Request Prevention**

- Idempotency keys prevent accidental duplicate transfers
- Critical for retry scenarios and network issues
- Cached responses ensure consistent results

#### 2. **Data Consistency**

- **ACID Transactions**: All transfer operations atomic
- Account updates and event creation in same transaction
- Either everything commits or everything rolls back

#### 3. **Event Delivery Guarantees**

- Transactional outbox ensures no lost events
- At-least-once delivery (events may be published multiple times)
- Consumers should be idempotent

#### 4. **Insufficient Balance**

- Validated before account updates:
  ```java
  if(fromAccount.getBalance().compareTo(amount) < 0) {
      throw new NotEnoughBalanceException();
  }
  ```
- Returns HTTP 400 Bad Request
- No partial transfers possible

#### 5. **Account Not Found**

- Validated early with clear error messages
- Returns HTTP 400 with account ID
- Prevents invalid references

#### 6. **Data Integrity Violations**

- Foreign key constraints prevent orphaned records
- Unique constraints on idempotency keys
- Global exception handler provides user-friendly errors

## Technology Stack

### Core Framework

- **Spring Boot 3.5.11** - Application framework
- **Java 21** - Programming language
- **Maven** - Build tool

### Spring Modules

- **Spring Data JDBC** - Data access layer
- **Spring Web** - REST API
- **Spring Actuator** - Health checks and metrics
- **Spring Validation** - Request validation

### Database

- **H2 Database** - In-memory database (development)
    - Console accessible at `http://localhost:8080/h2-console`
    - JDBC URL: `jdbc:h2:mem:paymentdb`
    - Username: `sa`, Password: _(empty)_

### Monitoring

- **Micrometer + Prometheus** - Metrics collection

### Development Tools

- **Lombok** - Reduces boilerplate code
- **JUnit 5** - Testing framework
- **Mockito** - Mocking framework
- **Hamcrest** - Assertion matchers

## Project Structure

```
src/
├── main/
│   ├── java/.../paymentservice/
│   │   ├── PaymentserviceApplication.java
│   │   ├── account/                    # Account management
│   │   │   ├── controller/            # REST endpoints
│   │   │   ├── dto/                   # Data transfer objects
│   │   │   ├── entity/                # Domain entities
│   │   │   ├── exception/             # Custom exceptions
│   │   │   ├── mapper/                # DTO ↔ Entity mapping
│   │   │   ├── repository/            # Data access
│   │   │   └── service/               # Business logic
│   │   ├── transfer/                   # Transfer management
│   │   │   └── [same structure]
│   │   ├── idempotency/               # Idempotency system
│   │   │   ├── annotation/            # @IdempotentEndpoint
│   │   │   ├── entity/                # IdempotentItem
│   │   │   ├── exception/             # IdempotentItemExists
│   │   │   ├── interceptor/           # Request/response handling
│   │   │   ├── repository/            # Persistence
│   │   │   └── service/               # Core logic
│   │   ├── eventsystem/               # Transactional outbox
│   │   │   ├── dto/                   # Event definitions
│   │   │   ├── entity/                # OutboxMessage
│   │   │   ├── repository/            # Outbox persistence
│   │   │   └── service/               # Publishing logic
│   │   └── common/                    # Shared components
│   │       ├── config/                # Configuration
│   │       └── exception/             # Global handlers
│   └── resources/
│       ├── application.yml            # Configuration
│       ├── schema.sql                 # Database schema
│       └── data.sql                   # Test data
└── test/
    └── java/.../paymentservice/       # Unit tests
        ├── account/
        ├── transfer/
        ├── idempotency/
        └── eventsystem/
```

## Getting Started

### Prerequisites

- **Java 21** or higher
- **Maven 3.6+** (or use included wrapper)

### Build the Application

```bash
# Using Maven wrapper (recommended)
./mvnw clean install

# Or with local Maven installation
mvn clean install
```

### Run the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or with local Maven installation
mvn spring-boot:run

# Or run the JAR directly
java -jar target/paymentservice-0.0.1-SNAPSHOT.jar
```

The application starts on **http://localhost:8080**

### Verify It's Running

```bash
# Health check
curl http://localhost:8080/actuator/health

# List all accounts
curl http://localhost:8080/api/accounts
```

### Access H2 Console

1. Navigate to http://localhost:8080/h2-console
2. Enter connection details:
    - **JDBC URL**: `jdbc:h2:mem:paymentdb`
    - **Username**: `sa`
    - **Password**: _(leave empty)_
3. Click "Connect"

## API Documentation

### Account Endpoints

#### Create Account

```bash
POST /api/accounts
Content-Type: application/json
X-Idempotency-Key: unique-request-id-12345
{
  "currency": "USD",
  "initialBalance": 1000.00
}
```

**Response** (201 Created):

```json
{
  "id": 1,
  "balance": 1000.00,
  "initialBalance": 1000.00,
  "currency": "USD"
}
```

#### Get All Accounts

```bash
GET /api/accounts
```

**Response** (200 OK):

```json
[
  {
    "id": 1,
    "balance": 1000.00,
    "initialBalance": 1000.00,
    "currency": "USD"
  }
]
```

### Transfer Endpoints

#### Create Transfer (Idempotent)

```bash
POST /api/transfers
Content-Type: application/json
X-Idempotency-Key: unique-request-id-12345

{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100.00
}
```

**Response** (201 Created):

```json
{
  "id": 1,
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100.00,
  "rate": 1.0000
}
```

**Idempotency Behavior**:

- **First request**: Processes transfer, returns 201 Created
- **Duplicate request (processing)**: Returns 409 Conflict
- **Duplicate request (completed)**: Returns cached 201 response
- **Failed request retry**: Processes as new request

### Error Responses

#### Bad Request (400)

```json
{
  "message": "Account not found: 999"
}
```

#### Conflict (409)

```json
{
  "error": "Duplicate idempotency key"
}
```

#### Failed Dependency (424)

```json
{
  "message": "Failed to get exchange rate"
}
```

### Supported Currencies

- **USD** - US Dollar (rate: 1.1 to EUR)
- **EUR** - Euro (base currency, rate: 1.0)
- **GBP** - British Pound (rate: 0.9 to EUR)
- **HUF** - Hungarian Forint (rate: 350 to EUR)

## Testing

### Run All Tests

```bash
# Using Maven wrapper
./mvnw test

# Or with local Maven installation
mvn test
```

### Test Coverage

The project includes comprehensive unit tests for all critical components:

#### AccountServiceTest

- Account creation with validation
- Account retrieval with and without locking
- Account not found scenarios
- Balance updates and persistence

#### TransferServiceTest

- Successful transfers (same currency and cross-currency)
- Account not found handling
- Insufficient balance validation
- Exchange rate API failures
- Event publishing verification
- Concurrent transfer scenarios

#### IdempotencyServiceTest

- Idempotency record creation
- Duplicate key detection
- Request completion with response caching
- Failure handling and cleanup
- Transaction propagation behavior

#### EventServiceTest

- Event creation in outbox
- Batch publishing (top 100 pending)
- Status transitions (PENDING → PUBLISHED/FAILED)
- Pessimistic locking during publishing
- Empty outbox handling


### Example Test Run Output

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running hu.imregergo.paymentservice.account.AccountServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running hu.imregergo.paymentservice.transfer.TransferServiceTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running hu.imregergo.paymentservice.idempotency.IdempotencyServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running hu.imregergo.paymentservice.eventsystem.EventServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```

### Manual Testing with cURL

#### Create Two Accounts

```bash
# Account 1 (USD)
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: create-account-001" \
  -d '{"currency":"USD","initialBalance":1000}'

# Account 2 (EUR)
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
    -H "X-Idempotency-Key: create-account-002" \
  -d '{"currency":"EUR","initialBalance":500}'
```

#### Execute a Transfer

```bash
curl -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-transfer-001" \
  -d '{
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 100
  }'
```

#### Test Idempotency (Retry Same Request)

```bash
# Same idempotency key - should return cached response
curl -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-transfer-001" \
  -d '{
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 100
  }'
```

#### Verify Balances Updated

```bash
curl http://localhost:8080/api/accounts
```