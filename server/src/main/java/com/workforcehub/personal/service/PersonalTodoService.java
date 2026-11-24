package com.workforcehub.personal.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.personal.dto.PersonalTodoResponse;
import com.workforcehub.personal.dto.PersonalTodoSaveRequest;
import com.workforcehub.personal.repository.PersonalTodoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 个人待办服务。
 */
@Service
public class PersonalTodoService {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of("APPROVAL", "MEETING", "TASK", "REMINDER");
    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "DONE");

    private final PersonalTodoRepository personalTodoRepository;

    public PersonalTodoService(PersonalTodoRepository personalTodoRepository) {
        this.personalTodoRepository = personalTodoRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonalTodoResponse create(PersonalTodoSaveRequest request) {
        Long ownerId = requireEmployeeId();
        String category = StringUtils.hasText(request.getCategory()) ? request.getCategory() : "TASK";
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new BizException(400, "无效的待办分类");
        }
        validateTimeRange(request);
        PersonalTodoResponse todo = new PersonalTodoResponse();
        todo.setTodoId(IdGenerator.nextId());
        todo.setOwnerId(ownerId);
        todo.setTitle(request.getTitle());
        todo.setCategory(category);
        todo.setStatus("PENDING");
        todo.setStartAt(request.getStartAt());
        todo.setDueAt(request.getDueAt());
        todo.setAllDay(request.isAllDay());
        todo.setReminderMinutes(request.getReminderMinutes());
        todo.setReminderAt(reminderAt(request));
        todo.setNote(request.getNote());
        personalTodoRepository.insert(todo);
        return requireTodo(todo.getTodoId());
    }

    public List<PersonalTodoResponse> list(String category, String status,
                                           LocalDateTime start, LocalDateTime end) {
        if (StringUtils.hasText(category) && !ALLOWED_CATEGORIES.contains(category)) {
            throw new BizException(400, "无效的待办分类");
        }
        if (StringUtils.hasText(status) && !ALLOWED_STATUSES.contains(status)) {
            throw new BizException(400, "无效的待办状态");
        }
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(400, "开始时间不能晚于结束时间");
        }
        Long ownerId = requireEmployeeId();
        return personalTodoRepository.find(ownerId, category, status, start, end);
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonalTodoResponse update(Long todoId, PersonalTodoSaveRequest request) {
        PersonalTodoResponse existing = requireTodo(todoId);
        validateTimeRange(request);
        existing.setTitle(request.getTitle());
        if (StringUtils.hasText(request.getCategory())) {
            if (!ALLOWED_CATEGORIES.contains(request.getCategory())) {
                throw new BizException(400, "无效的待办分类");
            }
            existing.setCategory(request.getCategory());
        }
        existing.setStartAt(request.getStartAt());
        existing.setDueAt(request.getDueAt());
        existing.setAllDay(request.isAllDay());
        existing.setReminderMinutes(request.getReminderMinutes());
        existing.setReminderAt(reminderAt(request));
        existing.setNote(request.getNote());
        personalTodoRepository.update(existing);
        return requireTodo(todoId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonalTodoResponse complete(Long todoId) {
        PersonalTodoResponse existing = requireTodo(todoId);
        if ("DONE".equals(existing.getStatus())) {
            return existing;
        }
        personalTodoRepository.updateStatus(todoId, existing.getOwnerId(), "DONE", LocalDateTime.now());
        return requireTodo(todoId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonalTodoResponse reopen(Long todoId) {
        PersonalTodoResponse existing = requireTodo(todoId);
        if ("PENDING".equals(existing.getStatus())) {
            return existing;
        }
        personalTodoRepository.updateStatus(todoId, existing.getOwnerId(), "PENDING", null);
        return requireTodo(todoId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long todoId) {
        requireTodo(todoId);
        personalTodoRepository.deleteById(todoId, CurrentUserHolder.requireUserId());
    }

    private void validateTimeRange(PersonalTodoSaveRequest request) {
        if (request.getStartAt() != null && request.getDueAt() != null
                && request.getStartAt().isAfter(request.getDueAt())) {
            throw new BizException(400, "开始时间不能晚于截止时间");
        }
        if (request.isAllDay() && request.getStartAt() == null && request.getDueAt() == null) {
            throw new BizException(400, "全天事项须选择日期");
        }
        if (request.getReminderMinutes() != null
            && (!Set.of(0, 5, 10, 15, 30, 60, 1440).contains(request.getReminderMinutes())
                || request.getStartAt() == null && request.getDueAt() == null)) {
            throw new BizException(400, "提醒须选择有效的时间和提前分钟数");
        }
    }

    private LocalDateTime reminderAt(PersonalTodoSaveRequest request) {
        if (request.getReminderMinutes() == null) return null;
        return (request.getStartAt() != null ? request.getStartAt() : request.getDueAt()).minusMinutes(request.getReminderMinutes());
    }

    private PersonalTodoResponse requireTodo(Long todoId) {
        Long ownerId = requireEmployeeId();
        return personalTodoRepository.findByIdAndOwner(todoId, ownerId)
            .orElseThrow(() -> new BizException(404, "个人待办不存在"));
    }

    private Long requireEmployeeId() {
        Long userId = CurrentUserHolder.requireUserId();
        if (!CurrentUserHolder.isEmployee()) {
            throw new BizException(403, "仅员工端支持个人待办");
        }
        return userId;
    }
}
