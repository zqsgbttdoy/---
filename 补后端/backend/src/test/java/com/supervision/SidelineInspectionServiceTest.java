package com.supervision;

import com.supervision.dto.SidelineInspectionDTO;
import com.supervision.entity.SidelineInspection;
import com.supervision.exception.BusinessException;
import com.supervision.repository.SidelineInspectionRepository;
import com.supervision.service.SidelineInspectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SidelineInspectionService 单元测试")
class SidelineInspectionServiceTest {

    @Mock
    private SidelineInspectionRepository sidelineInspectionRepository;

    @InjectMocks
    private SidelineInspectionService sidelineInspectionService;

    private SidelineInspectionDTO buildDTO() {
        SidelineInspectionDTO dto = new SidelineInspectionDTO();
        dto.setCode("PZ-20240101");
        dto.setProjectId(1L);
        dto.setInspectionDate(LocalDate.of(2024, 1, 1));
        dto.setInspectorId(1L);
        dto.setLocation("旁站检查位置");
        dto.setContent("旁站检查内容");
        dto.setStartTime(LocalDateTime.of(2024, 1, 1, 8, 0));
        dto.setEndTime(LocalDateTime.of(2024, 1, 1, 17, 0));
        return dto;
    }

    private SidelineInspection buildExisting() {
        SidelineInspection inspection = new SidelineInspection();
        inspection.setId(1L);
        inspection.setCode("PZ-20240101");
        inspection.setProjectId(1L);
        inspection.setInspectionDate(LocalDate.of(2024, 1, 1));
        inspection.setInspectorId(1L);
        inspection.setLocation("旁站检查位置");
        inspection.setContent("旁站检查内容");
        inspection.setStartTime(LocalDateTime.of(2024, 1, 1, 8, 0));
        inspection.setEndTime(LocalDateTime.of(2024, 1, 1, 17, 0));
        inspection.setStatus("draft");
        return inspection;
    }

    @Test
    @DisplayName("创建旁站检查 - 验证默认status=draft")
    void testCreateSidelineInspection() {
        SidelineInspectionDTO dto = buildDTO();
        when(sidelineInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(sidelineInspectionRepository.save(any(SidelineInspection.class))).thenAnswer(invocation -> {
            SidelineInspection inspection = invocation.getArgument(0);
            inspection.setId(1L);
            return inspection;
        });

        SidelineInspection result = sidelineInspectionService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
        assertEquals("PZ-20240101", result.getCode());
        assertEquals(1L, result.getProjectId());
        assertEquals("旁站检查位置", result.getLocation());
        assertEquals(LocalDateTime.of(2024, 1, 1, 8, 0), result.getStartTime());
        assertEquals(LocalDateTime.of(2024, 1, 1, 17, 0), result.getEndTime());
        verify(sidelineInspectionRepository).save(any(SidelineInspection.class));
    }

    @Test
    @DisplayName("创建旁站检查 - 编号已存在应抛出BusinessException")
    void testCreateWithDuplicateCode() {
        SidelineInspectionDTO dto = buildDTO();
        SidelineInspection existing = buildExisting();
        when(sidelineInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sidelineInspectionService.create(dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(sidelineInspectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建旁站检查 - 编号存在但status为inactive时允许创建")
    void testCreateWithInactiveCode() {
        SidelineInspectionDTO dto = buildDTO();
        SidelineInspection inactiveInspection = buildExisting();
        inactiveInspection.setStatus("inactive");
        when(sidelineInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.of(inactiveInspection));
        when(sidelineInspectionRepository.save(any(SidelineInspection.class))).thenAnswer(invocation -> {
            SidelineInspection inspection = invocation.getArgument(0);
            inspection.setId(2L);
            return inspection;
        });

        SidelineInspection result = sidelineInspectionService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
    }

    @Test
    @DisplayName("更新旁站检查 - 正常更新")
    void testUpdateSidelineInspection() {
        SidelineInspection existing = buildExisting();
        when(sidelineInspectionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sidelineInspectionRepository.save(any(SidelineInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SidelineInspectionDTO dto = buildDTO();
        dto.setLocation("更新后的位置");
        dto.setContent("更新后的内容");

        SidelineInspection result = sidelineInspectionService.update(1L, dto);

        assertEquals("更新后的位置", result.getLocation());
        assertEquals("更新后的内容", result.getContent());
        verify(sidelineInspectionRepository).save(any(SidelineInspection.class));
    }

    @Test
    @DisplayName("更新旁站检查 - ID不存在应抛出BusinessException")
    void testUpdateNonExistent() {
        when(sidelineInspectionRepository.findById(999L)).thenReturn(Optional.empty());

        SidelineInspectionDTO dto = buildDTO();
        assertThrows(BusinessException.class, () -> sidelineInspectionService.update(999L, dto));
        verify(sidelineInspectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("删除旁站检查 - 应设置status为inactive")
    void testDeleteSidelineInspection() {
        SidelineInspection inspection = buildExisting();
        when(sidelineInspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));
        when(sidelineInspectionRepository.save(any(SidelineInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sidelineInspectionService.delete(1L);

        verify(sidelineInspectionRepository).save(argThat(i -> "inactive".equals(i.getStatus())));
    }

    @Test
    @DisplayName("根据ID查询 - 旁站检查不存在应抛出BusinessException")
    void testGetByIdNotFound() {
        when(sidelineInspectionRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sidelineInspectionService.getById(999L));
        assertEquals("旁站检查记录不存在", exception.getMessage());
    }

    @Test
    @DisplayName("创建旁站检查 - DTO传入status=recorded时应设置status为recorded")
    void testCreateWithDTOStatus() {
        SidelineInspectionDTO dto = buildDTO();
        dto.setStatus("recorded");
        when(sidelineInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(sidelineInspectionRepository.save(any(SidelineInspection.class))).thenAnswer(invocation -> {
            SidelineInspection inspection = invocation.getArgument(0);
            inspection.setId(1L);
            return inspection;
        });

        SidelineInspection result = sidelineInspectionService.create(dto);

        assertEquals("recorded", result.getStatus());
    }

    @Test
    @DisplayName("创建旁站检查 - DTO传入status=null时应fallback为draft")
    void testCreateWithNullDTOStatus() {
        SidelineInspectionDTO dto = buildDTO();
        dto.setStatus(null);
        when(sidelineInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(sidelineInspectionRepository.save(any(SidelineInspection.class))).thenAnswer(invocation -> {
            SidelineInspection inspection = invocation.getArgument(0);
            inspection.setId(1L);
            return inspection;
        });

        SidelineInspection result = sidelineInspectionService.create(dto);

        assertEquals("draft", result.getStatus());
    }

    @Test
    @DisplayName("更新旁站检查 - 新code已被其他active记录占用应抛出BusinessException")
    void testUpdateWithDuplicateCode() {
        SidelineInspection existing = buildExisting();
        SidelineInspection otherRecord = buildExisting();
        otherRecord.setId(2L);
        otherRecord.setCode("PZ-OTHER");
        when(sidelineInspectionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sidelineInspectionRepository.findByCode("PZ-OTHER")).thenReturn(Optional.of(otherRecord));

        SidelineInspectionDTO dto = buildDTO();
        dto.setCode("PZ-OTHER");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sidelineInspectionService.update(1L, dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(sidelineInspectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新旁站检查 - 新code仅被inactive记录占用时允许更新")
    void testUpdateWithInactiveDuplicateCode() {
        SidelineInspection existing = buildExisting();
        SidelineInspection inactiveRecord = buildExisting();
        inactiveRecord.setId(2L);
        inactiveRecord.setCode("PZ-OTHER");
        inactiveRecord.setStatus("inactive");
        when(sidelineInspectionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sidelineInspectionRepository.findByCode("PZ-OTHER")).thenReturn(Optional.of(inactiveRecord));
        when(sidelineInspectionRepository.save(any(SidelineInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SidelineInspectionDTO dto = buildDTO();
        dto.setCode("PZ-OTHER");

        SidelineInspection result = sidelineInspectionService.update(1L, dto);

        assertEquals("PZ-OTHER", result.getCode());
        verify(sidelineInspectionRepository).save(any());
    }

    @Test
    @DisplayName("更新旁站检查 - 新code与自身原code相同允许更新")
    void testUpdateWithSameCode() {
        SidelineInspection existing = buildExisting();
        when(sidelineInspectionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sidelineInspectionRepository.save(any(SidelineInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SidelineInspectionDTO dto = buildDTO();
        dto.setLocation("新位置");

        SidelineInspection result = sidelineInspectionService.update(1L, dto);

        assertEquals("新位置", result.getLocation());
        verify(sidelineInspectionRepository).save(any());
        verify(sidelineInspectionRepository, never()).findByCode(any());
    }
}