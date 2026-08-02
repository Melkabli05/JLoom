package {{package}}.user.presentation.mapper;
import {{package}}.user.domain.model.User;
import {{package}}.user.presentation.dto.UserView;
import {{package}}.user.presentation.dto.VerifyCredentialsResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {
    UserView toView(User user);
    VerifyCredentialsResponse toVerifyCredentialsResponse(User user);
}
