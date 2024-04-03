package org.folio.fqm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;

import org.folio.spring.liquibase.FolioLiquibaseConfiguration;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.folio.tenant.rest.resource.TenantApi;

@Log4j2
@ActiveProfiles({"test", "db-test"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModFqmManagerApplicationTest {

  @EnableAutoConfiguration(exclude = {FolioLiquibaseConfiguration.class})
  @RestController("folioTenantController")
  @Profile("test")
  static class TestTenantController implements TenantApi {

    @Override
    public ResponseEntity<Void> deleteTenant(String operationId) {
      return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> postTenant(@Valid TenantAttributes tenantAttributes) {
      log.info("============================");
      log.info("POST TENANT HELLO");
      log.info("============================");
      return ResponseEntity.status(HttpStatus.CREATED).build();
    }
  }

  @BeforeAll
  static void setUp() {
    log.info("============================");
    log.info("SETUP HELLO");
    log.info("============================");
  }

  @AfterAll
  static void after() {
    log.info("============================");
    log.info("AFTER HELLO");
    log.info("============================");
  }

  @Test
  void shouldAnswerWithTrue() {
    assertTrue(true);
  }
}
