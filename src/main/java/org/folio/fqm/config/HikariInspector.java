package org.folio.fqm.config;

import com.zaxxer.hikari.HikariDataSource;
import org.folio.spring.config.DataSourceFolioWrapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class HikariInspector {

  private final DataSource dataSource;

  public HikariInspector(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void checkPool() {
    var currentDataSource = (DataSourceFolioWrapper) dataSource;
    var underlyingDataSource = currentDataSource.getTargetDataSource();
    System.out.println("YYZ Checking pool for data source: " + underlyingDataSource.getClass().getName());
    if (underlyingDataSource instanceof HikariDataSource ds) {
      System.out.println("Hikari maxPoolSize: " + ds.getMaximumPoolSize());
      System.out.println("Hikari active connections: " + ds.getHikariPoolMXBean().getActiveConnections());
    }
  }
}
