# Project Context

**Tech Stack:** Java 17, Maven multi-module

A Modbus implementation for Java, providing client and server APIs for Modbus TCP, Modbus TCP
Security, Modbus RTU on Serial, and Modbus RTU on TCP.

**Architecture:**

- **modbus**: Core module — PDUs, framing, client/server APIs, and transport
  interfaces
- **modbus-tcp**: TCP and RTU-over-TCP transport implementations (Netty)
- **modbus-serial**: Serial port transport implementations (jSerialComm)
- **modbus-tests**: Integration tests

## Key Entry Points

- Client API: `modbus/.../client/ModbusClient.java`
- Server API: `modbus/.../server/ModbusServer.java`
- TCP transports: `modbus-tcp/.../tcp/client/` and `modbus-tcp/.../tcp/server/`
- Serial transports: `modbus-serial/.../serial/client/` and `.../serial/server/`

## Building and Testing

| Command                                         | Purpose                                    |
|-------------------------------------------------|--------------------------------------------|
| `mvn clean compile`                             | Compile without tests                      |
| `mvn clean verify`                              | Full build with tests and formatting check |
| `mvn spotless:apply`                            | Fix code formatting issues                 |
| `mvn test -pl <module> -Dtest=ClassName`        | Run a specific test class                  |
| `mvn test -pl <module> -Dtest=Class#methodName` | Run a specific test method                 |

**Note:** The `-pl <module>` flag (e.g., `-pl modbus`, `-pl modbus-tcp`) is required when running
specific tests in this multi-module project. Without it, Surefire fails on modules that don't
contain the specified test class.

For detailed testing patterns and other Maven commands, see
`.claude/docs/building-and-testing.md`.

## Additional Resources

- Java conventions: `.claude/docs/java-coding-conventions.md`
- Building and testing: `.claude/docs/building-and-testing.md`

## Verification

Use these steps to verify any completed work. Implementation plans should include these as success
criteria.

1. **Format and compile:**
    - `mvn spotless:apply` - Format code
    - `mvn clean compile` - Compile (skip tests)

2. **Review changes** using a general-purpose subagent to:
    - Review changes for correctness, style, and adherence to project conventions
    - Report **APPROVED** or **CHANGES REQUESTED**

Before committing, ensure all verification steps pass and review approval is received.
