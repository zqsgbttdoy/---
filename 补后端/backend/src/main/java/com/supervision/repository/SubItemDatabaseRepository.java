package com.supervision.repository;

import com.supervision.entity.SubItemDatabase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SubItemDatabaseRepository extends JpaRepository<SubItemDatabase, Long>, JpaSpecificationExecutor<SubItemDatabase> {

    List<SubItemDatabase> findByProjectId(Long projectId);

    List<SubItemDatabase> findByParentId(Long parentId);
}
