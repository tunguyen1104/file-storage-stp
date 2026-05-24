package com.filestorage.adapter.out.search.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(indexName = "#{@elasticsearchProperties.indices.ragChunks}", createIndex = false)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagChunkDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String fileId;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String folderId;

    @Field(type = FieldType.Keyword)
    private String uploadedBy;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "icu_analyzer"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256)
            }
    )
    private String originalName;

    @Field(type = FieldType.Integer)
    private Integer chunkIndex;

    @Field(type = FieldType.Keyword)
    private String ingestionId;

    @Field(type = FieldType.Text, analyzer = "icu_analyzer")
    private String content;

    @Field(type = FieldType.Dense_Vector, dims = 1024)
    private List<Float> contentVector;

    @Field(type = FieldType.Keyword)
    private String sourceKey;

    @Field(type = FieldType.Keyword)
    private String embeddingModel;

    @Field(type = FieldType.Keyword)
    private String chunkerVersion;

    @Field(type = FieldType.Boolean)
    private Boolean ready;

    @Field(type = FieldType.Boolean)
    private Boolean retrievable;

    @Field(type = FieldType.Keyword)
    private String fileStatus;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    public static String idOf(UUID fileId, UUID ingestionId, int chunkIndex) {
        return fileId + ":" + ingestionId + ":" + chunkIndex;
    }
}
