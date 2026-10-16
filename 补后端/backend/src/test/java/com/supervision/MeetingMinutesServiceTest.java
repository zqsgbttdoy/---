package com.supervision;

import com.supervision.dto.MeetingMinutesDTO;
import com.supervision.entity.MeetingMinutes;
import com.supervision.exception.BusinessException;
import com.supervision.repository.MeetingMinutesRepository;
import com.supervision.service.MeetingMinutesService;
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
@DisplayName("MeetingMinutesService 单元测试")
class MeetingMinutesServiceTest {

    @Mock
    private MeetingMinutesRepository meetingMinutesRepository;

    @Mock private com.supervision.service.ProjectAccessService access;
    @Mock private com.supervision.service.AttachmentAccessService attachmentAccess;

    @org.junit.jupiter.api.BeforeEach
    void identity() {
        var user = new com.supervision.entity.User();
        user.setId(1L);
        lenient().when(access.currentUser()).thenReturn(user);
    }

    @InjectMocks
    private MeetingMinutesService meetingMinutesService;

    @Test
    void deniedProjectCannotCreate() {
        doThrow(new BusinessException(403, "无项目权限")).when(access).requireRead(1L);
        assertThrows(BusinessException.class, () -> meetingMinutesService.create(buildDTO()));
        verifyNoInteractions(meetingMinutesRepository, attachmentAccess);
    }

    private MeetingMinutesDTO buildDTO() {
        MeetingMinutesDTO dto = new MeetingMinutesDTO();
        dto.setCode("HY-20240101");
        dto.setProjectId(1L);
        dto.setMeetingDate(LocalDate.of(2024, 1, 1));
        dto.setAuthorId(1L);
        dto.setTitle("监理例会");
        dto.setContent("会议内容");
        dto.setAttendees("张三、李四、王五");
        dto.setLocation("项目部会议室");
        return dto;
    }

    private MeetingMinutes buildExisting() {
        MeetingMinutes minutes = new MeetingMinutes();
        minutes.setId(1L);
        minutes.setCode("HY-20240101");
        minutes.setProjectId(1L);
        minutes.setMeetingDate(LocalDate.of(2024, 1, 1));
        minutes.setAuthorId(1L);
        minutes.setTitle("监理例会");
        minutes.setContent("会议内容");
        minutes.setAttendees("张三、李四、王五");
        minutes.setLocation("项目部会议室");
        minutes.setStatus("draft");
        return minutes;
    }

    @Test
    @DisplayName("创建会议纪要 - 验证默认status=draft")
    void testCreateMeetingMinutes() {
        MeetingMinutesDTO dto = buildDTO();
        when(meetingMinutesRepository.findByCode(dto.getCode())).thenReturn(Optional.empty());
        when(meetingMinutesRepository.save(any(MeetingMinutes.class))).thenAnswer(invocation -> {
            MeetingMinutes minutes = invocation.getArgument(0);
            minutes.setId(1L);
            return minutes;
        });

        MeetingMinutes result = meetingMinutesService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
        assertEquals("HY-20240101", result.getCode());
        assertEquals("监理例会", result.getTitle());
        verify(meetingMinutesRepository).save(any(MeetingMinutes.class));
    }

    @Test
    @DisplayName("创建会议纪要 - 编号已存在应抛出BusinessException")
    void testCreateWithDuplicateCode() {
        MeetingMinutesDTO dto = buildDTO();
        MeetingMinutes existing = buildExisting();
        when(meetingMinutesRepository.findByCode(dto.getCode())).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> meetingMinutesService.create(dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(meetingMinutesRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建会议纪要 - 编号存在但status为inactive时允许创建")
    void testCreateWithInactiveCode() {
        MeetingMinutesDTO dto = buildDTO();
        MeetingMinutes inactiveMinutes = buildExisting();
        inactiveMinutes.setStatus("inactive");
        when(meetingMinutesRepository.findByCode(dto.getCode())).thenReturn(Optional.of(inactiveMinutes));
        when(meetingMinutesRepository.save(any(MeetingMinutes.class))).thenAnswer(invocation -> {
            MeetingMinutes minutes = invocation.getArgument(0);
            minutes.setId(2L);
            return minutes;
        });

        MeetingMinutes result = meetingMinutesService.create(dto);

        assertNotNull(result);
        assertEquals("draft", result.getStatus());
    }

    @Test
    @DisplayName("更新会议纪要 - 正常更新")
    void testUpdateMeetingMinutes() {
        MeetingMinutes existing = buildExisting();
        when(meetingMinutesRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(meetingMinutesRepository.save(any(MeetingMinutes.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeetingMinutesDTO dto = buildDTO();
        dto.setTitle("更新后的标题");

        MeetingMinutes result = meetingMinutesService.update(1L, dto);

        assertEquals("更新后的标题", result.getTitle());
        verify(meetingMinutesRepository).save(any(MeetingMinutes.class));
    }

    @Test
    @DisplayName("更新会议纪要 - ID不存在应抛出BusinessException")
    void testUpdateNonExistent() {
        when(meetingMinutesRepository.findById(999L)).thenReturn(Optional.empty());

        MeetingMinutesDTO dto = buildDTO();
        assertThrows(BusinessException.class, () -> meetingMinutesService.update(999L, dto));
        verify(meetingMinutesRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新会议纪要 - 新code已被其他active记录占用应抛出BusinessException")
    void testUpdateWithDuplicateCode() {
        MeetingMinutes existing = buildExisting();
        MeetingMinutes otherRecord = buildExisting();
        otherRecord.setId(2L);
        otherRecord.setCode("HY-OTHER");
        when(meetingMinutesRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(meetingMinutesRepository.findByCode("HY-OTHER")).thenReturn(Optional.of(otherRecord));

        MeetingMinutesDTO dto = buildDTO();
        dto.setCode("HY-OTHER");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> meetingMinutesService.update(1L, dto));
        assertEquals("编号已存在", exception.getMessage());
        verify(meetingMinutesRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新会议纪要 - 新code仅被inactive记录占用时允许更新")
    void testUpdateWithInactiveDuplicateCode() {
        MeetingMinutes existing = buildExisting();
        MeetingMinutes inactiveRecord = buildExisting();
        inactiveRecord.setId(2L);
        inactiveRecord.setCode("HY-OTHER");
        inactiveRecord.setStatus("inactive");
        when(meetingMinutesRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(meetingMinutesRepository.findByCode("HY-OTHER")).thenReturn(Optional.of(inactiveRecord));
        when(meetingMinutesRepository.save(any(MeetingMinutes.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeetingMinutesDTO dto = buildDTO();
        dto.setCode("HY-OTHER");

        MeetingMinutes result = meetingMinutesService.update(1L, dto);

        assertEquals("HY-OTHER", result.getCode());
        verify(meetingMinutesRepository).save(any());
    }

    @Test
    @DisplayName("更新会议纪要 - 新code与自身原code相同允许更新")
    void testUpdateWithSameCode() {
        MeetingMinutes existing = buildExisting();
        when(meetingMinutesRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(meetingMinutesRepository.save(any(MeetingMinutes.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeetingMinutesDTO dto = buildDTO();
        dto.setTitle("新标题");

        MeetingMinutes result = meetingMinutesService.update(1L, dto);

        assertEquals("新标题", result.getTitle());
        verify(meetingMinutesRepository).save(any());
        verify(meetingMinutesRepository, never()).findByCode(any());
    }

    @Test
    @DisplayName("删除会议纪要 - 应设置status为inactive")
    void testDeleteMeetingMinutes() {
        MeetingMinutes minutes = buildExisting();
        when(meetingMinutesRepository.findById(1L)).thenReturn(Optional.of(minutes));
        when(meetingMinutesRepository.save(any(MeetingMinutes.class))).thenAnswer(invocation -> invocation.getArgument(0));

        meetingMinutesService.delete(1L);

        verify(meetingMinutesRepository).save(argThat(m -> "inactive".equals(m.getStatus())));
    }

    @Test
    @DisplayName("根据ID查询 - 会议纪要不存在应抛出BusinessException")
    void testGetByIdNotFound() {
        when(meetingMinutesRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> meetingMinutesService.getById(999L));
        assertEquals("会议纪要不存在", exception.getMessage());
    }
}
