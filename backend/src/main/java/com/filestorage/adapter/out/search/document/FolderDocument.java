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
import java.util.UUID;

@Document(indexName = "#{@elasticsearchProperties.indices.folders}", createIndex = false)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String parentId;

    @Field(type = FieldType.Keyword)
    private String createdBy;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "icu_analyzer"),
            otherFields = {
                    @InnerField(suffix = "sayt", type = FieldType.Search_As_You_Type),
                    @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256)
            }
    )
    private String name;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    public static String idOf(UUID folderId) {
        return folderId.toString();
    }
}
