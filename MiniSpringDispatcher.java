///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

/**
 * Demonstrates how Spring Boot's DispatcherServlet works using pure Java
 * Shows: annotations, reflection, handler mapping, and method invocation
 */
public class MiniSpringDispatcher {

    // ============== ANNOTATIONS (like Spring's @RestController, @GetMapping) ==============
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface RestController {
        String path() default "";
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface GetMapping {
        String value();
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface PostMapping {
        String value();
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface PathVariable {
        String value();
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface RequestBody {
    }

    // ============== REQUEST/RESPONSE OBJECTS ==============
    
    static class HttpRequest {
        String method;
        String path;
        Map<String, String> headers = new HashMap<>();
        String body;
        
        HttpRequest(String method, String path) {
            this.method = method;
            this.path = path;
        }
        
        HttpRequest body(String body) {
            this.body = body;
            return this;
        }
    }
    
    static class HttpResponse {
        int status = 200;
        String body;
        Map<String, String> headers = new HashMap<>();
        
        HttpResponse(String body) {
            this.body = body;
        }
        
        HttpResponse status(int status) {
            this.status = status;
            return this;
        }
    }

    // ============== HANDLER MAPPING (finds the right controller method) ==============
    
    static class HandlerMapping {
        private final List<MappedHandler> handlers = new ArrayList<>();
        
        static class MappedHandler {
            Pattern urlPattern;
            String httpMethod;
            Object controller;
            Method method;
            List<String> pathVariables;
            
            MappedHandler(String url, String httpMethod, Object controller, Method method) {
                this.httpMethod = httpMethod;
                this.controller = controller;
                this.method = method;
                this.pathVariables = new ArrayList<>();
                
                // Convert URL pattern like "/users/{id}" to regex
                String regex = url.replaceAll("\\{([^/]+)\\}", "(?<$1>[^/]+)");
                this.urlPattern = Pattern.compile("^" + regex + "$");
                
                // Extract path variable names
                Matcher m = Pattern.compile("\\{([^/]+)\\}").matcher(url);
                while (m.find()) {
                    pathVariables.add(m.group(1));
                }
            }
            
            boolean matches(String method, String path) {
                return this.httpMethod.equals(method) && urlPattern.matcher(path).matches();
            }
            
            Map<String, String> extractPathVariables(String path) {
                Map<String, String> vars = new HashMap<>();
                Matcher m = urlPattern.matcher(path);
                if (m.matches()) {
                    for (String varName : pathVariables) {
                        vars.put(varName, m.group(varName));
                    }
                }
                return vars;
            }
        }
        
        void registerController(Object controller) {
            Class<?> clazz = controller.getClass();
            
            // Get base path from @RestController
            String basePath = "";
            if (clazz.isAnnotationPresent(RestController.class)) {
                basePath = clazz.getAnnotation(RestController.class).path();
            }
            
            // Scan all methods for @GetMapping, @PostMapping
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(GetMapping.class)) {
                    String path = basePath + method.getAnnotation(GetMapping.class).value();
                    handlers.add(new MappedHandler(path, "GET", controller, method));
                }
                if (method.isAnnotationPresent(PostMapping.class)) {
                    String path = basePath + method.getAnnotation(PostMapping.class).value();
                    handlers.add(new MappedHandler(path, "POST", controller, method));
                }
            }
        }
        
        MappedHandler findHandler(String method, String path) {
            return handlers.stream()
                .filter(h -> h.matches(method, path))
                .findFirst()
                .orElse(null);
        }
    }

    // ============== HANDLER ADAPTER (invokes the controller method) ==============
    
    static class HandlerAdapter {
        
        Object invokeHandler(HandlerMapping.MappedHandler handler, HttpRequest request) 
                throws Exception {
            Method method = handler.method;
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            
            Map<String, String> pathVars = handler.extractPathVariables(request.path);
            
            // Resolve method arguments
            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                
                // Handle @PathVariable
                if (param.isAnnotationPresent(PathVariable.class)) {
                    String varName = param.getAnnotation(PathVariable.class).value();
                    String value = pathVars.get(varName);
                    
                    // Type conversion
                    args[i] = convertValue(value, param.getType());
                }
                // Handle @RequestBody
                else if (param.isAnnotationPresent(RequestBody.class)) {
                    args[i] = request.body;
                }
                // Handle HttpRequest injection
                else if (param.getType().equals(HttpRequest.class)) {
                    args[i] = request;
                }
            }
            
            // Invoke the controller method
            return method.invoke(handler.controller, args);
        }
        
        private Object convertValue(String value, Class<?> targetType) {
            if (targetType == String.class) return value;
            if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
            if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
            return value;
        }
    }

    // ============== DISPATCHER SERVLET (orchestrates everything) ==============
    
    static class DispatcherServlet {
        private final HandlerMapping handlerMapping = new HandlerMapping();
        private final HandlerAdapter handlerAdapter = new HandlerAdapter();
        
        void registerController(Object controller) {
            handlerMapping.registerController(controller);
        }
        
        HttpResponse dispatch(HttpRequest request) {
            try {
                System.out.println("\n[DispatcherServlet] Processing: " + request.method + " " + request.path);
                
                // Step 1: Find handler
                System.out.println("[HandlerMapping] Looking for handler...");
                HandlerMapping.MappedHandler handler = handlerMapping.findHandler(request.method, request.path);
                
                if (handler == null) {
                    System.out.println("[HandlerMapping] No handler found!");
                    return new HttpResponse("404 Not Found").status(404);
                }
                
                System.out.println("[HandlerMapping] Found: " + 
                    handler.controller.getClass().getSimpleName() + "." + handler.method.getName() + "()");
                
                // Step 2: Invoke handler
                System.out.println("[HandlerAdapter] Resolving arguments and invoking...");
                Object result = handlerAdapter.invokeHandler(handler, request);
                
                // Step 3: Convert result to response
                System.out.println("[MessageConverter] Converting result to response");
                String responseBody = (result != null) ? result.toString() : "";
                
                return new HttpResponse(responseBody);
                
            } catch (Exception e) {
                System.out.println("[ExceptionHandler] Caught exception: " + e.getMessage());
                return new HttpResponse("500 Internal Server Error: " + e.getMessage()).status(500);
            }
        }
    }

    // ============== SAMPLE CONTROLLERS ==============
    
    @RestController(path = "/api")
    static class UserController {
        
        @GetMapping("/users/{id}")
        public String getUser(@PathVariable("id") Long id) {
            return "{\"id\": " + id + ", \"name\": \"User " + id + "\"}";
        }
        
        @GetMapping("/users/{id}/posts/{postId}")
        public String getUserPost(@PathVariable("id") Long userId, @PathVariable("postId") Long postId) {
            return "{\"userId\": " + userId + ", \"postId\": " + postId + ", \"title\": \"Post " + postId + "\"}";
        }
        
        @PostMapping("/users")
        public String createUser(@RequestBody String body) {
            return "{\"created\": true, \"data\": " + body + "}";
        }
    }
    
    @RestController
    static class HealthController {
        
        @GetMapping("/health")
        public String health() {
            return "{\"status\": \"UP\"}";
        }
    }

    // ============== MAIN ==============
    
    public static void main(String[] args) {
        System.out.println("=== Mini Spring Boot Dispatcher Demo ===\n");
        System.out.println("This demonstrates how Spring Boot's DispatcherServlet works:");
        System.out.println("1. Annotations define routes (@GetMapping, @PostMapping)");
        System.out.println("2. HandlerMapping finds the right controller method using reflection");
        System.out.println("3. HandlerAdapter resolves arguments and invokes the method");
        System.out.println("4. Result is converted to HTTP response\n");
        
        // Create dispatcher and register controllers
        DispatcherServlet dispatcher = new DispatcherServlet();
        dispatcher.registerController(new UserController());
        dispatcher.registerController(new HealthController());
        
        // Simulate HTTP requests
        testRequest(dispatcher, new HttpRequest("GET", "/health"));
        testRequest(dispatcher, new HttpRequest("GET", "/api/users/123"));
        testRequest(dispatcher, new HttpRequest("GET", "/api/users/42/posts/7"));
        testRequest(dispatcher, new HttpRequest("POST", "/api/users").body("{\"name\": \"John Doe\"}"));
        testRequest(dispatcher, new HttpRequest("GET", "/api/users/not-a-number"));
        testRequest(dispatcher, new HttpRequest("GET", "/unknown"));
    }
    
    private static void testRequest(DispatcherServlet dispatcher, HttpRequest request) {
        HttpResponse response = dispatcher.dispatch(request);
        System.out.println("[Response] Status: " + response.status);
        System.out.println("[Response] Body: " + response.body);
        System.out.println("─".repeat(80));
    }
}
