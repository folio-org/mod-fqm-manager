package org.folio.fqm.config;

import org.folio.fqm.repository.QueryRepository;
import org.folio.fqm.repository.QueryResultsRepository;
import org.folio.fqm.service.DataBatchCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

@Configuration
public class FqmConfiguration {
  @Bean
  public Supplier<DataBatchCallback> dataBatchCallbackSupplier(QueryRepository queryRepository,
                                                               QueryResultsRepository queryResultsRepository) {
    return () -> new DataBatchCallback(queryRepository, queryResultsRepository);
  }
}
