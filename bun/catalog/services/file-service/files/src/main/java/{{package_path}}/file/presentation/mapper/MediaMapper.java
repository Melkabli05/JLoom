package {{package}}.file.presentation.mapper;

import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.presentation.dto.MediaUploadResponse;
import {{package}}.file.presentation.dto.MediaView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR)
public interface MediaMapper {
    @Mapping(source = "storageKey", target = "key")
    MediaUploadResponse toUploadResponse(MediaAsset asset);
    @Mapping(source = "storageKey", target = "key")
    MediaView toView(MediaAsset asset);
}
