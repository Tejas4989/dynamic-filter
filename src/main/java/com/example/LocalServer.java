package com.example;

import com.example.config.AppConfig;
import com.example.config.DataSourceConfig;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Embedded Jetty server for local development and testing.
 * 
 * Run with: mvn compile exec:java
 * Or run this main class directly from IDE.
 */
public class LocalServer {
    
    private static final int PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        Server server = new Server(PORT);
        
        // Create Spring application context
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(AppConfig.class, DataSourceConfig.class);
        
        // Create DispatcherServlet
        DispatcherServlet dispatcherServlet = new DispatcherServlet(context);
        
        // Setup Jetty servlet context
        ServletContextHandler servletHandler = new ServletContextHandler();
        servletHandler.setContextPath("/");
        servletHandler.addServlet(new ServletHolder(dispatcherServlet), "/*");
        
        server.setHandler(servletHandler);
        
        // Initialize database with sample data
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Starting Dynamic Filter POC Server on port " + PORT);
        System.out.println("═══════════════════════════════════════════════════════════════");
        
        server.start();
        
        System.out.println();
        System.out.println("  Server started successfully!");
        System.out.println();
        System.out.println("  Try these API calls:");
        System.out.println();
        System.out.println("  # Get all users");
        System.out.println("  curl http://localhost:8080/api/v1/users");
        System.out.println();
        System.out.println("  # Filter by firstName starts with 'J'");
        System.out.println("  curl \"http://localhost:8080/api/v1/users?filter=firstName:sw:J\"");
        System.out.println();
        System.out.println("  # Filter by lastName equals 'Doe'");
        System.out.println("  curl \"http://localhost:8080/api/v1/users?filter=lastName:eq:Doe\"");
        System.out.println();
        System.out.println("  # Filter with IN clause (roleIds in 1,2)");
        System.out.println("  curl \"http://localhost:8080/api/v1/users?filter=roleIds:in:(1,2)\"");
        System.out.println();
        System.out.println("  # Multiple filters + sort");
        System.out.println("  curl \"http://localhost:8080/api/v1/users?filter=firstName:sw:J,lastName:notnull&sort=lastName:asc\"");
        System.out.println();
        System.out.println("  # Pagination");
        System.out.println("  curl \"http://localhost:8080/api/v1/users?limit=2&offset=0\"");
        System.out.println();
        System.out.println("  # Get available fields");
        System.out.println("  curl http://localhost:8080/api/v1/users/metadata/fields");
        System.out.println();
        System.out.println("  # Get user by ID");
        System.out.println("  curl http://localhost:8080/api/v1/users/1");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Press Ctrl+C to stop the server");
        System.out.println("═══════════════════════════════════════════════════════════════");
        
        server.join();
    }
}
