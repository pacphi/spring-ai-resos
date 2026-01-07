package me.pacphi.ai.resos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration class that ensures proper ordering between SchemaCreator and Liquibase.
 * <p>
 * The critical requirement is that SchemaCreator must complete its @PostConstruct
 * (which generates changelogs) BEFORE SpringLiquibase initializes (which reads them).
 * <p>
 * This is achieved by:
 * <ol>
 *   <li>Using BeanFactoryPostProcessor to add @DependsOn to SpringLiquibase bean definition</li>
 *   <li>SpringLiquibase will wait for SchemaCreator to fully initialize first</li>
 *   <li>SchemaCreator's @PostConstruct runs and sets up changelogs</li>
 *   <li>LiquibaseCustomizer then configures the changelog location</li>
 * </ol>
 *
 * @see SchemaCreator
 * @see LiquibaseCustomizer
 */
@Configuration
@Profile({"dev", "test"})
public class LiquibaseConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LiquibaseConfiguration.class);

    /**
     * BeanFactoryPostProcessor that ensures SpringLiquibase depends on SchemaCreator.
     * <p>
     * This runs before any beans are instantiated, modifying the bean definitions
     * to add a @DependsOn relationship. This guarantees that:
     * <ul>
     *   <li>SchemaCreator is fully initialized (including @PostConstruct)</li>
     *   <li>System property is set with temp directory path</li>
     *   <li>Changelogs are generated and ready to use</li>
     * </ul>
     * before SpringLiquibase attempts to read them.
     */
    @Bean
    static BeanFactoryPostProcessor liquibaseDependencyEnforcer() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                // Find all SpringLiquibase bean definitions
                String[] liquibaseBeanNames = beanFactory.getBeanNamesForType(
                        liquibase.integration.spring.SpringLiquibase.class, true, false);

                if (liquibaseBeanNames.length == 0) {
                    // Liquibase might be defined by name only (auto-configuration)
                    if (beanFactory.containsBeanDefinition("liquibase")) {
                        addDependsOn(beanFactory, "liquibase");
                    } else {
                        logger.debug("No SpringLiquibase bean found - skipping dependency enforcement");
                    }
                } else {
                    for (String beanName : liquibaseBeanNames) {
                        addDependsOn(beanFactory, beanName);
                    }
                }
            }

            private void addDependsOn(ConfigurableListableBeanFactory beanFactory, String beanName) {
                if (beanFactory instanceof BeanDefinitionRegistry registry) {
                    if (registry.containsBeanDefinition(beanName)) {
                        var beanDef = registry.getBeanDefinition(beanName);

                        // Get existing dependencies
                        String[] existingDeps = beanDef.getDependsOn();
                        String[] newDeps;

                        if (existingDeps == null || existingDeps.length == 0) {
                            newDeps = new String[]{"schemaCreator"};
                        } else {
                            // Add schemaCreator if not already present
                            boolean hasSchemaCreator = false;
                            for (String dep : existingDeps) {
                                if ("schemaCreator".equals(dep)) {
                                    hasSchemaCreator = true;
                                    break;
                                }
                            }
                            if (hasSchemaCreator) {
                                return; // Already depends on schemaCreator
                            }
                            newDeps = new String[existingDeps.length + 1];
                            System.arraycopy(existingDeps, 0, newDeps, 0, existingDeps.length);
                            newDeps[existingDeps.length] = "schemaCreator";
                        }

                        beanDef.setDependsOn(newDeps);
                        logger.info("Added @DependsOn('schemaCreator') to bean '{}' - " +
                                "ensuring changelogs are generated before Liquibase runs", beanName);
                    }
                }
            }
        };
    }
}
