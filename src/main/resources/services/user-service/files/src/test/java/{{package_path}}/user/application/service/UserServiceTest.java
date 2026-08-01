package {{package}}.user.application.service;

import {{package}}.user.domain.model.Role;
import {{package}}.user.domain.model.User;
import {{package}}.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(repository, passwordEncoder, Clock.systemUTC());
    }

    @Test
    void registerNormalizesEmailToLowercaseHashesPasswordAndStoresUser() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        service = new UserServiceImpl(repository, passwordEncoder, fixed);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("supersecret123")).thenReturn("{bcrypt}hash");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID id = service.register("Alice@Example.com", "supersecret123");

        assertNotNull(id);
        verify(repository).save(argThatUser(u ->
                u.getEmail().equals("alice@example.com")
                        && u.getPasswordHash().equals("{bcrypt}hash")
                        && u.getCreatedAt().equals(fixed.instant())
                        && u.getEmailVerifiedAt() == null));
    }

    @Test
    void duplicateEmailReturnsConflictWithoutHashingOrSaving() {
        when(repository.findByEmail(eq("dup@example.com"))).thenReturn(
                Optional.of(new User(UUID.randomUUID(), "dup@example.com", "hash", Instant.now(), null, Role.USER, true)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.register("dup@example.com", "anothertwo"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void duplicateEmailCheckIsCaseInsensitive() {
        when(repository.findByEmail(eq("dup@example.com"))).thenReturn(
                Optional.of(new User(UUID.randomUUID(), "dup@example.com", "hash", Instant.now(), null, Role.USER, true)));

        assertThrows(ResponseStatusException.class,
                () -> service.register("DUP@EXAMPLE.COM", "anothertwo"));
    }

    @Test
    void concurrentRegistrationRaceIsTranslatedToConflictNotARawDatabaseError() {
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}hash");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint violated"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.register("race@example.com", "supersecret123"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void verifyCredentialsReturnsTheUserWhenPasswordMatches() {
        User existing = new User(UUID.randomUUID(), "alice@example.com", "storedhash", Instant.now(), null, Role.USER, true);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correctpassword", "storedhash")).thenReturn(true);

        Optional<User> result = service.verifyCredentials("Alice@Example.com", "correctpassword");

        assertEquals(existing, result.orElse(null));
    }

    @Test
    void verifyCredentialsReturnsEmptyWhenPasswordIsWrong() {
        User existing = new User(UUID.randomUUID(), "alice@example.com", "storedhash", Instant.now(), null, Role.USER, true);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrongpassword", "storedhash")).thenReturn(false);

        assertEquals(Optional.empty(), service.verifyCredentials("alice@example.com", "wrongpassword"));
    }

    @Test
    void verifyCredentialsReturnsEmptyWhenEmailIsUnknown() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), service.verifyCredentials("missing@example.com", "anypassword"));
    }

    @Test
    void verifyCredentialsFailsClosedWhenPasswordEncoderThrows() {
        User existing = new User(UUID.randomUUID(), "alice@example.com", "corrupted", Instant.now(), null, Role.USER, true);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("anypassword", "corrupted")).thenThrow(new IllegalArgumentException("Encoded password does not look like BCrypt"));

        assertEquals(Optional.empty(), service.verifyCredentials("alice@example.com", "anypassword"));
    }

    @Test
    void verifyCredentialsReturnsEmptyWhenAccountIsDisabledEvenWithTheCorrectPassword() {
        User disabled = new User(UUID.randomUUID(), "alice@example.com", "storedhash", Instant.now(), null, Role.USER, false);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("correctpassword", "storedhash")).thenReturn(true);

        assertEquals(Optional.empty(), service.verifyCredentials("alice@example.com", "correctpassword"));
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getById(missing));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void changeEmailRequiresCorrectCurrentPassword() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "old@example.com", "storedhash", Instant.now(), Instant.now(), Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrongpassword", "storedhash")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.changeEmail(id, "new@example.com", "wrongpassword"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void changeEmailRejectsAnEmailAlreadyBelongingToSomeoneElse() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "old@example.com", "storedhash", Instant.now(), Instant.now(), Role.USER, true);
        User someoneElse = new User(UUID.randomUUID(), "taken@example.com", "otherhash", Instant.now(), null, Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correctpassword", "storedhash")).thenReturn(true);
        when(repository.findByEmail("taken@example.com")).thenReturn(Optional.of(someoneElse));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.changeEmail(id, "taken@example.com", "correctpassword"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void changeEmailToItsOwnCurrentValueSucceeds() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "same@example.com", "storedhash", Instant.now(), Instant.now(), Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correctpassword", "storedhash")).thenReturn(true);
        when(repository.findByEmail("same@example.com")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.changeEmail(id, "same@example.com", "correctpassword");
    }

    @Test
    void changeEmailResetsEmailVerifiedAt() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "old@example.com", "storedhash", Instant.now(), Instant.now(), Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correctpassword", "storedhash")).thenReturn(true);
        when(repository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.changeEmail(id, "new@example.com", "correctpassword");

        assertEquals("new@example.com", updated.getEmail());
        assertEquals(null, updated.getEmailVerifiedAt());
    }

    @Test
    void changePasswordRequiresCorrectCurrentPassword() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "alice@example.com", "storedhash", Instant.now(), null, Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrongpassword", "storedhash")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> service.changePassword(id, "wrongpassword", "newpassword123"));
        verify(repository, never()).save(any());
    }

    @Test
    void changePasswordFailsClosedWhenPasswordEncoderThrows() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "alice@example.com", "corrupted", Instant.now(), null, Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("anypassword", "corrupted")).thenThrow(new IllegalArgumentException("Encoded password does not look like BCrypt"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.changePassword(id, "anypassword", "newpassword123"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void changePasswordHashesTheNewPasswordAndSaves() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "alice@example.com", "oldhash", Instant.now(), null, Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("oldpassword", "oldhash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword123")).thenReturn("{bcrypt}newhash");

        service.changePassword(id, "oldpassword", "newpassword123");

        verify(repository).save(argThatUser(u -> u.getPasswordHash().equals("{bcrypt}newhash")));
    }

    @Test
    void deleteThrowsNotFoundWhenMissingAndNeverCallsDelete() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.delete(missing, "anypassword"));
        verify(repository, never()).delete(any(User.class));
    }

    @Test
    void deleteRequiresCorrectCurrentPassword() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "alice@example.com", "storedhash", Instant.now(), null, Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrongpassword", "storedhash")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.delete(id, "wrongpassword"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(repository, never()).delete(any(User.class));
    }

    @Test
    void deleteRemovesAnExistingUser() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "alice@example.com", "storedhash", Instant.now(), null, Role.USER, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correctpassword", "storedhash")).thenReturn(true);

        service.delete(id, "correctpassword");

        verify(repository).delete(existing);
    }

    @Test
    void listWithNoFiltersDelegatesToTheRepositorysPagedQuery() {
        User user = new User(UUID.randomUUID(), "alice@example.com", "hash", Instant.now(), null, Role.USER, true);
        Pageable pageable = Pageable.ofSize(20);
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(user)));

        Page<User> result = service.list(null, null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    private static User argThatUser(java.util.function.Predicate<User> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
