package com.hackaton.ulibre.checklist;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmacionItemChecklistRepository extends JpaRepository<ConfirmacionItemChecklist, UUID> {
}
