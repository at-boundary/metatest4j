# Metatest - REST API Mutation Testing Framework

> **Experimental Project (v0.1.0)**
>
> This project is currently in experimental/development phase. Features and APIs may change between versions.

Metatest measures the effectiveness and defect detection capabilities of REST API tests.

It takes the **evaluation posture from mutation testing** and the **specification posture from property-based testing**:
- Drawing from Property Based Testing: invariants are first-class specifications. The mutation space is derived from them, not from code grammar.
- Drawing from Mutation Testing: the goal is evaluating whether tests catch corruptions, not finding bugs in the implementation.

## Table of Contents

- [Overview](#overview)
- [Problem Statement](#problem-statement)
- [How Metatest Differs from PIT and Property-Based Testing](#how-metatest-differs-from-pit-and-property-based-testing)
- [Architecture](#architecture)
- [Technical Implementation](#technical-implementation)
- [Configuration](#configuration)
  - [Directory Structure](#directory-structure)
  - [contract.yml](#contractyml)
  - [Feature Files](#feature-files)
  - [metatest.properties](#metatestproperties)
  - [coverage_config.yml](#coverage_configyml)
- [Invariants DSL Reference](#invariants-dsl-reference)
  - [Supported Operators](#supported-operators)
  - [Field References](#field-references)
  - [Array Fields](#array-fields)
  - [Conditional Invariants](#conditional-invariants-ifthen)
- [Installation](#installation)
- [Reports and Analytics](#reports-and-analytics)
- [Integration with CI/CD](#integration-with-cicd)
- [Performance Considerations](#performance-considerations)
- [Requirements](#requirements)
- [Troubleshooting](#troubleshooting)
- [Building from Source](#building-from-source)

## Overview

Metatest applies mutation testing principles to REST API integration tests. It intercepts HTTP responses during test execution, injects faults into response payloads, and re-executes tests to verify they detect the injected failures. Tests that pass despite injected faults indicate weak assertions or incomplete validation logic.

The framework operates transparently through AspectJ bytecode weaving, requiring no modifications to existing test code.

## Problem Statement

Traditional test coverage metrics measure code execution paths but fail to assess assertion quality. A test with 100% code coverage may still pass when the API returns incorrect data, null values, or missing fields.

Metatest addresses this gap by separating two concerns:

- **Contract faults** — structural mutations (null fields, missing fields) that test whether your assertions check field presence
- **Invariant faults** — business rule violations (negative prices, invalid status values, broken timestamps) that test whether your assertions check semantic correctness

Invariants are the primary artifact. They are both the specification of what your API must always guarantee, and the blueprint for what mutations to generate.

## How Metatest Differs from PIT and Property-Based Testing

Metatest occupies a specific intersection in the testing tool landscape that is worth making explicit.

### PIT / Stryker — mutation testing for source code

PIT mutates **source code syntax**: flip `>` to `>=`, negate a return value, delete a branch, replace a constant. The mutations are grammar-level, not meaning-level. No concept of business semantics, no invariants. Your tests either catch the syntactic corruption or they don't.

PIT answers: *"Do your unit tests catch code-level regressions?"*

### Property-Based Testing (QuickCheck, jqwik, Hypothesis)

You define a **property** — a universally quantified claim like *"for any valid email, `parse(email).toString() == email`"* — and the framework generates random inputs trying to falsify it, then shrinks failing cases to a minimal counterexample. It explores the input space against a stated specification. No mutation of code. No evaluation of test quality.

PBT answers: *"Does your implementation hold for all inputs?"*

### Where Metatest sits

|  | PIT | PBT | Metatest |
|---|---|---|---|
| **Goal** | Evaluate tests | Find implementation bugs | Evaluate tests |
| **Method** | Corrupt the code | Generate adversarial inputs | Generate adversarial responses |
| **Driven by** | Code grammar | Property definitions | Invariant definitions |
| **Output** | Mutation score | Counterexample | Fault detection score per invariant |
| **Specification needed** | No | Yes | Yes |


### Value mutations

Current mutations are structural: null the field, remove the field. Property-guided mutation goes further by generating **boundary-crossing values derived from the invariant's own constraint**:

- `price > 0` → also inject `0`, `-1`, `NaN`
- `status in [ACTIVE, SUSPENDED]` → also inject `"DELETED"`, `"active"` (wrong case), `""`
- `created_at <= updated_at` → also inject a response where `updated_at` precedes `created_at` by one second

The invariant tells you the exact semantic boundary. Generate values that cross it. This is the PBT falsification idea applied to API response data rather than function inputs — and it makes invariants far more powerful than traditional contract testing tools that only verify field presence.

---

## Architecture

### Core Components

```
metatest-rest-java/
├── lib/                          # Core library
│   ├── core/
│   │   ├── interceptor/          # AspectJ interception layer
│   │   │   ├── AspectExecutor    # @Test and HTTP client interception
│   │   │   └── TestContext       # Thread-local execution context
│   │   ├── config/               # Configuration management
│   │   │   ├── LocalConfigurationSource   # YAML-based config
│   │   │   ├── ApiConfigurationSource     # Cloud API config
│   │   │   ├── FeatureConfigScanner       # Feature file loader
│   │   │   └── FeatureConfigCache         # Parsed invariant cache
│   │   └── normalizer/           # Endpoint pattern normalization
│   ├── injection/                # Fault injection strategies
│   │   ├── NullFieldStrategy     # Set fields to null
│   │   ├── MissingFieldStrategy  # Remove fields entirely
│   │   ├── EmptyListStrategy     # Empty arrays/collections
│   │   └── EmptyStringStrategy   # Empty string values
│   ├── invariant/                # Invariant evaluation engine
│   │   ├── InvariantSimulator    # Generates and tests invariant mutations
│   │   └── FieldExtractor        # JSONPath-based field resolution
│   ├── simulation/               # Test execution engine
│   │   ├── Runner                # Fault simulation orchestrator
│   │   └── FaultSimulationReport # Results aggregation and reporting
│   ├── coverage/                 # Endpoint coverage tracking
│   ├── analytics/                # Gap analysis
│   ├── http/                     # HTTP abstraction layer
│   └── api/                      # Cloud API integration
└── gradle-plugin/                # Gradle plugin for zero-config setup
```

## Technical Implementation

### AspectJ Interception

Metatest uses compile-time and load-time weaving to intercept:

1. **Test method execution** — `@Around("execution(@org.junit.jupiter.api.Test * *(..))")`
   - Establishes thread-local test context
   - Captures baseline test execution
   - Triggers fault simulation after successful baseline

2. **HTTP client calls** — `@Around("execution(* org.apache.http.impl.client.CloseableHttpClient.execute(..))")`
   - Intercepts Apache HttpClient requests
   - Captures request/response pairs
   - Injects faulty responses during simulation runs

### Fault Injection Strategies

| Strategy | Mutation | Use Case |
|----------|----------|----------|
| `NullFieldStrategy` | Set field value to `null` | Tests assertion: `assertNotNull(response.field)` |
| `MissingFieldStrategy` | Remove field from JSON | Tests field existence checks |
| `EmptyListStrategy` | Replace array with `[]` | Tests collection size assertions |
| `EmptyStringStrategy` | Replace string with `""` | Tests non-empty string validation |

### Simulation Algorithm

```
for each test that exercises endpoint E:
    baseline = run test, capture response
    for each contract fault type:
        for each field in response:
            inject fault → re-run test → record caught/escaped
    for each invariant defined on E:
        generate mutation that violates the invariant
        re-run test → record caught/escaped
```

---

## Configuration

All Metatest configuration lives in `src/test/resources/metatest/`:

### Directory Structure

```
src/test/resources/
└── metatest/
    ├── contract.yml          # Global fault settings and exclusions
    ├── metatest.properties   # API key and connection settings (optional)
    ├── coverage_config.yml   # Coverage tracking settings (optional)
    └── features/             # Business rule invariants (one file per domain)
        ├── orders.yml
        ├── accounts.yml
        └── auth.yml
```

---

### contract.yml

Controls which contract fault types are enabled globally, plus exclusion rules and simulation settings. **Invariants are not defined here** — they live in feature files.

```yaml
version: "1.0"

settings:
  default_quantifier: all       # For array fields: all, any, none
  stop_on_first_catch: true     # Skip simulation once any test catches a fault

contract:
  null_field:
    enabled: true
  missing_field:
    enabled: true
  empty_list:
    enabled: false
  empty_string:
    enabled: false

exclusions:
  urls:
    - '*/health*'
    - '*/actuator/*'
  tests:
    - '*SmokeTest*'

simulation:
  only_success_responses: true
  skip_collections_response: true
  min_response_fields: 1
```

---

### Feature Files

Feature files define the invariants (business rules) for a domain. They live in `src/test/resources/metatest/features/` and are loaded automatically.

Each file groups related invariants together and specifies which tests exercise those endpoints:

```yaml
feature: "Order Management"
description: >
  Business rules for order lifecycle: status transitions,
  price constraints, and temporal ordering.

invariants:
  /api/v1/orders/{id}:
    GET:
      invariants:
        - name: positive_quantity
          field: quantity
          greater_than: 0

        - name: valid_status
          field: status
          in: [PENDING, FILLED, REJECTED, CANCELLED]

        - name: filled_order_has_timestamp
          if:
            field: status
            equals: FILLED
          then:
            field: filled_at
            is_not_null: true

        - name: created_before_filled
          if:
            field: filled_at
            is_not_null: true
          then:
            field: created_at
            less_than_or_equal: $.filled_at

  /api/v1/orders:
    GET:
      invariants:
        - name: all_orders_positive_quantity
          field: $[*].quantity
          greater_than: 0

    POST:
      invariants:
        - name: new_order_valid_status
          field: status
          in: [PENDING, FILLED, REJECTED]

# Tests that exercise these endpoints.
# Metatest re-runs these during simulation to check if they catch violations.
tests:
  - class: com.example.OrdersApiTest
    methods:
      - testCreateBuyOrder
      - testCreateSellOrder
      - testListMyOrders
```

**Why feature files instead of a single config?**

- Invariants express business semantics — grouping them by domain keeps them meaningful and maintainable
- Different teams can own different feature files
- Test mappings make it explicit which tests are responsible for catching which violations
- The report shows gaps per feature, not just per endpoint

---

### metatest.properties

Optional. Required only when using the cloud API for fault strategy configuration.

```properties
# src/test/resources/metatest/metatest.properties
metatest.api.key=mt_proj_xxxxxxxxxxxxx
metatest.project.id=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
metatest.api.url=http://localhost:8080

# Force local mode even when API key is present
metatest.config.source=local
```

Configuration source priority: system property `metatest.config.source` > env var `METATEST_CONFIG_SOURCE` > `metatest.properties` > auto-detect (uses API if key is present).

---

### coverage_config.yml

Optional. Controls endpoint coverage tracking.

```yaml
# src/test/resources/metatest/coverage_config.yml
coverage:
  enabled: true
  output_file: schema_coverage.json
  urls:
    - http://localhost:8080   # empty = track all
  include_request_body: true
  include_response_body: false
  aggregate_by_pattern: true
  gap_analysis:
    enabled: true
    openapi_spec_path: api-spec.yaml
    output_file: gap_analysis.json
```

---

## Invariants DSL Reference

Invariants define business rules that API responses must satisfy. Metatest generates mutations that violate these rules and verifies your tests detect the violations.

### Supported Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `equals` | Exact match | `equals: "ACTIVE"` |
| `not_equals` | Not equal to | `not_equals: "DELETED"` |
| `greater_than` | Numeric > | `greater_than: 0` |
| `greater_than_or_equal` | Numeric >= | `greater_than_or_equal: 0` |
| `less_than` | Numeric < | `less_than: 100` |
| `less_than_or_equal` | Numeric <=, or cross-field | `less_than_or_equal: $.updated_at` |
| `in` | Value in list | `in: [BUY, SELL]` |
| `not_in` | Value not in list | `not_in: [DELETED, ARCHIVED]` |
| `is_null` | Must be null | `is_null: true` |
| `is_not_null` | Must not be null | `is_not_null: true` |
| `is_empty` | Must be empty | `is_empty: true` |
| `is_not_empty` | Must not be empty | `is_not_empty: true` |

### Field References

Use `$.field_name` to reference another field in the same response:

```yaml
- name: created_before_updated
  field: created_at
  less_than_or_equal: $.updated_at
```

### Array Fields

Use `$[*].field` to validate every item in an array response:

```yaml
- name: all_prices_positive
  field: $[*].price
  greater_than: 0
```

The `default_quantifier` in `contract.yml` controls evaluation:
- `all` (default): every item must satisfy the condition
- `any`: at least one item must satisfy
- `none`: no item should satisfy

### Conditional Invariants (if/then)

Rules that apply only when a precondition holds:

```yaml
- name: shipped_order_has_tracking
  if:
    field: status
    equals: SHIPPED
  then:
    field: tracking_number
    is_not_empty: true
```

Metatest skips this invariant when `status != SHIPPED`. When `status == SHIPPED`, it generates a mutation violating the `then` clause and checks if your test catches it.

---

## Installation

### Step 1: Add JitPack Repository

**settings.gradle.kts:**
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: Add Dependency

**build.gradle.kts:**
```kotlin
dependencies {
    testImplementation("com.github.at-boundary:metatest-rest-java:v0.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    testImplementation("io.rest-assured:rest-assured:5.3.0")
}
```

### Step 3: Configure AspectJ Agent

**build.gradle.kts:**
```kotlin
tasks.test {
    useJUnitPlatform()

    val aspectjAgent = configurations.testRuntimeClasspath.get()
        .files.find { it.name.contains("aspectjweaver") }

    if (aspectjAgent != null) {
        jvmArgs(
            "-javaagent:$aspectjAgent",
            "-DrunWithMetatest=${System.getProperty("runWithMetatest") ?: "false"}"
        )
    }
}
```

### Step 4: Create Configuration

Create `src/test/resources/metatest/contract.yml`:

```yaml
version: "1.0"

settings:
  default_quantifier: all
  stop_on_first_catch: true

contract:
  null_field:
    enabled: true
  missing_field:
    enabled: true
```

Create feature files in `src/test/resources/metatest/features/`:

```yaml
# src/test/resources/metatest/features/users.yml
feature: "User Management"

invariants:
  /api/users/{id}:
    GET:
      invariants:
        - name: user_has_email
          field: email
          is_not_empty: true

        - name: valid_status
          field: status
          in: [ACTIVE, SUSPENDED, PENDING]

        - name: active_user_has_verified_email
          if:
            field: status
            equals: ACTIVE
          then:
            field: email_verified
            equals: true

tests:
  - class: com.example.UserApiTest
    methods:
      - testGetUser
      - testListUsers
```

### Step 5: Run Tests

```bash
# Normal test execution
./gradlew test

# With Metatest fault simulation
./gradlew test -DrunWithMetatest=true
```

---

## Reports and Analytics

Metatest generates both JSON and HTML reports after test execution.

### HTML Report

Generated at `metatest_report.html`. Open in any browser for an interactive view.

**Tabs:**
- **Summary** — overall detection rate, escaped vs caught fault counts
- **Fault Simulation** — per-endpoint breakdown of contract and invariant faults
- **Test Matrix** — 2D grid of tests × faults showing which tests catch which violations
- **Coverage** — endpoint coverage with HTTP call logs
- **Gap Analysis** — endpoints in OpenAPI spec not covered by any test

### Fault Simulation Report (JSON)

Generated at `fault_simulation_report.json`:

```json
{
  "/api/v1/orders/{id}": {
    "contract_faults": {
      "null_field": {
        "status": {
          "caught_by_any_test": true,
          "tested_by": ["OrdersApiTest.testGetOrder"],
          "caught_by": [{ "test": "OrdersApiTest.testGetOrder", "caught": true }]
        }
      }
    },
    "invariant_faults": {
      "filled_order_has_timestamp": {
        "caught_by_any_test": false,
        "tested_by": ["OrdersApiTest.testGetOrder"],
        "caught_by": []
      }
    }
  }
}
```

- `caught_by_any_test: false` — no test detected this violation; this is a test quality gap
- `contract_faults` — structural mutations grouped by fault type and field
- `invariant_faults` — business rule violations grouped by invariant name

### Console Summary

Metatest prints an ASCII summary after all simulations:

```
============================================================
  METATEST FAULT SIMULATION SUMMARY
============================================================
  Test                          Caught   Total   Escaped
------------------------------------------------------------
  OrdersApiTest.testGetOrder      8        12      4
  AuthApiTest.testLogin           3         3      0
------------------------------------------------------------
  TOTAL                          11        15      4
============================================================
Escaped faults in OrdersApiTest.testGetOrder:
  [X] invariant: filled_order_has_timestamp
  [X] invariant: valid_status
  ...
```

---

## Integration with CI/CD

### GitHub Actions

```yaml
name: Metatest Validation

on: [push, pull_request]

jobs:
  test-quality:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run tests with Metatest
        run: ./gradlew test -DrunWithMetatest=true

      - name: Upload reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: metatest-reports
          path: |
            fault_simulation_report.json
            metatest_report.html
            schema_coverage.json
```

---

## Performance Considerations

Simulation time scales with: tests × response fields × enabled fault types.

**Optimization strategies:**
- `stop_on_first_catch: true` — skips a fault once any test catches it (faster, less detail)
- `simulation.only_success_responses: true` — skip error responses
- `simulation.min_response_fields` — skip simple responses
- Add slow tests to `exclusions.tests`
- Run Metatest on CI only, not during local development

---

## Requirements

- **Java**: 17 or higher
- **Gradle**: 7.3 or higher
- **JUnit**: 5.x (Jupiter)
- **HTTP Client**: Apache HttpClient (via RestAssured or direct usage)

---

## Troubleshooting

### No fault simulation occurs

Verify `-DrunWithMetatest=true` is set and `aspectjweaver` is on the test classpath.

### Configuration file not found

Ensure `contract.yml` is at `src/test/resources/metatest/contract.yml`. The fallback search order is: `metatest/contract.yml` → `contract.yml` → `config.yml`.

### Unexpected API connection attempt

If you see a `ConnectException` to `localhost:8080`, your `metatest.properties` contains an API key and auto-detection is picking up the API source. Add `metatest.config.source=local` to `metatest.properties` to force local mode.

### Feature invariants not appearing in report

Verify feature files are in `src/test/resources/metatest/features/` and contain a valid `tests:` section mapping to real test class and method names.

### AspectJ weaver not found

```kotlin
testImplementation("org.aspectj:aspectjweaver:1.9.19")
```

---

## Building from Source

```bash
git clone https://github.com/at-boundary/metatest-rest-java.git
cd metatest-rest-java

./gradlew publishToMavenLocal
./gradlew :lib:test

# Run example project
cd ../metatest-rest-java-example
./gradlew test -DrunWithMetatest=true
```
---

## Support

- **Issues**: https://github.com/at-boundary/metatest-rest-java/issues
- **Example Project**: https://github.com/at-boundary/metatest-rest-java-example
