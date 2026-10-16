package com.supervision;

import com.supervision.dto.SupervisionMonthlyReportDTO;
import com.supervision.entity.SupervisionMonthlyReport;
import com.supervision.exception.BusinessException;
import com.supervision.repository.SupervisionMonthlyReportRepository;
import com.supervision.service.SupervisionMonthlyReportService;
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
@DisplayName("SupervisionMonthlyReportService 单元测试")
class SupervisionMonthlyReportServiceTest {

    @Mock
    private SupervisionMonthlyReportRepository supervisionMonthlyReportRepository;

    @Mock private com.supervision.service.ProjectAccessService access;
    @Mock private com.supervision.service.AttachmentAccessService attachmentAccess;

    @org.junit.jupiter.api.BeforeEach
    void identity() {
        var user = new com.supervision.entity.User();
        user.setId(1L);
        lenient().when(access.currentUser()).thenReturn(user);
    }

    @InjectMocks
    private SupervisionMonthlyReportService supervisionMonthlyReportService;

    @Test
    void deniedProjectCannotCreate() {
        doThrow(new BusinessException(403, "无项目权限")).when(access).requireRead(1L);
        assertThrows(BusinessException.class, () -> supervisionMonthlyReportService.create(buildDTO()));
        verifyNoInteractions(supervisionMonthlyReportRepository, attachmentAccess);
    }

    private SupervisionMonthlyReportDTO buildDTO() {
        SupervisionMonthlyReportDTO dto = new SupervisionMonthlyReportDTO();
        dto.setCode("YB-202401");
        dto.setProjectId(1L);
        dto.setReportMonth("2024-01");
        dto.setAuthorId(1L);
        dto.setContent("月报内容");
        return dto;
    }

    private SupervisionMonthlyReport buildExisting() {
        SupervisionMonthlyReport report = new SupervisionMonthlyReport();
        report.setId(1L);
        report.setCode("YB-202401");
        report.setProjectId(1L);
        report.setReportMonth("2024-01");
        report.setAuthorId(1L);
        report.setContent("月报内容");
        report.setStatus("draft");
        return report;
    }

    @Test
    @DisplayName("创建监理月报 - 验证默认status=draft")
    void testCreateMonthlyReport() {
        SupervisionMonthlyReportDTO dto = buildDTO();
        when(supervisionMonthlyReportRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(supervisionMonthlyReportRepository.save(any(SupervisionMonthlyReport.class))).thenAnswer(invocation -> {
            SupervisionMonthlyReport report = invocation.getArgument(0);
            report.setId(1L);
            return report;
        });

        SupervisionMonthlyReport result = supervisionMonthlyReportService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
        assertEquals("YB-202401", result.getCode());
        assertEquals("2024-01", result.getReportMonth());
        verify(supervisionMonthlyReportRepository).save(any(SupervisionMonthlyReport.class));
    }

    @Test
    @DisplayName("创建监理月报 - 编号已存在应抛出BusinessException")
    void testCreateWithDuplicateCode() {
        SupervisionMonthlyReportDTO dto = buildDTO();
        SupervisionMonthlyReport existing = buildExisting();
        when(supervisionMonthlyReportRepository.findByCode(dto.getCode())).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> supervisionMonthlyReportService.create(dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(supervisionMonthlyReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建监理月报 - 编号存在但status为inactive时允许创建")
    void testCreateWithInactiveCode() {
        SupervisionMonthlyReportDTO dto = buildDTO();
        SupervisionMonthlyReport inactiveReport = buildExisting();
        inactiveReport.setStatus("inactive");
        when(supervisionMonthlyReportRepository.findByCode(dto.getCode())).thenReturn(Optional.of(inactiveReport));
        when(supervisionMonthlyReportRepository.save(any(SupervisionMonthlyReport.class))).thenAnswer(invocation -> {
            SupervisionMonthlyReport report = invocation.getArgument(0);
            report.setId(2L);
            return report;
        });

        SupervisionMonthlyReport result = supervisionMonthlyReportService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
    }

    @Test
    @DisplayName("更新监理月报 - 正常更新")
    void testUpdateMonthlyReport() {
        SupervisionMonthlyReport existing = buildExisting();
        when(supervisionMonthlyReportRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supervisionMonthlyReportRepository.save(any(SupervisionMonthlyReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupervisionMonthlyReportDTO dto = buildDTO();
        dto.setContent("更新后的内容");

        SupervisionMonthlyReport result = supervisionMonthlyReportService.update(1L, dto);

        assertEquals("更新后的内容", result.getContent());
        verify(supervisionMonthlyReportRepository).save(any(SupervisionMonthlyReport.class));
    }

    @Test
    @DisplayName("更新监理月报 - ID不存在应抛出BusinessException")
    void testUpdateNonExistent() {
        when(supervisionMonthlyReportRepository.findById(999L)).thenReturn(Optional.empty());

        SupervisionMonthlyReportDTO dto = buildDTO();
        assertThrows(BusinessException.class, () -> supervisionMonthlyReportService.update(999L, dto));
        verify(supervisionMonthlyReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新监理月报 - 新code已被其他active记录占用应抛出BusinessException")
    void testUpdateWithDuplicateCode() {
        SupervisionMonthlyReport existing = buildExisting();
        SupervisionMonthlyReport otherRecord = buildExisting();
        otherRecord.setId(2L);
        otherRecord.setCode("YB-OTHER");
        when(supervisionMonthlyReportRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supervisionMonthlyReportRepository.findByCode("YB-OTHER")).thenReturn(Optional.of(otherRecord));

        SupervisionMonthlyReportDTO dto = buildDTO();
        dto.setCode("YB-OTHER");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> supervisionMonthlyReportService.update(1L, dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(supervisionMonthlyReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新监理月报 - 新code仅被inactive记录占用时允许更新")
    void testUpdateWithInactiveDuplicateCode() {
        SupervisionMonthlyReport existing = buildExisting();
        SupervisionMonthlyReport inactiveRecord = buildExisting();
        inactiveRecord.setId(2L);
        inactiveRecord.setCode("YB-OTHER");
        inactiveRecord.setStatus("inactive");
        when(supervisionMonthlyReportRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supervisionMonthlyReportRepository.findByCode("YB-OTHER")).thenReturn(Optional.of(inactiveRecord));
        when(supervisionMonthlyReportRepository.save(any(SupervisionMonthlyReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupervisionMonthlyReportDTO dto = buildDTO();
        dto.setCode("YB-OTHER");

        SupervisionMonthlyReport result = supervisionMonthlyReportService.update(1L, dto);

        assertEquals("YB-OTHER", result.getCode());
        verify(supervisionMonthlyReportRepository).save(any());
    }

    @Test
    @DisplayName("更新监理月报 - 新code与自身原code相同允许更新")
    void testUpdateWithSameCode() {
        SupervisionMonthlyReport existing = buildExisting();
        when(supervisionMonthlyReportRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supervisionMonthlyReportRepository.save(any(SupervisionMonthlyReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupervisionMonthlyReportDTO dto = buildDTO();
        dto.setContent("新内容");

        SupervisionMonthlyReport result = supervisionMonthlyReportService.update(1L, dto);

        assertEquals("新内容", result.getContent());
        verify(supervisionMonthlyReportRepository).save(any());
        verify(supervisionMonthlyReportRepository, never()).findByCode(any());
    }

    @Test
    @DisplayName("删除监理月报 - 应设置status为inactive")
    void testDeleteMonthlyReport() {
        SupervisionMonthlyReport report = buildExisting();
        when(supervisionMonthlyReportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(supervisionMonthlyReportRepository.save(any(SupervisionMonthlyReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        supervisionMonthlyReportService.delete(1L);

        verify(supervisionMonthlyReportRepository).save(argThat(r -> "inactive".equals(r.getStatus())));
    }

    @Test
    @DisplayName("根据ID查询 - 监理月报不存在应抛出BusinessException")
    void testGetByIdNotFound() {
        when(supervisionMonthlyReportRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> supervisionMonthlyReportService.getById(999L));
        assertEquals("监理月报不存在", exception.getMessage());
    }
}
