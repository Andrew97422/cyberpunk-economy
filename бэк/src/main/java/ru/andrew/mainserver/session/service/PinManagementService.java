package ru.andrew.mainserver.session.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.account.service.AccountService;
import ru.andrew.mainserver.audit.entity.AuditEventType;
import ru.andrew.mainserver.audit.service.AuditService;
import ru.andrew.mainserver.auth.service.CurrentUserService;
import ru.andrew.mainserver.pin.dto.CreatePinRequest;
import ru.andrew.mainserver.pin.dto.PinResponse;
import ru.andrew.mainserver.pin.entity.PinCode;
import ru.andrew.mainserver.pin.service.PinCodeService;

@Service
@RequiredArgsConstructor
public class PinManagementService {

    private final AccountService accountService;
    private final PinCodeService pinCodeService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Transactional
    public PinResponse createPin(CreatePinRequest request) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        Account target = accountService.getActiveByPublicName(request.getPublicName());

        PinCode pinCode = pinCodeService.create(
                target,
                request.getRawPin(),
                request.getDurationMinutes(),
                actor.getId(),
                request.getComment()
        );

        auditService.log(
                AuditEventType.PIN_CREATED,
                actor.getId(),
                "PinCode",
                pinCode.getId(),
                null,
                "PIN created for account " + target.getPublicName(),
                null
        );

        return PinResponse.builder()
                .id(pinCode.getId())
                .accountId(target.getId())
                .publicName(target.getPublicName())
                .status(pinCode.getStatus().name())
                .durationMinutes(pinCode.getDurationMinutes())
                .createdAt(pinCode.getCreatedAt())
                .usedAt(pinCode.getUsedAt())
                .comment(pinCode.getComment())
                .build();
    }

    @Transactional
    public PinResponse revokePin(Long pinId) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        PinCode pinCode = pinCodeService.getById(pinId);
        pinCodeService.revoke(pinCode);

        auditService.log(
                AuditEventType.PIN_REVOKED,
                actor.getId(),
                "PinCode",
                pinCode.getId(),
                null,
                "PIN revoked",
                null
        );

        return PinResponse.builder()
                .id(pinCode.getId())
                .accountId(pinCode.getAccount().getId())
                .publicName(pinCode.getAccount().getPublicName())
                .status(pinCode.getStatus().name())
                .durationMinutes(pinCode.getDurationMinutes())
                .createdAt(pinCode.getCreatedAt())
                .usedAt(pinCode.getUsedAt())
                .comment(pinCode.getComment())
                .build();
    }
}