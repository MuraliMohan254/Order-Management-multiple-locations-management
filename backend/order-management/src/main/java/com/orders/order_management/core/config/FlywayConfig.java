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
                .validateOnMigrate(false)
                .load();
        var result = flyway.migrate();
        log.info("Flyway migration completed successfully. Applied {} migrations.", result.migrationsExecuted);

        // Ensure idempotent schema compatibility on existing databases before JPA validates
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE IF EXISTS orders ADD COLUMN IF NOT EXISTS billed_by VARCHAR(100);");
            stmt.execute("ALTER TABLE IF EXISTS payments ADD COLUMN IF NOT EXISTS settled_by VARCHAR(100);");
            stmt.execute("ALTER TABLE IF EXISTS payments ADD COLUMN IF NOT EXISTS settled_by_id BIGINT;");
            stmt.execute("ALTER TABLE IF EXISTS system_config ADD COLUMN IF NOT EXISTS gst_number VARCHAR(50);");
            stmt.execute("ALTER TABLE IF EXISTS system_config ADD COLUMN IF NOT EXISTS printer_ip VARCHAR(50) DEFAULT '192.168.31.53';");
            stmt.execute("ALTER TABLE IF EXISTS system_config ADD COLUMN IF NOT EXISTS printer_port INTEGER DEFAULT 9100;");
            stmt.execute("ALTER TABLE IF EXISTS system_config ADD COLUMN IF NOT EXISTS quick_bill_print_receipt BOOLEAN DEFAULT true;");
            stmt.execute("ALTER TABLE IF EXISTS order_items ADD COLUMN IF NOT EXISTS batch_number INTEGER NOT NULL DEFAULT 1;");
        } catch (Exception e) {
            log.warn("Notice during schema self-healing: {}", e.getMessage());
        }

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
