package {{package}}.user.presentation.controller;

import {{package}}.user.application.service.UserService;
import {{package}}.user.presentation.dto.VerifyCredentialsRequest;
import {{package}}.user.presentation.dto.VerifyCredentialsResponse;
import {{package}}.user.presentation.mapper.UserMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/internal/users")
class InternalUserController {
    private final UserService service;
    private final String serviceKey;
    private final UserMapper mapper;
    InternalUserController(UserService service, @Value("${internal.service-key:}") String serviceKey, UserMapper mapper) {
        this.service = service;
        this.serviceKey = serviceKey;
        this.mapper = mapper;
    }
    @PostMapping("/verify-credentials")
    ResponseEntity<VerifyCredentialsResponse> verifyCredentials(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String providedKey,
            @Valid @RequestBody VerifyCredentialsRequest request) {
        if (serviceKey.isBlank() || !serviceKey.equals(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return service.verifyCredentials(request.email(), request.password())
                .map(mapper::toVerifyCredentialsResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
