package org.folio.fqm.service;

import static org.folio.fqm.repository.EntityTypeRepository.ID_FIELD_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.fqm.repository.ResultSetRepository;
import org.folio.fqm.testutil.TestDataFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultSetServiceTest {

  private ResultSetRepository resultSetRepository;
  private ResultSetService service;

  @BeforeEach
  void setUp() {
    this.resultSetRepository = mock(ResultSetRepository.class);
    this.service = new ResultSetService(resultSetRepository);
  }

  @Test
  void shouldGetResultSet() {
    String tenantId = "tenant_01";
    UUID entityTypeId = UUID.randomUUID();
    List<Map<String, Object>> expectedResult = TestDataFixture.getEntityContents();
    List<Map<String, Object>> reversedContent = Lists.reverse(expectedResult);
    List<String> fields = List.of("id", "key1", "key2");
    List<UUID> listIds = new ArrayList<>();
    expectedResult.forEach(content ->
      listIds.add((UUID) content.get(ID_FIELD_NAME))
    );
    when(
      resultSetRepository.getResultSet(tenantId, entityTypeId, fields, listIds)
    )
      .thenReturn(reversedContent);
    List<Map<String, Object>> actualResult = service.getResultSet(
      tenantId,
      entityTypeId,
      fields,
      listIds
    );
    assertEquals(expectedResult, actualResult);
  }
}
