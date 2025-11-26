package org.folio.fqm.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.fqm.repository.SourceViewRepository;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectSelectStep;
import org.jooq.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class SourceViewServiceTest {

  @Mock
  private SourceViewRepository sourceViewRepository;

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @Mock
  private ResourcePatternResolver resourceResolver;

  @Mock
  private DSLContext jooqContext;

  @InjectMocks
  private SourceViewService sourceViewService;

  @BeforeEach
  void setup() {
    lenient().when(folioExecutionContext.getTenantId()).thenReturn("tenantId");
  }

  @SuppressWarnings("unchecked")
  private void mockFetchOne(boolean shouldHaveResult) {
    SelectSelectStep<Record1<Integer>> selection = mock(SelectSelectStep.class);
    when(jooqContext.selectOne()).thenReturn(selection);
    SelectJoinStep<Record1<Integer>> selection2 = mock(SelectJoinStep.class);
    when(selection.from(any(Table.class))).thenReturn(selection2);
    SelectConditionStep<Record1<Integer>> selection3 = mock(SelectConditionStep.class);
    when(selection2.where(any(List.class))).thenReturn(selection3);

    if (shouldHaveResult) {
      when(selection3.fetchOne()).thenReturn(mock(Record1.class));
    } else {
      when(selection3.fetchOne()).thenReturn(null);
    }
  }
}
