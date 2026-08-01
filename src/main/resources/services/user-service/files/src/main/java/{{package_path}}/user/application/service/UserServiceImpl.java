package {{package}}.user.application.service;

import {{package}}.user.domain.model.Role;
import {{package}}.user.domain.model.User;
import {{package}}.user.domain.model.User_;
import {{package}}.user.infrastructure.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    UserServiceImpl(UserRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public UUID register(String email, String plainPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (repository.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        User user = new User(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(plainPassword),
                clock.instant(),
                null,
                Role.USER,
                true);
        try {
            return repository.save(user).getId();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered", e);
        }
    }

    @Override
    public Optional<User> verifyCredentials(String email, String plainPassword) {
        String normalizedEmail = normalizeEmail(email);
        return repository.findByEmail(normalizedEmail)
                .filter(user -> matchesPassword(plainPassword, user))
                .filter(User::isEnabled);
    }

    @Override
    public User getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found: " + id));
    }

    @Override
    @Transactional
    public User changeEmail(UUID id, String newEmail, String currentPassword) {
        User user = getById(id);
        requireCurrentPassword(user, currentPassword);

        String normalizedEmail = normalizeEmail(newEmail);
        Optional<User> existing = repository.findByEmail(normalizedEmail);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }

        user.changeEmail(normalizedEmail);
        try {
            return repository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered", e);
        }
    }

    @Override
    @Transactional
    public void changePassword(UUID id, String currentPassword, String newPassword) {
        User user = getById(id);
        requireCurrentPassword(user, currentPassword);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        repository.save(user);
    }

    @Override
    @Transactional
    public void delete(UUID id, String currentPassword) {
        User user = getById(id);
        requireCurrentPassword(user, currentPassword);
        repository.delete(user);
    }

    @Override
    public Page<User> list(String emailContains, Role role, Pageable pageable) {
        Specification<User> spec = Specification.unrestricted();
        if (emailContains != null && !emailContains.isBlank()) {
            String pattern = "%" + normalizeEmail(emailContains) + "%";
            spec = spec.and((root, cb) -> cb.like(cb.lower(root.get(User_.email)), pattern));
        }
        if (role != null) {
            spec = spec.and((root, cb) -> cb.equal(root.get(User_.role), role));
        }
        return repository.findAll(spec, pageable);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (!matchesPassword(currentPassword, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "current password is incorrect");
        }
    }

    private boolean matchesPassword(String plainPassword, User user) {
        try {
            return passwordEncoder.matches(plainPassword, user.getPasswordHash());
        } catch (IllegalArgumentException e) {
            log.error("Password verification failed unexpectedly for user {}", user.getId(), e);
            return false;
        }
    }
}
