package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    interface TenantUserCount {
        UUID getTenantId();

        long getUserCount();
    }

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.tenant WHERE u.email = :email")
    Optional<User> findByEmailWithRoleAndTenant(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.tenant LEFT JOIN FETCH u.createdBy WHERE u.id = :id")
    Optional<User> findByIdWithRoleAndTenant(@Param("id") UUID id);

    /**
     * Fetches user with a pessimistic write lock to serialize concurrent quota updates.
     * Use inside a transaction when modifying or checking storageUsedBytes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.tenant WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    @Query("""
            SELECT u.tenant.id AS tenantId, COUNT(u.id) AS userCount
            FROM User u
            WHERE u.tenant.id IN :tenantIds
            GROUP BY u.tenant.id
            """)
    List<TenantUserCount> countUsersByTenantIds(@Param("tenantIds") Collection<UUID> tenantIds);

    /**
     * Atomically increments storageUsedBytes by {@code size} only if the result
     * would not exceed storageQuotaBytes.  Returns 1 if the update succeeded,
     * 0 if it was rejected because the quota would be exceeded.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE User u
            SET u.storageUsedBytes = u.storageUsedBytes + :size
            WHERE u.id = :id
            AND u.storageUsedBytes + :size <= u.storageQuotaBytes
            """)
    int incrementStorageUsedIfQuotaAllows(@Param("id") UUID id, @Param("size") long size);
}
