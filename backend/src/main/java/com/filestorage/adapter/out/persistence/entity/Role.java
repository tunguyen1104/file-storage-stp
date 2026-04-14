package com.filestorage.adapter.out.persistence.entity;

import com.filestorage.domain.model.enums.RoleCode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    @Enumerated(EnumType.STRING)
    private RoleCode code;

    @Column(name = "name", length = 100, unique = true, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}

