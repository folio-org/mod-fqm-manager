package org.folio.fqm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.folio.fqm.service.EntityTypeInitializationService;
import org.folio.spring.liquibase.FolioLiquibaseConfiguration;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.folio.tenant.rest.resource.TenantApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles({ "test", "db-test" })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModFqmManagerApplicationTest {

  @EnableAutoConfiguration(exclude = { FolioLiquibaseConfiguration.class })
  @RestController("folioTenantController")
  @Profile("test")
  static class TestTenantController implements TenantApi {

    @Autowired
    private EntityTypeInitializationService entityTypeInitializationService;

    @Override
    public ResponseEntity<Void> deleteTenant(String operationId) {
      return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> postTenant(@Valid TenantAttributes tenantAttributes) {
      try {
        entityTypeInitializationService.initializeEntityTypes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return ResponseEntity.status(HttpStatus.CREATED).build();
    }
  }

  @Test
  void shouldAnswerWithTrue() {
    assertTrue(true);
  }
}
