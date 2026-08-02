package {{package}}.user;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;

import java.time.Instant;
import java.util.UUID;

@Controller("/users")
class UserController {

    private final UserService service;

    UserController(UserService service) {
        this.service = service;
    }

    @Post
    @Produces(MediaType.APPLICATION_JSON)
    HttpResponse<UserResponse> register(@Body RegistrationRequest request) {
        UUID id = service.register(request.email(), request.password());
        return HttpResponse.created(new UserResponse(id));
    }

    @Get("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    UserView get(@PathVariable UUID id) {
        User user = service.getById(id);
        return new UserView(user.id(), user.email(), user.createdAt(), user.emailVerifiedAt());
    }

    public record RegistrationRequest(String email, String password) {
    }

    public record UserResponse(UUID id) {
    }

    public record UserView(UUID id, String email, Instant createdAt, Instant emailVerifiedAt) {
    }
}