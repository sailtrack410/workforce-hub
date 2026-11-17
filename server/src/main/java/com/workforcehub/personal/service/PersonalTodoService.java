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
        String category = StringUtils.hasText(request.getCategory()) ? request.getCategory() : "TASK";
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new BizException(400, "无效的待办分类");
        }
        PersonalTodoResponse todo = new PersonalTodoResponse();
        todo.setTodoId(IdGenerator.nextId());
        todo.setOwnerId(CurrentUserHolder.requireUserId());
        todo.setTitle(request.getTitle());
        todo.setCategory(category);
        todo.setStatus("PENDING");
        todo.setDueAt(request.getDueAt());
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
        Long ownerId = CurrentUserHolder.requireUserId();
        return personalTodoRepository.find(ownerId, category, status, start, end);
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonalTodoResponse update(Long todoId, PersonalTodoSaveRequest request) {
        PersonalTodoResponse existing = requireTodo(todoId);
        existing.setTitle(request.getTitle());
        if (StringUtils.hasText(request.getCategory())) {
            if (!ALLOWED_CATEGORIES.contains(request.getCategory())) {
                throw new BizException(400, "无效的待办分类");
            }
            existing.setCategory(request.getCategory());
        }
        existing.setDueAt(request.getDueAt());
        existing.setNote(request.getNote());
        personalTodoRepository.update(existing);
        return requireTodo(todoId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonalTodoResponse complete(Long todoId) {
        PersonalTodoResponse existing = requireTodo(todoId);
        personalTodoRepository.updateStatus(todoId, existing.getOwnerId(), "DONE", LocalDateTime.now());
        return requireTodo(todoId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonalTodoResponse reopen(Long todoId) {
        PersonalTodoResponse existing = requireTodo(todoId);
        personalTodoRepository.updateStatus(todoId, existing.getOwnerId(), "PENDING", null);
        return requireTodo(todoId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long todoId) {
        requireTodo(todoId);
        personalTodoRepository.deleteById(todoId, CurrentUserHolder.requireUserId());
    }

    private PersonalTodoResponse requireTodo(Long todoId) {
        Long ownerId = CurrentUserHolder.requireUserId();
        return personalTodoRepository.findByIdAndOwner(todoId, ownerId)
            .orElseThrow(() -> new BizException(404, "个人待办不存在"));
    }
}
