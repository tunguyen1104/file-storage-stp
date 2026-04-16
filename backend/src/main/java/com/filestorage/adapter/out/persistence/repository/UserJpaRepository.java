package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.tenant WHERE u.email = :email")
    Optional<User> findByEmailWithRoleAndTenant(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.tenant LEFT JOIN FETCH u.createdBy WHERE u.id = :id")
    Optional<User> findByIdWithRoleAndTenant(@Param("id") UUID id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);
}
