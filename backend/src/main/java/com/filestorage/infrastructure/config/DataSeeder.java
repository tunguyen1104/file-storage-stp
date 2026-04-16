package com.filestorage.infrastructure.config;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.RoleJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the {@code roles} table, the system tenant, and the super-admin
 * account on startup if entries are missing.
 *
 * <p><b>Super-admin bootstrap:</b> {@code User.createdBy} is a non-null
 * self-referential FK. For the first account, we set {@code createdBy}
 * to the entity itself <em>before</em> calling {@code save()}. Hibernate
 * generates the UUID client-side ({@code GenerationType.UUID}), so both
 * the entity's own ID and the {@code created_by} column resolve to the
 * same UUID in the single INSERT statement.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final String SYSTEM_TENANT_SLUG = "system";

    private final RoleJpaRepository    roleRepository;
    private final TenantJpaRepository  tenantRepository;
    private final UserJpaRepository    userRepository;
    private final PasswordEncoder      passwordEncoder;

    @Value("${app.seed.super-admin.email:superadmin@system.local}")
    private String superAdminEmail;

    @Value("${app.seed.super-admin.password:Admin@123456}")
    private String superAdminPassword;

    @Override
    @Transactional
    public void run(String @NonNull ... args) {
        log.info("Starting data seed...");

        seedRole(RoleCode.SUPER_ADMIN,  "Super Admin",  "Full system access across all tenants");
        seedRole(RoleCode.TENANT_ADMIN, "Tenant Admin", "Administrative access within a tenant");
        seedRole(RoleCode.MEMBER,       "Member",       "Standard member access within a tenant");

        Tenant systemTenant = seedSystemTenant();
        seedSuperAdmin(systemTenant);

        log.info("Data seed completed.");
    }

    private void seedRole(RoleCode code, String name, String description) {
        if (!roleRepository.existsByCode(code)) {
            Role role = Role.builder()
                    .code(code)
                    .name(name)
                    .description(description)
                    .build();
            roleRepository.save(role);
            log.info("Seeded role: {}", code);
        }
    }

    /**
     * Creates the system tenant (slug = {@code "system"}) used as the home
     * tenant for the super-admin account.
     *
     * @return the existing or newly created system {@link Tenant}
     */
    private Tenant seedSystemTenant() {
        return tenantRepository.findBySlug(SYSTEM_TENANT_SLUG).orElseGet(() -> {
            Tenant tenant = Tenant.builder()
                    .name("System")
                    .slug(SYSTEM_TENANT_SLUG)
                    .isActive(true)
                    .storageQuotaBytes(Long.MAX_VALUE) // unlimited for system tenant
                    .build();
            Tenant saved = tenantRepository.save(tenant);
            log.info("Seeded system tenant (id={})", saved.getId());
            return saved;
        });
    }

    /**
     * Creates the super-admin user if no account with the configured email exists.
     *
     * <p>The {@code createdBy} self-reference is resolved by assigning the entity
     * to itself <em>before</em> {@code save()} — Hibernate generates the UUID
     * client-side, so both columns share the same value in one INSERT.</p>
     *
     * @param systemTenant the system tenant the super-admin belongs to
     */
    private void seedSuperAdmin(Tenant systemTenant) {
        String email = superAdminEmail.toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            log.debug("Super-admin already exists (email={}), skipping", email);
            return;
        }

        Role superAdminRole = roleRepository.findByCode(RoleCode.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "SUPER_ADMIN role not found — roles must be seeded before users"));

        User superAdmin = User.builder()
                .tenant(systemTenant)
                .email(email)
                .passwordHash(passwordEncoder.encode(superAdminPassword))
                .fullName("Super Admin")
                .role(superAdminRole)
                .isActive(true)
                .build();

        // Bootstrap self-reference: Hibernate generates the UUID before INSERT,
        // so setting createdBy = self resolves to the same UUID in the DB row.
        superAdmin.setCreatedBy(superAdmin);

        userRepository.save(superAdmin);
        log.info("Seeded super-admin account (email={})", email);
    }
}

