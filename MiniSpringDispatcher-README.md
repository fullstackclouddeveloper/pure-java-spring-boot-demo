# Mini Spring Boot Dispatcher

A pure Java demonstration of how Spring Boot's DispatcherServlet works internally, using only JDK features like annotations and reflection. No frameworks required!

## What This Demonstrates

This project shows the core mechanisms Spring Boot uses to route HTTP requests to controller methods:

### 1. **Custom Annotations**
Just like Spring's `@RestController`, `@GetMapping`, etc., this creates custom annotations:
```java
@RestController(path = "/api")
static class UserController {
    @GetMapping("/users/{id}")
    public String getUser(@PathVariable("id") Long id) {
        return "{\"id\": " + id + ", \"name\": \"User " + id + "\"}";
    }
}
```

### 2. **Handler Mapping**
Scans controller classes using Java reflection to:
- Find all methods annotated with `@GetMapping` or `@PostMapping`
- Convert URL patterns like `/users/{id}` into regex patterns
- Build a registry mapping URLs to controller methods

### 3. **Request Dispatching**
When a request comes in:
1. **HandlerMapping** finds the matching controller method
2. **HandlerAdapter** resolves method parameters by examining annotations
3. Method is invoked using reflection
4. Result is converted to an HTTP response

### 4. **Argument Resolution**
Automatically resolves method parameters:
- `@PathVariable("id")` → extracts from URL path
- `@RequestBody` → passes request body
- Type conversion (String → Long, Integer, etc.)

## Architecture

```
HttpRequest
    ↓
DispatcherServlet
    ↓
HandlerMapping (finds controller method via reflection)
    ↓
HandlerAdapter (resolves arguments & invokes method)
    ↓
Controller Method Execution
    ↓
HttpResponse
```

This is essentially how Spring Boot's `DispatcherServlet` works, just simplified!

## Prerequisites

- Java 17 or later
- [JBang](https://www.jbang.dev/) installed

### Installing JBang

**macOS/Linux:**
```bash
curl -Ls https://sh.jbang.dev | bash -s - app setup
```

**Windows:**
```powershell
iex "& { $(iwr https://ps.jbang.dev) } app setup"
```

**Or via package managers:**
```bash
# macOS
brew install jbangdev/tap/jbang

# Linux (SDKMAN)
sdk install jbang

# Windows (Chocolatey)
choco install jbang
```

## Running the Demo

### Option 1: Direct Execution (Unix/Linux/macOS)
```bash
chmod +x MiniSpringDispatcher.java
./MiniSpringDispatcher.java
```

### Option 2: Using JBang
```bash
jbang MiniSpringDispatcher.java
```

### Option 3: Compile and Run Traditionally
```bash
javac MiniSpringDispatcher.java
java MiniSpringDispatcher
```

## Sample Output

```
=== Mini Spring Boot Dispatcher Demo ===

This demonstrates how Spring Boot's DispatcherServlet works:
1. Annotations define routes (@GetMapping, @PostMapping)
2. HandlerMapping finds the right controller method using reflection
3. HandlerAdapter resolves arguments and invokes the method
4. Result is converted to HTTP response

[DispatcherServlet] Processing: GET /health
[HandlerMapping] Looking for handler...
[HandlerMapping] Found: HealthController.health()
[HandlerAdapter] Resolving arguments and invoking...
[MessageConverter] Converting result to response
[Response] Status: 200
[Response] Body: {"status": "UP"}
────────────────────────────────────────────────────────────────────────────────

[DispatcherServlet] Processing: GET /api/users/123
[HandlerMapping] Looking for handler...
[HandlerMapping] Found: UserController.getUser()
[HandlerAdapter] Resolving arguments and invoking...
[MessageConverter] Converting result to response
[Response] Status: 200
[Response] Body: {"id": 123, "name": "User 123"}
────────────────────────────────────────────────────────────────────────────────
```

## What You'll Learn

By reading this code, you'll understand:

1. **How Spring's annotations work** - They're just metadata that can be read at runtime via reflection
2. **How URL routing works** - Pattern matching using regex and named capture groups
3. **How method parameters are resolved** - Inspecting parameter annotations and types
4. **How the Front Controller pattern works** - One dispatcher coordinates all requests
5. **Why there's performance overhead** - Each request involves reflection, pattern matching, and multiple component lookups

## Key Components

| Component | Spring Equivalent | Purpose |
|-----------|------------------|---------|
| `HandlerMapping` | `RequestMappingHandlerMapping` | Finds which controller method handles a request |
| `HandlerAdapter` | `RequestMappingHandlerAdapter` | Invokes the controller method with resolved arguments |
| `DispatcherServlet` | `DispatcherServlet` | Orchestrates the entire request/response flow |
| `@RestController` | `@RestController` | Marks a class as a REST controller |
| `@GetMapping` | `@GetMapping` | Maps HTTP GET requests to methods |
| `@PathVariable` | `@PathVariable` | Extracts variables from URL paths |

## Extending the Demo

Try adding:
- `@RequestParam` support for query parameters
- Exception handling with `@ExceptionHandler`
- Interceptors for cross-cutting concerns
- JSON serialization using a library like Gson
- Async request handling

## Real-World Differences

This demo simplifies several things that Spring Boot does:
- **Thread safety**: Real Spring uses thread-safe data structures
- **Performance**: Spring caches reflection lookups and uses optimized data structures
- **Features**: Spring supports many more annotations, content negotiation, validation, etc.
- **Error handling**: Spring has sophisticated exception resolution
- **Integration**: Spring integrates with servlet containers, application servers, etc.

## Why This Matters

Understanding this helps you:
- Debug Spring Boot applications more effectively
- Make informed performance optimization decisions
- Appreciate what frameworks do for you
- Understand the trade-offs between raw servlets and frameworks

## License

This is a educational demonstration. Feel free to use and modify for learning purposes.

## Further Reading

- [Spring MVC Documentation](https://docs.spring.io/spring-framework/reference/web/webmvc.html)
- [DispatcherServlet Source Code](https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/java/org/springframework/web/servlet/DispatcherServlet.java)
- Java Reflection Tutorial
- Front Controller Pattern
