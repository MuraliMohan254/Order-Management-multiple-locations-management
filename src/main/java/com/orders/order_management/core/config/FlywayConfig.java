package com.orders.order_management.core.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public Flyway flyway(DataSource dataSource) {
        log.info("Starting Flyway database migration...");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        var result = flyway.migrate();
        log.info("Flyway migration completed successfully. Applied {} migrations.", result.migrationsExecuted);
        return flyway;
    }

    @Bean
    public static BeanFactoryPostProcessor dependsOnFlywayPostProcessor() {
        return beanFactory -> {
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                if ("entityManagerFactory".equals(beanName) || beanName.endsWith("EntityManagerFactory")) {
                    var bd = beanFactory.getBeanDefinition(beanName);
                    String[] dependsOn = bd.getDependsOn();
                    if (dependsOn == null || dependsOn.length == 0) {
                        bd.setDependsOn("flyway");
                    } else {
                        String[] newDependsOn = new String[dependsOn.length + 1];
                        System.arraycopy(dependsOn, 0, newDependsOn, 0, dependsOn.length);
                        newDependsOn[dependsOn.length] = "flyway";
                        bd.setDependsOn(newDependsOn);
                    }
                }
            }
        };
    }
}
