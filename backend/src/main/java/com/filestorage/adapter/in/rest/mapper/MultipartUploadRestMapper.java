package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.MultipartCompleteResponse;
import com.filestorage.adapter.in.rest.dto.response.MultipartInitResponse;
import com.filestorage.adapter.in.rest.dto.response.MultipartSessionResponse;
import com.filestorage.adapter.in.rest.dto.response.PartCompletionResponse;
import com.filestorage.adapter.in.rest.dto.response.PartUrlResponse;
import com.filestorage.domain.model.file.MultipartCompleteView;
import com.filestorage.domain.model.file.MultipartSessionView;
import com.filestorage.domain.model.file.MultipartUploadInitView;
import com.filestorage.domain.model.file.PartCompletionView;
import com.filestorage.domain.model.file.PartPresignedUrlView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface MultipartUploadRestMapper {

    MultipartInitResponse toInitResponse(MultipartUploadInitView view);

    PartUrlResponse toPartUrlResponse(PartPresignedUrlView view);

    PartCompletionResponse toPartCompletionResponse(PartCompletionView view);

    @Mapping(target = "message",
             constant = "File is being scanned for viruses. Please check back shortly.")
    MultipartCompleteResponse toCompleteResponse(MultipartCompleteView view);

    @Mapping(target = "uploadedParts", expression = "java(toPartInfoList(view))")
    MultipartSessionResponse toSessionResponse(MultipartSessionView view);

    default java.util.List<MultipartSessionResponse.PartInfo> toPartInfoList(MultipartSessionView view) {
        if (view.uploadedParts() == null) return java.util.List.of();
        return view.uploadedParts().stream()
                .map(p -> MultipartSessionResponse.PartInfo.builder()
                        .partNumber(p.partNumber())
                        .etag(p.etag())
                        .build())
                .toList();
    }
}
