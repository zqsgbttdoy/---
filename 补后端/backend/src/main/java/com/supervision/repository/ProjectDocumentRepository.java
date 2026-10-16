package com.supervision.repository;

import com.supervision.entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long>, JpaSpecificationExecutor<ProjectDocument> {

    List<ProjectDocument> findByProjectId(Long projectId);

    List<ProjectDocument> findByProjectIdAndSubItemId(Long projectId, Long subItemId);
}
