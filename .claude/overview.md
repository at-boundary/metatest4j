# Metatest REST Java — Project Overview

## Problem Statement

Metatest addresses a critical gap in API testing: **assertion quality validation**. Traditional test coverage only measures code execution paths — it cannot determine whether assertions would actually catch real API contract violations.

A test suite can achieve 100% code coverage yet fail to catch:
- API fields returning `null` unexpectedly
- Missing fields in responses
- Empty collections where data is expected
- Business rule violations (e.g., `status=FILLED` but `filled_at=null`)

**Core question Metatest answers:** "Would your tests actually catch real API bugs?"

---

## Architecture Overview

The framework operates transparently through **AspectJ bytecode weaving** — no test code modifications required.

### Module Structure

```
metatest-rest-java/
├── lib/                          # Core library (56 Java files)
│   ├── core/
│   │   ├── interceptor/          # AspectJ interception layer
│   │   ├── config/               # Configuration management (YAML + API)
│   │   └── normalizer/           # Endpoint pattern normalization
│   ├── injection/                # Fault injection strategies
│   ├── invariant/                # Business rule (invariant) validation
│   ├── simulation/               # Test execution engine
│   ├── coverage/                 # Endpoint coverage tracking
│   ├── analytics/                # Gap analysis, assertion analytics
│   ├── http/                     # HTTP abstraction (Apache, OkHttp, etc.)
│   ├── report/                   # HTML report generator
│   └── api/                      # Cloud API integration
├── gradle-plugin/                # Gradle plugin for zero-config setup
│   └── MetatestPlugin.java
└── settings.gradle.kts
```

### Core Execution Flow

1. **Baseline Run**: Test runs normally; AspectJ intercepts HTTP calls and captures request/response pairs
2. **Fault Simulation**: For each captured response, systematic mutations are applied:
   - *Contract faults*: field-level mutations (null, missing, empty list, empty string)
   - *Invariant faults*: business rule violations (e.g., violate `balance > 0`)
3. **Test Re-execution**: Test runs again with each faulty response injected at the exact request position
4. **Result Recording**: Tests that pass despite injected faults = **escaped** (undetected) faults
5. **Report Generation**: JSON and HTML reports with per-endpoint fault detection rates

### Key Classes

| Class | Role |
|-------|------|
| `AspectExecutor` | Main `@Around` advice for `@Test` methods and Apache HttpClient `execute()` |
| `TestContext` | Thread-local state per test: captured requests, simulation index, injected response |
| `Runner` | Orchestrates baseline + fault simulation iterations |
| `FaultSimulationReport` | Singleton (ConcurrentHashMap) accumulating results across all tests |
| `InvariantSimulator` | Runs invariant violation mutations |
| `ViolationGenerator` | Generates concrete mutations that violate a given invariant |
| `ConditionEvaluator` | Evaluates invariant conditions against JSON responses |
| `HtmlReportGenerator` | Produces the interactive HTML report |

---

## Features

### Contract Fault Injection

Four field-level mutation strategies:

| Strategy | What It Does | Catches Tests Asserting |
|----------|-------------|------------------------|
| `NullFieldStrategy` | Set field value to `null` | `assertNotNull(field)` |
| `MissingFieldStrategy` | Remove field entirely | Field existence checks |
| `EmptyListStrategy` | Replace array with `[]` | `assertFalse(list.isEmpty())` |
| `EmptyStringStrategy` | Replace string with `""` | Non-empty string validation |

### Invariants DSL (Business Rules in YAML)

Define business rules that must hold in API responses:

```yaml
endpoints:
  /api/v1/orders/{id}:
    GET:
      invariants:
        - name: filled_order_has_filled_at
          if:
            field: status
            equals: FILLED
          then:
            field: filled_at
            is_not_null: true

        - name: positive_price
          field: price
          greater_than: 0

        - name: valid_status
          field: status
          in: [PENDING, OPEN, FILLED, CANCELLED]
```

**Supported operators:** `equals`, `not_equals`, `greater_than`, `greater_than_or_equal`, `less_than`, `less_than_or_equal`, `in`, `not_in`, `is_null`, `is_not_null`, `is_empty`, `is_not_empty`

**Advanced:**
- Conditional invariants (`if`/`then` blocks)
- Array item validation: `$[*].field` with quantifiers (`all`, `any`, `none`)
- Cross-field comparison: `created_at <= $.updated_at`

### Multi-Request Test Support

Tests making multiple API calls are handled correctly. The framework tracks which request in the sequence to mutate, and injects the faulty response only at that position during re-runs.

### Reporting

| Report | Format | Contents |
|--------|--------|----------|
| `fault_simulation_report.json` | JSON | Per-endpoint fault results (caught/escaped per field/invariant) |
| `metatest_report.html` | HTML (interactive) | Dashboard: summary, fault details, gap analysis, schema coverage |
| `schema_coverage.json` | JSON | Per-endpoint response field coverage |
| `gap_analysis.json` | JSON | Untested endpoints vs OpenAPI spec |

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17+ |
| Build | Gradle 7.3+ (multi-module) |
| Test Framework | JUnit 5 (Jupiter) |
| HTTP Client | Apache HttpClient (via RestAssured) |
| Bytecode Weaving | AspectJ 1.9.22 |
| Config Parsing | Jackson YAML |
| JSON Processing | Jackson Databind + org.json |
| Logging | SLF4J |
| HTTP Mocking (tests) | WireMock 3.0.1 |

---

## Configuration

Primary config: `src/main/resources/config.yml`

```yaml
version: "1.0"

settings:
  default_quantifier: all        # all | any | none for array fields
  stop_on_first_catch: false     # Skip redundant faults in CI

endpoints:
  /api/path:
    METHOD:
      invariants: [...]

faults:
  null_field: { enabled: true }
  missing_field: { enabled: true }
  empty_list: { enabled: false }
  empty_string: { enabled: false }

exclusions:
  urls: ['*/login*', '*/auth/*']
  tests: ['*IntegrationTest*']
  endpoints: ['/admin/*']

simulation:
  only_success_responses: true
  skip_collections_response: true
  min_response_fields: 1
  multiple_endpoints_strategy:
    test_only_last_endpoint: false

report:
  format: json
  output_path: "./fault_simulation_report.json"
```

### Running

```bash
# Activate mutation testing mode
./gradlew test -DrunWithMetatest=true
```

### Gradle Plugin (zero-config)

```kotlin
plugins {
    id("io.metatest") version "0.1.0"
}

metatest {
    apiKey = "your_api_key"
    projectId = "your_project_id"
    apiUrl = "http://localhost:8080"
}
```

---

## Notable Design Decisions

- **AspectJ weaving**: Transparent instrumentation; test code unchanged. Tradeoff: complexity in thread-local context management.
- **Thread-local `TestContext`**: Safe for parallel test execution; cleaned up after each test to prevent leaks.
- **Singleton `FaultSimulationReport`**: Thread-safe accumulation across all test re-executions via `ConcurrentHashMap`.
- **`stop_on_first_catch`**: Performance optimization for CI — once any test catches a fault, skip remaining tests for that mutation.
- **YAML invariants DSL**: Business rules defined in config, not hardcoded in tests. Easier to maintain as rules evolve.
- **OpenAPI integration**: `OpenAPISpecLoader` + `GapAnalyzer` detect endpoints defined in spec but never hit by tests.
- **HTTP abstraction layer**: `HTTPFactory` wraps Apache, OkHttp, and `HttpURLConnection` behind common interfaces for multi-client support.

---

## Project Status

**Version:** 0.1.0 (Experimental / Development)

**Roadmap (from design notes):**
- Easy: Boundary value generation, type-aware invalid values, schema validation
- Medium: Pre/post conditions, state machines, sequence invariants, composite mutations
- Hard: Stateful property-based testing, shrinking, dependent invariants
- Very Hard: Contract inference, model-based testing, temporal logic, equivalence testing
