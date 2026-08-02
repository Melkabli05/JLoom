package {{package}}.user.application.service;
import {{package}}.user.domain.model.Role;
import {{package}}.user.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;
public interface UserService {
    UUID register(String email, String plainPassword);
    Optional<User> verifyCredentials(String email, String plainPassword);
    User getById(UUID id);
    User changeEmail(UUID id, String newEmail, String currentPassword);
    void changePassword(UUID id, String currentPassword, String newPassword);
    void delete(UUID id, String currentPassword);
    Page<User> list(String emailContains, Role role, Pageable pageable);
}
