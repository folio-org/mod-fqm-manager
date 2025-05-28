package org.folio.fqm.context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

@Component
@Profile("db-test")
public class TestcontainerBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
  private static final int POSTGRES_PORT = 5432;

  @SuppressWarnings("resource")
  @Container
  public static PostgreSQLContainer<?> dbContainer = new PostgreSQLContainer<>("postgres:16-alpine")
      .withStartupAttempts(3);

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    dbContainer.start();

    System.setProperty("DB_HOST", dbContainer.getHost());
    System.setProperty("DB_PORT", "" + dbContainer.getMappedPort(POSTGRES_PORT));
    System.setProperty("DB_HOST_READER", dbContainer.getHost());
    System.setProperty("DB_PORT_READER", "" + dbContainer.getMappedPort(POSTGRES_PORT));
    System.setProperty("DB_DATABASE", dbContainer.getDatabaseName());
    System.setProperty("DB_USERNAME", dbContainer.getUsername());
    System.setProperty("DB_PASSWORD", dbContainer.getPassword());
  }
}
