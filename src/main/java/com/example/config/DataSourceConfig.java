package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * DataSource configuration.
 * 
 * <p>Configure your database connection properties in application.properties
 * or override this configuration for your specific database.</p>
 */
@Configuration
@PropertySource(value = "classpath:application.properties", ignoreResourceNotFound = true)
public class DataSourceConfig {
    
    @Value("${jdbc.driver:org.h2.Driver}")
    private String driverClassName;
    
    @Value("${jdbc.url:jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1}")
    private String url;
    
    @Value("${jdbc.username:sa}")
    private String username;
    
    @Value("${jdbc.password:}")
    private String password;
    
    @Value("${jdbc.initSchema:true}")
    private boolean initSchema;
    
    /**
     * Creates the DataSource bean and initializes the schema.
     * 
     * <p>For production use, consider using a connection pool like HikariCP.</p>
     */
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        
        // Initialize schema with sample data if enabled
        if (initSchema) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema.sql"));
            populator.setContinueOnError(true);
            DatabasePopulatorUtils.execute(populator, dataSource);
        }
        
        return dataSource;
    }
}
