# Building and Testing

## Build/Compile the Project

To compile the project without running tests:

```bash
mvn clean compile
```

## Run All Tests

To run all tests and verify the project:

```bash
mvn clean verify
```

This command will:

- Clean previous builds
- Compile the code
- Run all unit tests
- Run integration tests (if configured)
- Run code quality checks (like Spotless)

## Run Specific Tests

Because this is a multi-module project, use `-pl <module>` to target the module containing the test.
Without it, Surefire fails on modules that don't contain the specified test class.

To run a specific test class:

```bash
mvn test -pl modbus -Dtest=Crc16Test
```

To run a specific test method:

```bash
mvn test -pl modbus -Dtest=Crc16Test#crc16
```

To run multiple test classes in the same module:

```bash
mvn test -pl modbus -Dtest=Crc16Test,MbapHeaderTest
```

To run tests matching a pattern in a specific module:

```bash
mvn test -pl modbus -Dtest=*RequestTest
```

Available modules: `modbus`, `modbus-tcp`, `modbus-serial`, `modbus-tests`.

## Other Common Maven Commands

**Run tests only (skip compilation if already compiled):**

```bash
mvn test
```

**Package the project (creates JAR file):**

```bash
mvn clean package
```

**Install to local Maven repository:**

```bash
mvn clean install
```

**Run only unit tests (skip integration tests):**

```bash
mvn clean test
```

## Code Formatting

This project uses Spotless with Google Java Format for code formatting.

The `spotless:check` goal is bound to the `verify` phase and will fail the build if code is not
properly formatted.

If the build fails due to formatting issues, run:

```bash
mvn spotless:apply
```

This will automatically format all Java files according to Google Java Format standards.

## Dependency Source Code

To examine dependency source code, check the `external/src` directory at the project root. This
directory contains unpacked source files from all dependencies, organized by package structure for
easy browsing and searching.

**If the directory doesn't exist or content is missing:**

Run this command from the project root to download and unpack all dependency sources:

```bash
mvn generate-resources -Pdownload-external-src
```

This will create the `external/src` directory with sources from all dependencies in a single
top-level location.
