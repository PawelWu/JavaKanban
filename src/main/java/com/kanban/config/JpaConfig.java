package com.kanban.config;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

@Configuration
@EnableJpaRepositories(basePackages = "com.kanban.repository")
@EntityScan(basePackages = "com.kanban.entity")
public class JpaConfig {

    // Rely on Spring Boot's auto-configured DataSource (HikariCP with single-conn for SQLite)
    // Just customize Hibernate properties here.

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.kanban.entity");

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        props.put("hibernate.hbm2ddl.auto", "update");
        // Critical for SQLite: allow Hibernate to prepare statements via JDBC directly
        props.put("hibernate.order_inserts", true);
        props.put("hibernate.order_updates", true);
        em.setJpaPropertyMap(props);

        return em;
    }
}
