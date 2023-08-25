package org.folio.fqm.config;

import org.folio.fql.FqlService;
import org.folio.fqm.lib.FQM;
import org.folio.fqm.lib.service.FqlValidationService;
import org.folio.fqm.lib.service.FqmMetaDataService;
import org.folio.fqm.lib.service.QueryProcessorService;
import org.folio.fqm.lib.service.ResultSetService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class BeanConfigurations {

  @Bean
  public FqmMetaDataService fqmMetaDataService(@Qualifier("readerDataSource") DataSource dataSource) {
    return FQM.fqmMetaDataService(dataSource);
  }

  @Bean
  public QueryProcessorService queryProcessorService(@Qualifier("readerDataSource") DataSource dataSource) {
    return FQM.queryProcessorService(dataSource);
  }

  @Bean
  public ResultSetService getResultSetService(@Qualifier("readerDataSource") DataSource dataSource) {
    return FQM.resultSetService(dataSource);
  }

  @Bean
  public FqlValidationService fqlValidationService(@Qualifier("readerDataSource") DataSource dataSource) {
    return FQM.fqlValidationService(dataSource);
  }

  @Bean
  public FqlService fqlService() {
    return FQM.fqlService();
  }
}
