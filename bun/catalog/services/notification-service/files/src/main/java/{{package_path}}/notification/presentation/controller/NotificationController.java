package {{package}}.notification.presentation.controller;

import {{package}}.notification.application.service.NotificationService;
import {{package}}.notification.presentation.dto.NotificationDetailView;
import {{package}}.notification.presentation.dto.NotificationView;
import {{package}}.notification.presentation.mapper.NotificationMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
@RestController
@RequestMapping("/notifications")
class NotificationController {
    private final NotificationService service;
    private final NotificationMapper mapper;
    NotificationController(NotificationService service, NotificationMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    PagedModel<NotificationView> list(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return new PagedModel<>(service.list(pageable).map(mapper::toView));
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    NotificationDetailView get(@PathVariable UUID id) {
        return mapper.toDetailView(service.getById(id));
    }
}
