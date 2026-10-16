package com.supervision;

import com.supervision.dto.ApproveDTO;
import com.supervision.dto.SupervisionDiaryDTO;
import com.supervision.entity.SupervisionDiary;
import com.supervision.exception.BusinessException;
import com.supervision.repository.SupervisionDiaryRepository;
import com.supervision.service.SupervisionDiaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupervisionDiaryService 单元测试")
class SupervisionDiaryServiceTest {

    @Mock
    private SupervisionDiaryRepository supervisionDiaryRepository;

    @InjectMocks
    private SupervisionDiaryService supervisionDiaryService;

    private SupervisionDiaryDTO buildDiaryDTO() {
        SupervisionDiaryDTO dto = new SupervisionDiaryDTO();
        dto.setCode("JL-RJ-20240101");
        dto.setProjectId(1L);
        dto.setDiaryDate(LocalDate.of(2024, 1, 1));
        dto.setAuthorId(1L);
        dto.setWeather("晴");
        dto.setWind("微风");
        dto.setTemperature("25℃");
        dto.setConstruction("施工内容");
        dto.setManagement("管理内容");
        dto.setInspection("检查内容");
        return dto;
    }

    private SupervisionDiary buildExistingDiary() {
        SupervisionDiary diary = new SupervisionDiary();
        diary.setId(1L);
        diary.setCode("JL-RJ-20240101");
        diary.setProjectId(1L);
        diary.setDiaryDate(LocalDate.of(2024, 1, 1));
        diary.setAuthorId(1L);
        diary.setWeather("晴");
        diary.setWind("微风");
        diary.setTemperature("25℃");
        diary.setConstruction("施工内容");
        diary.setManagement("管理内容");
        diary.setInspection("检查内容");
        diary.setStatus("draft");
        diary.setVersion(1);
        return diary;
    }

    @Test
    @DisplayName("创建日记 - 验证默认status=draft和version=1")
    void testCreateDiary() {
        SupervisionDiaryDTO dto = buildDiaryDTO();
        when(supervisionDiaryRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(supervisionDiaryRepository.save(any(SupervisionDiary.class))).thenAnswer(invocation -> {
            SupervisionDiary diary = invocation.getArgument(0);
            diary.setId(1L);
            return diary;
        });

        SupervisionDiary result = supervisionDiaryService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
        assertEquals(1, result.getVersion());
        assertEquals("JL-RJ-20240101", result.getCode());
        assertEquals(1L, result.getProjectId());
        verify(supervisionDiaryRepository).save(any(SupervisionDiary.class));
    }

    @Test
    @DisplayName("创建日记 - 编号已存在应抛出BusinessException")
    void testCreateDiaryWithDuplicateCode() {
        SupervisionDiaryDTO dto = buildDiaryDTO();
        SupervisionDiary existing = buildExistingDiary();
        when(supervisionDiaryRepository.findByCode(dto.getCode())).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> supervisionDiaryService.create(dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(supervisionDiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建日记 - 编号存在但status为inactive时允许创建")
    void testCreateDiaryWithInactiveCode() {
        SupervisionDiaryDTO dto = buildDiaryDTO();
        SupervisionDiary inactiveDiary = buildExistingDiary();
        inactiveDiary.setStatus("inactive");
        when(supervisionDiaryRepository.findByCode(dto.getCode())).thenReturn(Optional.of(inactiveDiary));
        when(supervisionDiaryRepository.save(any(SupervisionDiary.class))).thenAnswer(invocation -> {
            SupervisionDiary diary = invocation.getArgument(0);
            diary.setId(2L);
            return diary;
        });

        SupervisionDiary result = supervisionDiaryService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
    }

    @Test
    @DisplayName("审核通过 - 验证status=approved")
    void testApproveDiary() {
        SupervisionDiary diary = buildExistingDiary();
        diary.setRejectReason("之前驳回原因");
        when(supervisionDiaryRepository.findById(1L)).thenReturn(Optional.of(diary));
        when(supervisionDiaryRepository.save(any(SupervisionDiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApproveDTO dto = new ApproveDTO();
        dto.setId(1L);
        dto.setStatus("approved");

        SupervisionDiary result = supervisionDiaryService.approve(dto);

        assertEquals("approved", result.getStatus());
        assertNull(result.getRejectReason());
        verify(supervisionDiaryRepository).save(any(SupervisionDiary.class));
    }

    @Test
    @DisplayName("审核驳回 - 验证status=rejected和rejectReason")
    void testRejectDiary() {
        SupervisionDiary diary = buildExistingDiary();
        when(supervisionDiaryRepository.findById(1L)).thenReturn(Optional.of(diary));
        when(supervisionDiaryRepository.save(any(SupervisionDiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApproveDTO dto = new ApproveDTO();
        dto.setId(1L);
        dto.setStatus("rejected");
        dto.setRejectReason("内容不完整");

        SupervisionDiary result = supervisionDiaryService.approve(dto);

        assertEquals("rejected", result.getStatus());
        assertEquals("内容不完整", result.getRejectReason());
        verify(supervisionDiaryRepository).save(any(SupervisionDiary.class));
    }

    @Test
    @DisplayName("审核 - status为null时应抛出BusinessException")
    void testApproveWithNullStatus() {
        ApproveDTO dto = new ApproveDTO();
        dto.setId(1L);
        dto.setStatus(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> supervisionDiaryService.approve(dto));
        assertEquals("审核状态不能为空", exception.getMessage());
        verify(supervisionDiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("审核 - approved状态不可再次审批应抛出BusinessException")
    void testApproveAlreadyApproved() {
        SupervisionDiary diary = buildExistingDiary();
        diary.setStatus("approved");
        when(supervisionDiaryRepository.findById(1L)).thenReturn(Optional.of(diary));

        ApproveDTO dto = new ApproveDTO();
        dto.setId(1L);
        dto.setStatus("approved");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> supervisionDiaryService.approve(dto));
        assertEquals("只有草稿或已提交状态的日记才能审批", exception.getMessage());
        verify(supervisionDiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("审核 - submitted状态可审批通过")
    void testApproveSubmittedDiary() {
        SupervisionDiary diary = buildExistingDiary();
        diary.setStatus("submitted");
        when(supervisionDiaryRepository.findById(1L)).thenReturn(Optional.of(diary));
        when(supervisionDiaryRepository.save(any(SupervisionDiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApproveDTO dto = new ApproveDTO();
        dto.setId(1L);
        dto.setStatus("approved");

        SupervisionDiary result = supervisionDiaryService.approve(dto);

        assertEquals("approved", result.getStatus());
        verify(supervisionDiaryRepository).save(any());
    }

    @Test
    @DisplayName("更新日记 - 新code已被其他active记录占用应抛出BusinessException")
    void testUpdateWithDuplicateCode() {
        SupervisionDiary existing = buildExistingDiary();
        SupervisionDiary otherRecord = buildExistingDiary();
        otherRecord.setId(2L);
        otherRecord.setCode("JL-RJ-OTHER");
        when(supervisionDiaryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supervisionDiaryRepository.findByCode("JL-RJ-OTHER")).thenReturn(Optional.of(otherRecord));

        SupervisionDiaryDTO dto = buildDiaryDTO();
        dto.setCode("JL-RJ-OTHER");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> supervisionDiaryService.update(1L, dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(supervisionDiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新日记 - 新code仅被inactive记录占用时允许更新")
    void testUpdateWithInactiveDuplicateCode() {
        SupervisionDiary existing = buildExistingDiary();
        SupervisionDiary inactiveRecord = buildExistingDiary();
        inactiveRecord.setId(2L);
        inactiveRecord.setCode("JL-RJ-OTHER");
        inactiveRecord.setStatus("inactive");
        when(supervisionDiaryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supervisionDiaryRepository.findByCode("JL-RJ-OTHER")).thenReturn(Optional.of(inactiveRecord));
        when(supervisionDiaryRepository.save(any(SupervisionDiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupervisionDiaryDTO dto = buildDiaryDTO();
        dto.setCode("JL-RJ-OTHER");

        SupervisionDiary result = supervisionDiaryService.update(1L, dto);

        assertEquals("JL-RJ-OTHER", result.getCode());
        verify(supervisionDiaryRepository).save(any());
    }

    @Test
    @DisplayName("更新日记 - 新code与自身原code相同允许更新")
    void testUpdateWithSameCode() {
        SupervisionDiary existing = buildExistingDiary();
        when(supervisionDiaryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supervisionDiaryRepository.save(any(SupervisionDiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupervisionDiaryDTO dto = buildDiaryDTO();
        dto.setWeather("雨");

        SupervisionDiary result = supervisionDiaryService.update(1L, dto);

        assertEquals("雨", result.getWeather());
        verify(supervisionDiaryRepository).save(any());
        verify(supervisionDiaryRepository, never()).findByCode(any());
    }
}
