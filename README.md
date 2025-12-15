# Pure Java Framework Internals

Educational demonstrations of how Spring Boot and Hibernate work internally, using only JDK features—no frameworks required!

## 🎯 Purpose

Ever wondered how Spring Boot magically routes requests to controllers? Or how Hibernate tracks entity changes and lazily loads relationships? These demos demystify the "magic" by implementing simplified versions using pure Java reflection, annotations, and proxies.

## 📚 What's Inside

### 1. [Mini Spring Boot Dispatcher](MiniSpringDispatcher.java)
Demonstrates how Spring Boot's `DispatcherServlet` works:
- **Custom annotations** (`@RestController`, `@GetMapping`, `@PathVariable`)
- **Handler mapping** - Routes URLs to controller methods using reflection
- **Argument resolution** - Extracts path variables and converts types
- **Front Controller pattern** - One dispatcher orchestrates all requests

```java
@RestController(path = "/api")
class UserController {
    @GetMapping("/users/{id}")
    public String getUser(@PathVariable("id") Long id) {
        return "{\"id\": " + id + ", \"name\": \"User " + id + "\"}";
    }
}
```

**Key Concepts**: Annotations, Reflection, URL Pattern Matching, Method Invocation

### 2. [Mini JPA/Hibernate](MiniJPAHibernate.java)
Demonstrates how JPA/Hibernate ORM works:
- **Entity metadata** - Introspects `@Entity`, `@Id`, `@Column` annotations
- **Persistence context** - First-level cache ensuring object identity
- **Lazy loading** - Defers database queries using wrapper objects
- **Dirty checking** - Automatically detects and persists changes
- **Transaction management** - Coordinates flush and commit

```java
@Entity(table = "users")
class User {
    @Id @GeneratedValue
    private Long id;
    private String username;
}

em.beginTransaction();
User user = em.find(User.class, 1L);  // Query database
User same = em.find(User.class, 1L);  // Returns cached instance!
assert user == same;  // Same object reference
em.commit();
```

**Key Concepts**: ORM, Persistence Context, Lazy Loading, Change Tracking, SQL Generation

## 🚀 Quick Start

### Prerequisites
- Java 17+
- [JBang](https://www.jbang.dev/) (installs automatically with one command)

### Installation
```bash
# Install JBang (macOS/Linux)
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Or via Homebrew
brew install jbangdev/tap/jbang
```

### Running the Demos

```bash
# Spring Boot Dispatcher Demo
jbang MiniSpringDispatcher.java

# JPA/Hibernate Demo
jbang MiniJPAHibernate.java
```

JBang automatically downloads dependencies (like H2 database) on first run!

## 📖 What You'll Learn

### From the Spring Boot Demo:
- How `@RestController` and `@GetMapping` annotations are processed
- Why there's a performance overhead compared to raw servlets
- How path variables like `/users/{id}` are extracted
- What happens between receiving an HTTP request and calling your controller

### From the JPA/Hibernate Demo:
- Why `em.find(User.class, 1L)` twice returns the **same instance**
- How lazy loading works and why it causes `LazyInitializationException`
- When SQL is actually executed (hint: not when you call `persist()`)
- What causes the N+1 query problem
- Why Hibernate needs bytecode manipulation (we use simple wrappers)

## 🎓 Educational Value

These demos strip away all the complexity to reveal the core techniques:
- **No magic** - Just annotations + reflection + careful state tracking
- **Simplified** - ~500 lines each vs thousands in real frameworks
- **Runnable** - See exactly when SQL executes and caches are hit
- **Hackable** - Experiment by adding features (validation, caching, interceptors)

Perfect for:
- Understanding framework internals before job interviews
- Debugging Spring/Hibernate issues in production
- Appreciating what frameworks do for you
- Learning advanced Java techniques (reflection, proxies, annotations)

## 📝 Detailed Documentation

Each demo includes a comprehensive README:
- [Spring Boot Dispatcher README](README-SpringBoot.md) - Architecture, examples, extension ideas
- [JPA/Hibernate README](README-JPA.md) - Persistence context, lazy loading, common pitfalls

## 🔧 Real-World Differences

These are educational simplifications. Production frameworks add:
- **Performance**: Caching, bytecode enhancement, connection pooling
- **Features**: Validation, security, transaction propagation, distributed caching
- **Robustness**: Thread safety, error handling, edge case coverage
- **Scale**: Handling thousands of entities, complex queries, cluster coordination

But the **core concepts** are the same!

## 🤝 Contributing

Found a bug? Want to add features? PRs welcome! Ideas:
- Add `@RequestParam` support for query parameters
- Implement `@ExceptionHandler` for error handling
- Add second-level cache to JPA demo
- Support `@OneToMany` collections
- Implement JPQL query language

## 📄 License

MIT License - Use freely for learning and teaching!

## 🙏 Acknowledgments

Inspired by the brilliant engineering in Spring Framework and Hibernate ORM. Standing on the shoulders of giants to help others understand how giants work.

---

**⭐ If this helped you understand Spring or Hibernate better, give it a star!**
