package me.pacphi.ai.resos.config;

import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Customizes the auto-configured SpringLiquibase bean to use temp directory changelogs
 * when running from JAR (Docker containers).
 * <p>
 * This BeanPostProcessor intercepts SpringLiquibase bean initialization and redirects
 * it to use file-based changelogs from the temp directory when the system property
 * {@link SchemaCreator#CHANGELOG_DIR_PROPERTY} is set.
 * <p>
 * <b>How it works:</b>
 * <ol>
 *   <li>SchemaCreator generates changelogs and sets system property</li>
 *   <li>This customizer detects the property during SpringLiquibase initialization</li>
 *   <li>Redirects Liquibase to use file: prefix and FileSystemResourceLoader</li>
 * </ol>
 * <p>
 * <b>Important:</b> This relies on SchemaCreator's @PostConstruct running before
 * SpringLiquibase is initialized. This ordering is ensured by LiquibaseConfiguration
 * which uses @DependsOn annotation.
 *
 * @see SchemaCreator
 * @see LiquibaseConfiguration
 */
@Component
public class LiquibaseCustomizer implements BeanPostProcessor, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LiquibaseCustomizer.class);

    @Override
    public int getOrder() {
        // Run early to catch SpringLiquibase before it initializes
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SpringLiquibase liquibase) {
            customizeLiquibase(liquibase, beanName);
        }
        return bean;
    }

    private void customizeLiquibase(SpringLiquibase liquibase, String beanName) {
        // Check if SchemaCreator set a temp directory (JAR execution)
        String tempDirPath = System.getProperty(SchemaCreator.CHANGELOG_DIR_PROPERTY);

        if (tempDirPath != null) {
            Path changelogFile = Path.of(tempDirPath, "db/changelog/db.changelog-master.yml");

            // Verify the changelog file exists
            if (!changelogFile.toFile().exists()) {
                logger.warn("Temp changelog file does not exist yet: {}. " +
                        "This may indicate SchemaCreator hasn't run. " +
                        "Ensure @DependsOn ordering is correct.", changelogFile);
                // Still proceed - SchemaCreator might create it
            }

            logger.info("Customizing Liquibase bean '{}' for JAR execution", beanName);
            logger.info("  - Setting ResourceLoader: FileSystemResourceLoader");
            logger.info("  - Setting change-log: file:{}", changelogFile);

            // Use FileSystemResourceLoader for file: prefix support
            liquibase.setResourceLoader(new FileSystemResourceLoader());

            // Use file: prefix for absolute filesystem paths
            liquibase.setChangeLog("file:" + changelogFile.toString());

            logger.info("Liquibase customization complete");
        } else {
            logger.debug("No temp directory set - using classpath changelogs");
        }
    }
}
