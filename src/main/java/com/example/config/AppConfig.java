package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

/**
 * Main Spring configuration class for the application.
 * 
 * <p>Configures:
 * <ul>
 *   <li>Component scanning for all com.example packages</li>
 *   <li>JdbcClient for database access</li>
 *   <li>Transaction management</li>
 *   <li>Web MVC setup</li>
 * </ul>
 */
@Configuration
@EnableWebMvc
@EnableTransactionManagement
@ComponentScan(basePackages = "com.example")
public class AppConfig implements WebMvcConfigurer {
    
    /**
     * Creates the JdbcClient bean from the DataSource.
     * 
     * <p>JdbcClient is Spring 6's modern, fluent API for JDBC access.
     * It provides a cleaner alternative to JdbcTemplate with better
     * type safety and method chaining.</p>
     *
     * @param dataSource the configured DataSource
     * @return the JdbcClient instance
     */
    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
    
    /**
     * Transaction manager for JDBC operations.
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
