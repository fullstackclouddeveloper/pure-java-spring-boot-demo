# Mini JPA/Hibernate

A pure Java demonstration of how JPA and Hibernate work internally, using only JDK features like annotations, reflection, and dynamic proxies. No frameworks required!

## What This Demonstrates

This project shows the core mechanisms JPA/Hibernate uses for Object-Relational Mapping (ORM):

### 1. **Entity Metadata Extraction**
Uses reflection to analyze entity classes:
```java
@Entity(table = "users")
static class User {
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(name = "username")
    private String username;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private User manager;
}
```

The framework scans these annotations to understand:
- Table names and column mappings
- Primary keys and generation strategies
- Relationships between entities
- Fetch strategies (EAGER vs LAZY)

### 2. **Persistence Context (First-Level Cache)**
Implements the "identity map" pattern:
- Ensures only ONE instance exists per database row in memory
- Caches entities within a transaction
- Tracks entity state (new, managed, dirty)
- Prevents redundant database queries

### 3. **Lazy Loading with Wrappers**
Creates wrapper objects that load data on first access:
```java
Post post = em.find(Post.class, 1L);
// Author is a wrapper - NOT loaded yet
User author = post.getAuthor();
// NOW it loads from database when accessed
String name = author.getUsername();
```

**Note**: Real Hibernate uses bytecode manipulation (Javassist/ByteBuddy) to create actual subclasses of your entities. We use a simple wrapper for demonstration purposes since Java's dynamic proxies only work with interfaces.

### 4. **Automatic Dirty Checking**
Tracks changes to managed entities:
- Detects modifications to entity fields
- Generates UPDATE SQL automatically on flush
- No need to call explicit `update()` method

### 5. **Transaction Management**
Coordinates database operations:
- BEGIN transaction
- Flush changes (INSERT/UPDATE)
- COMMIT or ROLLBACK

## Architecture

```
EntityManager
    ↓
PersistenceContext (First-Level Cache)
    ↓
Entity Metadata (via Reflection)
    ↓
SQL Generation & Execution
    ↓
Result Mapping (ResultSet → Entity)
    ↓
Lazy Proxy Creation (Dynamic Proxy)
```

This is essentially how Hibernate's `SessionImpl` and JPA's `EntityManager` work!

## Prerequisites

- Java 17 or later
- [JBang](https://www.jbang.dev/) installed (automatically downloads H2 database)

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
chmod +x MiniJPAHibernate.java
./MiniJPAHibernate.java
```

### Option 2: Using JBang
```bash
jbang MiniJPAHibernate.java
```

JBang will automatically download the H2 database dependency!

## Sample Output

```
=== Mini JPA/Hibernate Demo ===

This demonstrates how JPA/Hibernate works:
1. Entity metadata extracted via reflection
2. Persistence context (first-level cache)
3. Lazy loading with dynamic proxies
4. Automatic dirty checking
5. Transaction management

[Setup] Creating database schema...

================================================================================
DEMO 1: Basic CRUD Operations
================================================================================

[Transaction] BEGIN
[EntityManager] Marked for INSERT: User
[EntityManager] Flushing changes to database
  [SQL] INSERT INTO users (username, email) VALUES (?, ?)
  [PersistenceContext] Cached: User#1
[Transaction] COMMIT

Result: User{id=1, username='john_doe', email='john@example.com'}

================================================================================
DEMO 2: Persistence Context (First-Level Cache)
================================================================================

[Transaction] BEGIN
[EntityManager] Finding User#1
  [SQL] SELECT * FROM users WHERE id = ?
  [PersistenceContext] Cached: User#1

First find: User{id=1, username='john_doe', email='john@example.com'}

[EntityManager] Finding User#1
  [PersistenceContext] Cache HIT: User#1

Second find: User{id=1, username='john_doe', email='john@example.com'}

Same instance? true
[Transaction] COMMIT

================================================================================
DEMO 3: Lazy Loading with Dynamic Proxies
================================================================================

[Transaction] BEGIN
[EntityManager] Finding User#1
  [PersistenceContext] Cache HIT: User#1
[EntityManager] Marked for INSERT: Post
[EntityManager] Flushing changes to database
  [SQL] INSERT INTO posts (title, content, author_id) VALUES (?, ?, ?)
[Transaction] COMMIT
  [PersistenceContext] Cleared
[Transaction] BEGIN

Loading post...
[EntityManager] Finding Post#1
  [SQL] SELECT * FROM posts WHERE id = ?
  [PersistenceContext] Cached: Post#1
Post loaded: Post{id=1, title='My First Post'}
Author is lazy wrapper: true

Now accessing author...
    [LazyProxy] Loading User#1
[EntityManager] Finding User#1
  [SQL] SELECT * FROM users WHERE id = ?
  [PersistenceContext] Cached: User#1
Author: john_doe
[Transaction] COMMIT
```

## What You'll Learn

By reading this code, you'll understand:

1. **How ORM mapping works** - Converting between objects and relational tables
2. **The Persistence Context** - Why it's called "first-level cache" and how it ensures identity
3. **Lazy loading mechanics** - How dynamic proxies defer database queries
4. **Why Hibernate seems "magical"** - It's just reflection, proxies, and careful state tracking
5. **N+1 query problem** - Why lazy loading can cause performance issues
6. **Transaction boundaries** - When SQL is actually executed

## Key Components

| Component | JPA/Hibernate Equivalent | Purpose |
|-----------|-------------------------|---------|
| `EntityMetadata` | `EntityPersister` / `EntityMetamodel` | Stores metadata about entity classes |
| `PersistenceContext` | `PersistenceContext` / `StatefulPersistenceContext` | First-level cache, tracks entity state |
| `EntityManager` | `EntityManager` / `SessionImpl` | Main API for CRUD operations |
| `LazyLoadingWrapper` | `LazyInitializer` / `javassist` proxies | Defers loading until accessed |
| `@Entity` | `@Entity` | Marks a class as a database entity |
| `@Id` | `@Id` | Marks the primary key field |
| `@ManyToOne` | `@ManyToOne` | Defines a many-to-one relationship |

## Key Concepts Demonstrated

### Persistence Context (First-Level Cache)

The persistence context ensures:
- **Identity**: Only one instance per database row
- **Change tracking**: Automatically detects modifications
- **Performance**: Avoids redundant queries

```java
User user1 = em.find(User.class, 1L); // Query database
User user2 = em.find(User.class, 1L); // Returns cached instance
assert user1 == user2; // Same object!
```

### Lazy Loading

Relationships marked as `LAZY` create wrapper objects:
- Wrapper defers database query until first access
- Allows loading only what you need
- In real Hibernate, uses bytecode manipulation to create actual subclasses

**Note**: This demo uses a simple wrapper class. Real Hibernate uses libraries like Javassist or ByteBuddy to create actual proxy subclasses of your entities at runtime, which is more transparent but requires bytecode manipulation.

**Beware**: Accessing lazy relationships outside a transaction causes `LazyInitializationException`!

### Transaction and Flush

Changes aren't immediately written to the database:
1. `persist()` / `merge()` marks entities
2. `flush()` generates and executes SQL
3. `commit()` makes changes permanent

```java
em.beginTransaction();
User user = new User("john", "john@example.com");
em.persist(user); // No SQL yet!
em.commit();      // SQL executed here
```

### Entity States

- **Transient**: New object, not yet persisted
- **Managed**: Tracked by persistence context
- **Detached**: Was managed, but context closed
- **Removed**: Marked for deletion

## Extending the Demo

Try adding:
- `@OneToMany` relationships with collections
- Cascade operations (CascadeType.ALL)
- Second-level cache (shared across sessions)
- JPQL query language
- Optimistic locking with `@Version`
- Inheritance strategies
- Composite keys

## Real-World Differences

This demo simplifies several things that Hibernate does:

### Performance Optimizations
- **Bytecode enhancement**: Hibernate can modify entity classes at runtime
- **Batch operations**: Groups multiple INSERTs/UPDATEs
- **SQL generation**: Much more sophisticated query building
- **Connection pooling**: Manages database connections efficiently

### Advanced Features
- **Second-level cache**: Shared cache across sessions (Ehcache, Hazelcast)
- **Query cache**: Caches query results
- **Criteria API**: Type-safe queries
- **Native SQL**: Direct SQL execution when needed
- **Interceptors**: Hook into entity lifecycle events

### Production Concerns
- **Thread safety**: Hibernate sessions are not thread-safe
- **Transaction management**: Integration with JTA, Spring transactions
- **Schema generation**: Can auto-create tables from entities
- **Migration tools**: Flyway, Liquibase for version control

## Common Pitfalls

### N+1 Query Problem
```java
List<Post> posts = em.query("SELECT p FROM Post p");
for (Post post : posts) {
    System.out.println(post.getAuthor().getName()); // Lazy load!
}
// Results in: 1 query for posts + N queries for authors
```

**Solution**: Use JOIN FETCH or eager loading

### LazyInitializationException
```java
em.beginTransaction();
Post post = em.find(Post.class, 1L);
em.commit();
em.close();

// Later...
post.getAuthor().getName(); // ERROR! Session closed
```

**Solution**: Load data within transaction or use DTOs

### Identity vs Equality
```java
User user1 = em.find(User.class, 1L);
User user2 = new User();
user2.setId(1L);

user1 == user2; // false - different instances
```

**Solution**: Override `equals()` and `hashCode()` based on ID

## Performance Implications

JPA/Hibernate adds overhead:
- Reflection for metadata extraction (cached)
- Proxy creation for lazy loading
- Change detection (dirty checking)
- First-level cache maintenance

But provides value:
- Eliminates boilerplate SQL
- Prevents SQL injection
- Provides caching strategies
- Handles complex relationships

## Why This Matters

Understanding this helps you:
- Debug `LazyInitializationException`
- Optimize query performance
- Choose appropriate fetch strategies
- Understand when SQL is executed
- Appreciate the complexity ORMs hide

## Further Reading

- [Hibernate Architecture](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#architecture)
- [JPA Specification](https://jakarta.ee/specifications/persistence/)
- [Vlad Mihalcea's Blog](https://vladmihalcea.com/) - Hibernate expert
- "Java Persistence with Hibernate" book
- Proxy Pattern and Dynamic Proxies in Java

## License

This is an educational demonstration. Feel free to use and modify for learning purposes.
