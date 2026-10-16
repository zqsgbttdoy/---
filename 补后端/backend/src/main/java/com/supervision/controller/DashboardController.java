package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.AuthContext;
import com.supervision.entity.SafetyLog;
import com.supervision.entity.SupervisionDiary;
import com.supervision.entity.SupervisionLog;
import com.supervision.entity.SupervisionNotice;
import com.supervision.entity.WorkContact;
import com.supervision.repository.MessageRepository;
import com.supervision.repository.SafetyLogRepository;
import com.supervision.repository.SupervisionDiaryRepository;
import com.supervision.repository.SupervisionLogRepository;
import com.supervision.repository.SupervisionNoticeRepository;
import com.supervision.repository.WorkContactRepository;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@AllArgsConstructor
public class DashboardController {

    private final SupervisionNoticeRepository noticeRepo;
    private final WorkContactRepository contactRepo;
    private final SupervisionDiaryRepository diaryRepo;
    private final SupervisionLogRepository logRepo;
    private final SafetyLogRepository safetyRepo;
    private final MessageRepository messageRepo;
    private final com.supervision.service.ProjectAccessService access;
    private final com.supervision.service.FieldRecordService fields;

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 待办理与退回分别计数，不把草稿、正常待审核或软删除记录视为异常。
        long pendingNotices = noticeRepo.count((root, query, cb) -> cb.and(access.scope(root, cb), root.get("status").in("pending", "issued", "overdue")));
        long pendingContacts = contactRepo.count((root, query, cb) -> cb.and(access.scope(root, cb), root.get("status").in("pending", "sent", "overdue")));
        long abnormalDiaries = fields.list(com.supervision.common.FieldRecordKind.DIARY, 1, 1, null, null, "rejected", null, null, null).getTotal();
        long abnormalLogs = fields.list(com.supervision.common.FieldRecordKind.LOG, 1, 1, null, null, "rejected", null, null, null).getTotal();
        long abnormalSafetyLogs = safetyRepo.count((root, query, cb) -> cb.and(access.scope(root, cb), cb.equal(root.get("status"), "rejected")));

        long unreadMessages = messageRepo.countByReceiverIdAndIsRead(AuthContext.get().userId(), "0");

        stats.put("pendingNotices", pendingNotices);
        stats.put("pendingContacts", pendingContacts);
        stats.put("abnormalDiaries", abnormalDiaries);
        stats.put("abnormalLogs", abnormalLogs);
        stats.put("abnormalSafetyLogs", abnormalSafetyLogs);
        stats.put("unreadMessages", unreadMessages);

        return ApiResponse.success(stats);
    }
}
