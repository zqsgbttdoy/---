package com.supervision;

import com.supervision.dto.WorkStandardConfigDTO;
import com.supervision.entity.WorkStandardConfig;
import com.supervision.exception.BusinessException;
import com.supervision.repository.WorkStandardConfigRepository;
import com.supervision.service.WorkStandardConfigService;
import com.supervision.service.ProjectAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkStandardConfigService 单元测试")
class WorkStandardConfigServiceTest {

    @Mock
    private WorkStandardConfigRepository workStandardConfigRepository;

    @Mock
    private ProjectAccessService access;

    @InjectMocks
    private WorkStandardConfigService workStandardConfigService;

    private WorkStandardConfigDTO buildDTO() {
        WorkStandardConfigDTO dto = new WorkStandardConfigDTO();
        dto.setName("监理日志标准");
        dto.setModule("supervision_log");
        dto.setStandard("JL-STD-001");
        dto.setDescription("监理日志编写标准规范");
        return dto;
    }

    private WorkStandardConfig buildExisting() {
        WorkStandardConfig config = new WorkStandardConfig();
        config.setId(1L);
        config.setName("监理日志标准");
        config.setModule("supervision_log");
        config.setStandard("JL-STD-001");
        config.setDescription("监理日志编写标准规范");
        config.setStatus("active");
        return config;
    }

    @Test
    @DisplayName("创建工作标准配置 - 验证默认status=active")
    void testCreateWorkStandardConfig() {
        when(access.isAdmin()).thenReturn(true);
        WorkStandardConfigDTO dto = buildDTO();
        when(workStandardConfigRepository.save(any(WorkStandardConfig.class))).thenAnswer(invocation -> {
            WorkStandardConfig config = invocation.getArgument(0);
            config.setId(1L);
            return config;
        });

        WorkStandardConfig result = workStandardConfigService.create(dto);

        assertNotNull(result);
        assertEquals("active", result.getStatus());
        assertEquals("监理日志标准", result.getName());
        assertEquals("supervision_log", result.getModule());
        assertEquals("JL-STD-001", result.getStandard());
        assertEquals("监理日志编写标准规范", result.getDescription());
        verify(workStandardConfigRepository).save(any(WorkStandardConfig.class));
    }

    @Test
    @DisplayName("更新工作标准配置 - 正常更新")
    void testUpdateWorkStandardConfig() {
        when(access.isAdmin()).thenReturn(true);
        WorkStandardConfig existing = buildExisting();
        when(workStandardConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workStandardConfigRepository.save(any(WorkStandardConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkStandardConfigDTO dto = buildDTO();
        dto.setName("更新后的标准名称");
        dto.setDescription("更新后的描述");

        WorkStandardConfig result = workStandardConfigService.update(1L, dto);

        assertEquals("更新后的标准名称", result.getName());
        assertEquals("更新后的描述", result.getDescription());
        verify(workStandardConfigRepository).save(any(WorkStandardConfig.class));
    }

    @Test
    @DisplayName("更新工作标准配置 - ID不存在应抛出BusinessException")
    void testUpdateNonExistent() {
        when(access.isAdmin()).thenReturn(true);
        when(workStandardConfigRepository.findById(999L)).thenReturn(Optional.empty());

        WorkStandardConfigDTO dto = buildDTO();
        assertThrows(BusinessException.class, () -> workStandardConfigService.update(999L, dto));
        verify(workStandardConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("删除工作标准配置 - 应设置status为inactive")
    void testDeleteWorkStandardConfig() {
        when(access.isAdmin()).thenReturn(true);
        WorkStandardConfig config = buildExisting();
        when(workStandardConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(workStandardConfigRepository.save(any(WorkStandardConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        workStandardConfigService.delete(1L);

        verify(workStandardConfigRepository).save(argThat(c -> "inactive".equals(c.getStatus())));
    }

    @Test
    @DisplayName("非管理员的新增、更新和删除均在访问配置前拒绝")
    void testWritesRejectNonAdministrator() {
        assertEquals(403, assertThrows(BusinessException.class, () -> workStandardConfigService.create(buildDTO())).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> workStandardConfigService.update(1L, buildDTO())).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> workStandardConfigService.delete(1L)).getCode());
        verifyNoInteractions(workStandardConfigRepository);
    }

    @Test
    @DisplayName("根据ID查询 - 工作标准配置不存在应抛出BusinessException")
    void testGetByIdNotFound() {
        when(workStandardConfigRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> workStandardConfigService.getById(999L));
        assertEquals("工作标准配置不存在", exception.getMessage());
    }
}