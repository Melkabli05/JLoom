package {{package}}.user.presentation.controller;
import {{package}}.user.application.service.UserService;
import {{package}}.user.domain.model.Role;
import {{package}}.user.presentation.annotation.SelfOrAdmin;
import {{package}}.user.presentation.dto.ChangeEmailRequest;
import {{package}}.user.presentation.dto.ChangePasswordRequest;
import {{package}}.user.presentation.dto.DeleteUserRequest;
import {{package}}.user.presentation.dto.RegistrationRequest;
import {{package}}.user.presentation.dto.UserResponse;
import {{package}}.user.presentation.dto.UserView;
import {{package}}.user.presentation.mapper.UserMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
@RestController
@RequestMapping("/users")
class UserController {
    private final UserService service;
    private final UserMapper mapper;
    UserController(UserService service, UserMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    @PostMapping
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegistrationRequest request) {
        UUID id = service.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(id));
    }
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    PagedModel<UserView> list(@RequestParam(required = false) String email,
                               @RequestParam(required = false) Role role,
                               @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return new PagedModel<>(service.list(email, role, pageable).map(mapper::toView));
    }
    @GetMapping("/{id}")
    @SelfOrAdmin
    UserView get(@PathVariable UUID id) {
        return mapper.toView(service.getById(id));
    }
    @PatchMapping("/{id}/email")
    @SelfOrAdmin
    UserView changeEmail(@PathVariable UUID id, @Valid @RequestBody ChangeEmailRequest request) {
        return mapper.toView(service.changeEmail(id, request.newEmail(), request.currentPassword()));
    }
    @PatchMapping("/{id}/password")
    @SelfOrAdmin
    ResponseEntity<Void> changePassword(@PathVariable UUID id, @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(id, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{id}")
    @SelfOrAdmin
    ResponseEntity<Void> delete(@PathVariable UUID id, @Valid @RequestBody DeleteUserRequest request) {
        service.delete(id, request.currentPassword());
        return ResponseEntity.noContent().build();
    }
}
