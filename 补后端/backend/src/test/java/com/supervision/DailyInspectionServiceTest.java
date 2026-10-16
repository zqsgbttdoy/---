package com.supervision;

import com.supervision.dto.DailyInspectionDTO;
import com.supervision.entity.DailyInspection;
import com.supervision.exception.BusinessException;
import com.supervision.repository.DailyInspectionRepository;
import com.supervision.service.DailyInspectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyInspectionService 单元测试")
class DailyInspectionServiceTest {

    @Mock
    private DailyInspectionRepository dailyInspectionRepository;

    @InjectMocks
    private DailyInspectionService dailyInspectionService;

    private DailyInspectionDTO buildDTO() {
        DailyInspectionDTO dto = new DailyInspectionDTO();
        dto.setCode("RC-20240101");
        dto.setProjectId(1L);
        dto.setInspectionDate(LocalDate.of(2024, 1, 1));
        dto.setInspectorId(1L);
        dto.setLocation("施工现场A区");
        dto.setContent("巡视内容");
        dto.setIssues("发现安全隐患");
        return dto;
    }

    private DailyInspection buildExisting() {
        DailyInspection inspection = new DailyInspection();
        inspection.setId(1L);
        inspection.setCode("RC-20240101");
        inspection.setProjectId(1L);
        inspection.setInspectionDate(LocalDate.of(2024, 1, 1));
        inspection.setInspectorId(1L);
        inspection.setLocation("施工现场A区");
        inspection.setContent("巡视内容");
        inspection.setIssues("发现安全隐患");
        inspection.setStatus("draft");
        return inspection;
    }

    @Test
    @DisplayName("创建日常巡视 - 验证默认status=draft")
    void testCreateDailyInspection() {
        DailyInspectionDTO dto = buildDTO();
        when(dailyInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(dailyInspectionRepository.save(any(DailyInspection.class))).thenAnswer(invocation -> {
            DailyInspection inspection = invocation.getArgument(0);
            inspection.setId(1L);
            return inspection;
        });

        DailyInspection result = dailyInspectionService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
        assertEquals("RC-20240101", result.getCode());
        assertEquals(1L, result.getProjectId());
        assertEquals("施工现场A区", result.getLocation());
        verify(dailyInspectionRepository).save(any(DailyInspection.class));
    }

    @Test
    @DisplayName("创建日常巡视 - 编号已存在应抛出BusinessException")
    void testCreateWithDuplicateCode() {
        DailyInspectionDTO dto = buildDTO();
        DailyInspection existing = buildExisting();
        when(dailyInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dailyInspectionService.create(dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(dailyInspectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建日常巡视 - 编号存在但status为inactive时允许创建")
    void testCreateWithInactiveCode() {
        DailyInspectionDTO dto = buildDTO();
        DailyInspection inactiveInspection = buildExisting();
        inactiveInspection.setStatus("inactive");
        when(dailyInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.of(inactiveInspection));
        when(dailyInspectionRepository.save(any(DailyInspection.class))).thenAnswer(invocation -> {
            DailyInspection inspection = invocation.getArgument(0);
            inspection.setId(2L);
            return inspection;
        });

        DailyInspection result = dailyInspectionService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
    }

    @Test
    @DisplayName("更新日常巡视 - 正常更新")
    void testUpdateDailyInspection() {
        DailyInspection existing = buildExisting();
        when(dailyInspectionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dailyInspectionRepository.save(any(DailyInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyInspectionDTO dto = buildDTO();
        dto.setLocation("施工现场B区");
        dto.setContent("更新后的巡视内容");

        DailyInspection result = dailyInspectionService.update(1L, dto);

        assertEquals("施工现场B区", result.getLocation());
        assertEquals("更新后的巡视内容", result.getContent());
        verify(dailyInspectionRepository).save(any(DailyInspection.class));
    }

    @Test
    @DisplayName("更新日常巡视 - ID不存在应抛出BusinessException")
    void testUpdateNonExistent() {
        when(dailyInspectionRepository.findById(999L)).thenReturn(Optional.empty());

        DailyInspectionDTO dto = buildDTO();
        assertThrows(BusinessException.class, () -> dailyInspectionService.update(999L, dto));
        verify(dailyInspectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("删除日常巡视 - 应设置status为inactive")
    void testDeleteDailyInspection() {
        DailyInspection inspection = buildExisting();
        when(dailyInspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));
        when(dailyInspectionRepository.save(any(DailyInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        dailyInspectionService.delete(1L);

        verify(dailyInspectionRepository).save(argThat(i -> "inactive".equals(i.getStatus())));
    }

    @Test
    @DisplayName("根据ID查询 - 日常巡视不存在应抛出BusinessException")
    void testGetByIdNotFound() {
        when(dailyInspectionRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dailyInspectionService.getById(999L));
        assertEquals("日常巡视记录不存在", exception.getMessage());
    }

    @Test
    @DisplayName("统计 - 应排除inactive记录")
    void testStatisticsExcludeInactive() {
        DailyInspection draft1 = buildExisting();
        draft1.setStatus("draft");
        DailyInspection recorded1 = buildExisting();
        recorded1.setId(2L);
        recorded1.setStatus("recorded");
        DailyInspection fixed1 = buildExisting();
        fixed1.setId(3L);
        fixed1.setStatus("fixed");
        DailyInspection inactive1 = buildExisting();
        inactive1.setId(4L);
        inactive1.setStatus("inactive");

        when(dailyInspectionRepository.findByProjectId(1L)).thenReturn(List.of(draft1, recorded1, fixed1, inactive1));

        Map<String, Object> stats = dailyInspectionService.statistics(1L);

        assertEquals(3L, stats.get("total"));  // 应排除inactive
        assertEquals(1L, stats.get("draft"));
        assertEquals(1L, stats.get("recorded"));
        assertEquals(1L, stats.get("fixed"));
    }

    @Test
    @DisplayName("统计 - 全部为active记录")
    void testStatisticsAllActive() {
        DailyInspection draft1 = buildExisting();
        draft1.setStatus("draft");
        DailyInspection draft2 = buildExisting();
        draft2.setId(2L);
        draft2.setStatus("draft");

        when(dailyInspectionRepository.findByProjectId(1L)).thenReturn(List.of(draft1, draft2));

        Map<String, Object> stats = dailyInspectionService.statistics(1L);

        assertEquals(2L, stats.get("total"));
        assertEquals(2L, stats.get("draft"));
        assertEquals(0L, stats.get("recorded"));
        assertEquals(0L, stats.get("fixed"));
    }

    @Test
    @DisplayName("创建日常巡视 - DTO传入status=recorded时应设置status为recorded")
    void testCreateWithDTOStatus() {
        DailyInspectionDTO dto = buildDTO();
        dto.setStatus("recorded");
        when(dailyInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(dailyInspectionRepository.save(any(DailyInspection.class))).thenAnswer(invocation -> {
            DailyInspection inspection = invocation.getArgument(0);
            inspection.setId(1L);
            return inspection;
        });

        DailyInspection result = dailyInspectionService.create(dto);

        assertEquals("recorded", result.getStatus());
    }

    @Test
    @DisplayName("创建日常巡视 - DTO传入status=null时应fallback为draft")
    void testCreateWithNullDTOStatus() {
        DailyInspectionDTO dto = buildDTO();
        dto.setStatus(null);
        when(dailyInspectionRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(dailyInspectionRepository.save(any(DailyInspection.class))).thenAnswer(invocation -> {
            DailyInspection inspection = invocation.getArgument(0);
            inspection.setId(1L);
            return inspection;
        });

        DailyInspection result = dailyInspectionService.create(dto);

        assertEquals("draft", result.getStatus());
    }

    @Test
    @DisplayName("更新日常巡视 - 新code已被其他active记录占用应抛出BusinessException")
    void testUpdateWithDuplicateCode() {
        DailyInspection existing = buildExisting();
        DailyInspection otherRecord = buildExisting();
        otherRecord.setId(2L);
        otherRecord.setCode("RC-OTHER");
        when(dailyInspectionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dailyInspectionRepository.findByCode("RC-OTHER")).thenReturn(Optional.of(otherRecord));

        DailyInspectionDTO dto = buildDTO();
        dto.setCode("RC-OTHER");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> dailyInspectionService.update(1L, dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(dailyInspectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新日常巡视 - 新code仅被inactive记录占用时允许更新")
    void testUpdateWithInactiveDuplicateCode() {
        DailyInspection existing = buildExisting();
        DailyInspection inactiveRecord = buildExisting();
        inactiveRecord.setId(2L);
        inactiveRecord.setCode("RC-OTHER");
        inactiveRecord.setStatus("inactive");
        when(dailyInspectionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dailyInspectionRepository.findByCode("RC-OTHER")).thenReturn(Optional.of(inactiveRecord));
        when(dailyInspectionRepository.save(any(DailyInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyInspectionDTO dto = buildDTO();
        dto.setCode("RC-OTHER");

        DailyInspection result = dailyInspectionService.update(1L, dto);

        assertEquals("RC-OTHER", result.getCode());
        verify(dailyInspectionRepository).save(any());
    }

    @Test
    @DisplayName("更新日常巡视 - 新code与自身原code相同允许更新")
    void testUpdateWithSameCode() {
        DailyInspection existing = buildExisting();
        when(dailyInspectionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dailyInspectionRepository.save(any(DailyInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyInspectionDTO dto = buildDTO();
        dto.setLocation("新位置");

        DailyInspection result = dailyInspectionService.update(1L, dto);

        assertEquals("新位置", result.getLocation());
        verify(dailyInspectionRepository).save(any());
        verify(dailyInspectionRepository, never()).findByCode(any());
    }
}