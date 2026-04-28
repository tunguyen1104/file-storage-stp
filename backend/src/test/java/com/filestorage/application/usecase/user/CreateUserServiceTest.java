package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.messaging.EmailEventPublisher;
import com.filestorage.adapter.out.messaging.event.EmailEvent;
import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RoleJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.CreateUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateUserServiceTest {

    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private RoleJpaRepository roleRepository;
    @Mock
    private TenantJpaRepository tenantRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailEventPublisher emailEventPublisher;

    private CreateUserService createUserService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        PasswordGenerator passwordGenerator = new PasswordGenerator();
        createUserService = new CreateUserService(
                userRepository,
                roleRepository,
                tenantRepository,
                mapper,
                passwordEncoder,
                passwordGenerator,
                emailEventPublisher
        );
    }

    @Test
    void tenantAdminCannotAssignSuperAdminRole() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Role superAdminRole = Role.builder().code(RoleCode.SUPER_ADMIN).name("Super Admin").build();
        superAdminRole.setId(UUID.randomUUID());

        when(userRepository.existsByEmail("u@example.com")).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.SUPER_ADMIN)).thenReturn(Optional.of(superAdminRole));

        var command = new CreateUserUseCase.CreateUserCommand(
                actorId, tenantId, RoleCode.TENANT_ADMIN,
                "u@example.com", "User", RoleCode.SUPER_ADMIN, 100L, null
        );

        assertThrows(AppException.class, () -> createUserService.execute(command));
    }

    @Test
    void createUserSuccess() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Tenant tenant = Tenant.builder().name("T").slug("t").isActive(true).build();
        tenant.setId(tenantId);

        Role role = Role.builder().code(RoleCode.MEMBER).name("Member").build();
        role.setId(UUID.randomUUID());

        User actor = User.builder().tenant(tenant).role(role).email("admin@example.com")
                .passwordHash("hash").fullName("Admin").isActive(true).build();
        actor.setId(actorId);

        when(userRepository.existsByEmail("u@example.com")).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(role));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(Objects.requireNonNull(passwordEncoder.encode(any()))).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var command = new CreateUserUseCase.CreateUserCommand(
                actorId, tenantId, RoleCode.TENANT_ADMIN,
                "u@example.com", "User", RoleCode.MEMBER, 100L, null
        );

        var result = createUserService.execute(command);
        assertEquals("u@example.com", result.email());
        assertEquals(actorId, result.createdBy());

        ArgumentCaptor<EmailEvent> captor = ArgumentCaptor.forClass(EmailEvent.class);
        verify(emailEventPublisher).publishInitialPassword(captor.capture());
        EmailEvent published = captor.getValue();
        assertEquals(EmailEvent.EmailType.INITIAL_PASSWORD, published.type());
        assertEquals("u@example.com", published.to());
        assertTrue(published.payload().containsKey("temporaryPassword"));
    }

    @Test
    void createUserQuotaCannotExceedTenantQuota() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Tenant tenant = Tenant.builder().name("T").slug("t").isActive(true).storageQuotaBytes(150L).build();
        tenant.setId(tenantId);

        Role role = Role.builder().code(RoleCode.MEMBER).name("Member").build();
        role.setId(UUID.randomUUID());

        User actor = User.builder().tenant(tenant).role(role).email("admin@example.com")
                .passwordHash("hash").fullName("Admin").isActive(true).build();
        actor.setId(actorId);

        when(userRepository.existsByEmail("u@example.com")).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(role));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

        var command = new CreateUserUseCase.CreateUserCommand(
                actorId, tenantId, RoleCode.TENANT_ADMIN,
                "u@example.com", "User", RoleCode.MEMBER, 200L, null
        );

        AppException ex = assertThrows(AppException.class, () -> createUserService.execute(command));
        assertEquals(ErrorCode.TENANT_QUOTA_EXCEEDED, ex.getError());
    }

    @Test
    void createUserQuotaCannotExceedTenantRemainingQuota() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Tenant tenant = Tenant.builder().name("T").slug("t").isActive(true).storageQuotaBytes(300L).build();
        tenant.setId(tenantId);
        tenant.setStorageUsedBytes(250L);

        Role role = Role.builder().code(RoleCode.MEMBER).name("Member").build();
        role.setId(UUID.randomUUID());

        User actor = User.builder().tenant(tenant).role(role).email("admin@example.com")
                .passwordHash("hash").fullName("Admin").isActive(true).build();
        actor.setId(actorId);

        when(userRepository.existsByEmail("u@example.com")).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(role));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

        var command = new CreateUserUseCase.CreateUserCommand(
                actorId, tenantId, RoleCode.TENANT_ADMIN,
                "u@example.com", "User", RoleCode.MEMBER, 100L, null
        );

        AppException ex = assertThrows(AppException.class, () -> createUserService.execute(command));
        assertEquals(ErrorCode.TENANT_QUOTA_EXCEEDED, ex.getError());
    }
}
