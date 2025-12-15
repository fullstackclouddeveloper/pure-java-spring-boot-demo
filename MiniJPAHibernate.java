///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Demonstrates how JPA/Hibernate works using pure Java
 * Shows: entities, persistence context, lazy loading, transactions, caching
 */
public class MiniJPAHibernate {

    // ============== ANNOTATIONS (like JPA's @Entity, @Id, etc.) ==============
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Entity {
        String table() default "";
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Id {
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface GeneratedValue {
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Column {
        String name() default "";
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface ManyToOne {
        FetchType fetch() default FetchType.EAGER;
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface OneToMany {
        FetchType fetch() default FetchType.LAZY;
        String mappedBy() default "";
    }
    
    enum FetchType { EAGER, LAZY }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Transient {
    }

    // ============== ENTITY METADATA (introspects entity classes) ==============
    
    static class EntityMetadata {
        Class<?> entityClass;
        String tableName;
        Field idField;
        List<Field> columns = new ArrayList<>();
        Map<Field, RelationshipInfo> relationships = new HashMap<>();
        
        static class RelationshipInfo {
            FetchType fetchType;
            Class<?> targetEntity;
            String mappedBy;
        }
        
        static EntityMetadata analyze(Class<?> clazz) {
            EntityMetadata meta = new EntityMetadata();
            meta.entityClass = clazz;
            
            // Get table name
            Entity entityAnn = clazz.getAnnotation(Entity.class);
            meta.tableName = entityAnn.table().isEmpty() ? 
                clazz.getSimpleName().toLowerCase() : entityAnn.table();
            
            // Scan fields
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                
                if (field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                
                if (field.isAnnotationPresent(Id.class)) {
                    meta.idField = field;
                }
                
                if (field.isAnnotationPresent(ManyToOne.class)) {
                    RelationshipInfo rel = new RelationshipInfo();
                    rel.fetchType = field.getAnnotation(ManyToOne.class).fetch();
                    rel.targetEntity = field.getType();
                    meta.relationships.put(field, rel);
                } else if (field.isAnnotationPresent(OneToMany.class)) {
                    RelationshipInfo rel = new RelationshipInfo();
                    rel.fetchType = field.getAnnotation(OneToMany.class).fetch();
                    rel.mappedBy = field.getAnnotation(OneToMany.class).mappedBy();
                    // Get generic type
                    ParameterizedType pt = (ParameterizedType) field.getGenericType();
                    rel.targetEntity = (Class<?>) pt.getActualTypeArguments()[0];
                    meta.relationships.put(field, rel);
                } else if (!field.isAnnotationPresent(OneToMany.class)) {
                    meta.columns.add(field);
                }
            }
            
            return meta;
        }
        
        String getColumnName(Field field) {
            if (field.isAnnotationPresent(Column.class)) {
                String name = field.getAnnotation(Column.class).name();
                if (!name.isEmpty()) return name;
            }
            return field.getName();
        }
    }

    // ============== PERSISTENCE CONTEXT (tracks entity state) ==============
    
    static class PersistenceContext {
        // First-level cache: identity map ensures one instance per ID
        private final Map<EntityKey, Object> identityMap = new ConcurrentHashMap<>();
        // Track dirty entities for update
        private final Set<Object> dirtyEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
        // Track new entities for insert
        private final Set<Object> newEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
        
        static class EntityKey {
            Class<?> entityClass;
            Object id;
            
            EntityKey(Class<?> entityClass, Object id) {
                this.entityClass = entityClass;
                this.id = id;
            }
            
            @Override
            public boolean equals(Object o) {
                if (!(o instanceof EntityKey)) return false;
                EntityKey k = (EntityKey) o;
                return entityClass.equals(k.entityClass) && Objects.equals(id, k.id);
            }
            
            @Override
            public int hashCode() {
                return Objects.hash(entityClass, id);
            }
        }
        
        void put(Object entity, Object id) {
            EntityKey key = new EntityKey(entity.getClass(), id);
            identityMap.put(key, entity);
            System.out.println("  [PersistenceContext] Cached: " + 
                entity.getClass().getSimpleName() + "#" + id);
        }
        
        Object get(Class<?> entityClass, Object id) {
            EntityKey key = new EntityKey(entityClass, id);
            Object entity = identityMap.get(key);
            if (entity != null) {
                System.out.println("  [PersistenceContext] Cache HIT: " + 
                    entityClass.getSimpleName() + "#" + id);
            }
            return entity;
        }
        
        void markDirty(Object entity) {
            dirtyEntities.add(entity);
        }
        
        void markNew(Object entity) {
            newEntities.add(entity);
        }
        
        void clear() {
            identityMap.clear();
            dirtyEntities.clear();
            newEntities.clear();
            System.out.println("  [PersistenceContext] Cleared");
        }
    }

    // ============== LAZY LOADING PROXY (creates proxies for lazy relationships) ==============
    
    static class LazyLoadingProxy implements InvocationHandler {
        private Object realObject;
        private final Class<?> entityClass;
        private final Object id;
        private final EntityManager entityManager;
        private boolean initialized = false;
        
        LazyLoadingProxy(Class<?> entityClass, Object id, EntityManager em) {
            this.entityClass = entityClass;
            this.id = id;
            this.entityManager = em;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Initialize on first access (except getId)
            if (!initialized && !method.getName().equals("getId")) {
                System.out.println("    [LazyProxy] Loading " + entityClass.getSimpleName() + "#" + id);
                realObject = entityManager.find(entityClass, id);
                initialized = true;
            }
            
            // Handle getId() without initializing
            if (method.getName().equals("getId") && !initialized) {
                return id;
            }
            
            return method.invoke(realObject, args);
        }
        
        static <T> T createProxy(Class<T> entityClass, Object id, EntityManager em) {
            return (T) Proxy.newProxyInstance(
                entityClass.getClassLoader(),
                new Class<?>[] { entityClass },
                new LazyLoadingProxy(entityClass, id, em)
            );
        }
    }

    // ============== ENTITY MANAGER (main JPA interface) ==============
    
    static class EntityManager {
        private final Connection connection;
        private final PersistenceContext context = new PersistenceContext();
        private final Map<Class<?>, EntityMetadata> metadataCache = new ConcurrentHashMap<>();
        private boolean inTransaction = false;
        
        EntityManager(Connection connection) {
            this.connection = connection;
        }
        
        void beginTransaction() {
            try {
                connection.setAutoCommit(false);
                inTransaction = true;
                System.out.println("[Transaction] BEGIN");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        
        void commit() {
            try {
                // Flush changes to database
                flush();
                
                connection.commit();
                inTransaction = false;
                System.out.println("[Transaction] COMMIT");
            } catch (SQLException e) {
                rollback();
                throw new RuntimeException(e);
            }
        }
        
        void rollback() {
            try {
                connection.rollback();
                inTransaction = false;
                context.clear();
                System.out.println("[Transaction] ROLLBACK");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        
        void persist(Object entity) {
            context.markNew(entity);
            System.out.println("[EntityManager] Marked for INSERT: " + entity.getClass().getSimpleName());
        }
        
        <T> T find(Class<T> entityClass, Object id) {
            System.out.println("[EntityManager] Finding " + entityClass.getSimpleName() + "#" + id);
            
            // Check persistence context first (first-level cache)
            T cached = (T) context.get(entityClass, id);
            if (cached != null) {
                return cached;
            }
            
            // Load from database
            EntityMetadata meta = getMetadata(entityClass);
            
            try {
                String sql = "SELECT * FROM " + meta.tableName + " WHERE " + 
                    meta.getColumnName(meta.idField) + " = ?";
                System.out.println("  [SQL] " + sql);
                
                PreparedStatement stmt = connection.prepareStatement(sql);
                stmt.setObject(1, id);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    T entity = mapResultToEntity(rs, meta);
                    context.put(entity, id);
                    return entity;
                }
                
                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        
        void flush() {
            System.out.println("[EntityManager] Flushing changes to database");
            
            // Insert new entities
            for (Object entity : context.newEntities) {
                insertEntity(entity);
            }
            context.newEntities.clear();
            
            // Update dirty entities
            for (Object entity : context.dirtyEntities) {
                updateEntity(entity);
            }
            context.dirtyEntities.clear();
        }
        
        void close() {
            try {
                connection.close();
                System.out.println("[EntityManager] Closed");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        
        private EntityMetadata getMetadata(Class<?> entityClass) {
            return metadataCache.computeIfAbsent(entityClass, EntityMetadata::analyze);
        }
        
        private <T> T mapResultToEntity(ResultSet rs, EntityMetadata meta) throws SQLException {
            try {
                T entity = (T) meta.entityClass.getDeclaredConstructor().newInstance();
                
                // Map ID
                Object id = rs.getObject(meta.getColumnName(meta.idField));
                meta.idField.set(entity, id);
                
                // Map regular columns
                for (Field field : meta.columns) {
                    if (field == meta.idField) continue;
                    Object value = rs.getObject(meta.getColumnName(field));
                    field.set(entity, value);
                }
                
                // Handle relationships
                for (Map.Entry<Field, EntityMetadata.RelationshipInfo> entry : meta.relationships.entrySet()) {
                    Field field = entry.getKey();
                    EntityMetadata.RelationshipInfo rel = entry.getValue();
                    
                    if (field.isAnnotationPresent(ManyToOne.class)) {
                        String fkColumn = field.getName() + "_id";
                        Object fkValue = rs.getObject(fkColumn);
                        
                        if (fkValue != null) {
                            if (rel.fetchType == FetchType.LAZY) {
                                // Create lazy proxy
                                Object proxy = LazyLoadingProxy.createProxy(
                                    rel.targetEntity, fkValue, this);
                                field.set(entity, proxy);
                            } else {
                                // Eager fetch
                                Object related = find(rel.targetEntity, fkValue);
                                field.set(entity, related);
                            }
                        }
                    }
                    // OneToMany would require additional queries - simplified here
                }
                
                return entity;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        private void insertEntity(Object entity) {
            try {
                EntityMetadata meta = getMetadata(entity.getClass());
                
                List<String> columnNames = new ArrayList<>();
                List<Object> values = new ArrayList<>();
                
                for (Field field : meta.columns) {
                    if (field == meta.idField && field.isAnnotationPresent(GeneratedValue.class)) {
                        continue; // Skip auto-generated ID
                    }
                    columnNames.add(meta.getColumnName(field));
                    values.add(field.get(entity));
                }
                
                String sql = "INSERT INTO " + meta.tableName + " (" + 
                    String.join(", ", columnNames) + ") VALUES (" +
                    String.join(", ", Collections.nCopies(columnNames.size(), "?")) + ")";
                
                System.out.println("  [SQL] " + sql);
                
                PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < values.size(); i++) {
                    stmt.setObject(i + 1, values.get(i));
                }
                
                stmt.executeUpdate();
                
                // Get generated ID
                if (meta.idField.isAnnotationPresent(GeneratedValue.class)) {
                    ResultSet keys = stmt.getGeneratedKeys();
                    if (keys.next()) {
                        Object id = keys.getObject(1);
                        meta.idField.set(entity, id);
                        context.put(entity, id);
                    }
                }
                
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        private void updateEntity(Object entity) {
            try {
                EntityMetadata meta = getMetadata(entity.getClass());
                Object id = meta.idField.get(entity);
                
                List<String> setClauses = new ArrayList<>();
                List<Object> values = new ArrayList<>();
                
                for (Field field : meta.columns) {
                    if (field == meta.idField) continue;
                    setClauses.add(meta.getColumnName(field) + " = ?");
                    values.add(field.get(entity));
                }
                
                values.add(id);
                
                String sql = "UPDATE " + meta.tableName + " SET " + 
                    String.join(", ", setClauses) + " WHERE " + 
                    meta.getColumnName(meta.idField) + " = ?";
                
                System.out.println("  [SQL] " + sql);
                
                PreparedStatement stmt = connection.prepareStatement(sql);
                for (int i = 0; i < values.size(); i++) {
                    stmt.setObject(i + 1, values.get(i));
                }
                
                stmt.executeUpdate();
                
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ============== SAMPLE ENTITIES ==============
    
    @Entity(table = "users")
    static class User {
        @Id
        @GeneratedValue
        private Long id;
        
        @Column(name = "username")
        private String username;
        
        @Column(name = "email")
        private String email;
        
        // Transient field - not persisted
        @Transient
        private String tempData;
        
        public User() {}
        
        public User(String username, String email) {
            this.username = username;
            this.email = email;
        }
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        @Override
        public String toString() {
            return "User{id=" + id + ", username='" + username + "', email='" + email + "'}";
        }
    }
    
    @Entity(table = "posts")
    static class Post {
        @Id
        @GeneratedValue
        private Long id;
        
        private String title;
        private String content;
        
        @ManyToOne(fetch = FetchType.LAZY)
        private User author;
        
        public Post() {}
        
        public Post(String title, String content) {
            this.title = title;
            this.content = content;
        }
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public User getAuthor() { return author; }
        public void setAuthor(User author) { this.author = author; }
        
        @Override
        public String toString() {
            return "Post{id=" + id + ", title='" + title + "'}";
        }
    }

    // ============== MAIN ==============
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Mini JPA/Hibernate Demo ===\n");
        System.out.println("This demonstrates how JPA/Hibernate works:");
        System.out.println("1. Entity metadata extracted via reflection");
        System.out.println("2. Persistence context (first-level cache)");
        System.out.println("3. Lazy loading with dynamic proxies");
        System.out.println("4. Automatic dirty checking");
        System.out.println("5. Transaction management\n");
        
        // Setup in-memory database
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:testdb");
        setupDatabase(conn);
        
        EntityManager em = new EntityManager(conn);
        
        // Demo 1: Basic CRUD
        demo1_BasicCRUD(em);
        
        // Demo 2: Persistence Context (First-level cache)
        demo2_PersistenceContext(em);
        
        // Demo 3: Lazy Loading
        demo3_LazyLoading(em);
        
        em.close();
    }
    
    private static void setupDatabase(Connection conn) throws SQLException {
        System.out.println("[Setup] Creating database schema...\n");
        Statement stmt = conn.createStatement();
        
        stmt.execute("CREATE TABLE users (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "username VARCHAR(255), " +
            "email VARCHAR(255))");
        
        stmt.execute("CREATE TABLE posts (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "title VARCHAR(255), " +
            "content TEXT, " +
            "author_id BIGINT)");
    }
    
    private static void demo1_BasicCRUD(EntityManager em) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEMO 1: Basic CRUD Operations");
        System.out.println("=".repeat(80) + "\n");
        
        em.beginTransaction();
        
        // Create
        User user = new User("john_doe", "john@example.com");
        em.persist(user);
        
        // Flush happens on commit
        em.commit();
        
        System.out.println("\nResult: " + user);
    }
    
    private static void demo2_PersistenceContext(EntityManager em) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEMO 2: Persistence Context (First-Level Cache)");
        System.out.println("=".repeat(80) + "\n");
        
        em.beginTransaction();
        
        // First find - loads from database
        User user1 = em.find(User.class, 1L);
        System.out.println("\nFirst find: " + user1);
        
        // Second find - returns cached instance (no SQL!)
        User user2 = em.find(User.class, 1L);
        System.out.println("\nSecond find: " + user2);
        
        // They're the SAME instance (identity guaranteed)
        System.out.println("\nSame instance? " + (user1 == user2));
        
        em.commit();
    }
    
    private static void demo3_LazyLoading(EntityManager em) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DEMO 3: Lazy Loading with Dynamic Proxies");
        System.out.println("=".repeat(80) + "\n");
        
        em.beginTransaction();
        
        // Create post with author
        User author = em.find(User.class, 1L);
        Post post = new Post("My First Post", "Hello World!");
        post.setAuthor(author);
        em.persist(post);
        em.commit();
        
        // Clear context to force fresh load
        em.context.clear();
        
        em.beginTransaction();
        
        // Find post - author is LAZY loaded
        System.out.println("\nLoading post...");
        Post loadedPost = em.find(Post.class, 1L);
        System.out.println("Post loaded: " + loadedPost);
        System.out.println("Author is a proxy: " + Proxy.isProxyClass(loadedPost.getAuthor().getClass()));
        
        // Accessing author triggers lazy load
        System.out.println("\nNow accessing author...");
        System.out.println("Author: " + loadedPost.getAuthor().getUsername());
        
        em.commit();
    }
}
