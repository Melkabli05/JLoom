package {{package}}.notification.presentation.mapper;

import {{package}}.notification.domain.model.Notification;
import {{package}}.notification.presentation.dto.NotificationDetailView;
import {{package}}.notification.presentation.dto.NotificationView;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NotificationMapper {
    NotificationView toView(Notification notification);
    NotificationDetailView toDetailView(Notification notification);
}
