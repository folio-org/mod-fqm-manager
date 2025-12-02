package org.folio.fqm.repository;

import org.folio.fqm.domain.SourceViewRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceViewRecordRepository extends JpaRepository<SourceViewRecord, String> {}
