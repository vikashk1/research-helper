---
name: test-case-generator
description: Generate JUnit 5 + Mockito unit tests for a Java class in this project. Use when the user asks to write tests, generate test cases, add test coverage, or test a specific class or method.
---

Generate JUnit 5 + Mockito unit tests for: $ARGUMENTS

## Project context
- Java 17, Spring Boot 3.2.5, Maven, Lombok
- `spring-boot-starter-test` — JUnit 5, Mockito, AssertJ available
- Constructor injection via `@RequiredArgsConstructor`

## Steps

1. **Resolve target.** If `$ARGUMENTS` is empty, glob `src/main/java/**/*.java` and ask which class. Otherwise locate or read the file.

2. **Analyse the class** — dependencies, public methods, exception paths.

3. **Derive test path** — mirror main → test:
   `src/main/java/com/epam/research/agent/Foo.java` → `src/test/java/com/epam/research/agent/FooTest.java`

4. **Generate test class:**
   - `@ExtendWith(MockitoExtension.class)`, never `@SpringBootTest`
   - `@Mock` per dependency, `@InjectMocks` for the class under test
   - Method naming: `should_<outcome>_when_<condition>()`
   - Cover: happy path, null/empty/boundary inputs, exception paths
   - Use AssertJ for assertions, `ArgumentCaptor` to inspect mock args
   - Methods throwing `UnsupportedOperationException("Not implemented yet")` → single test asserting that exception

5. **Project-specific mock rules:**
   - `AnthropicClient` → `@Mock`; stub `.messages().create(params)` to return a fake `Message`
   - `JobRepository`, `SseService`, `JobService`, `CoordinatorAgent`, `ClarificationAgent` → `@Mock` when depended upon

6. **Write** the test file to the derived path.

7. **Report** file written, scenarios covered, any skipped methods with reason.
