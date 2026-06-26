package ru.andrew.mainserver.pin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.pin.dto.CreatePinRequest;
import ru.andrew.mainserver.pin.dto.PinResponse;
import ru.andrew.mainserver.session.service.PinManagementService;

@RestController
@RequestMapping("/admin/pins")
@RequiredArgsConstructor
public class PinManagementController {

    private final PinManagementService pinManagementService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public PinResponse createPin(@Valid @RequestBody CreatePinRequest request) {
        return pinManagementService.createPin(request);
    }

    @PostMapping("/{pinId}/revoke")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public PinResponse revokePin(@PathVariable Long pinId) {
        return pinManagementService.revokePin(pinId);
    }
}