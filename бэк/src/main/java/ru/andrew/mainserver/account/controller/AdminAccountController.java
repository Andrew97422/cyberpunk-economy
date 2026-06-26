package ru.andrew.mainserver.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.service.AccountService;

@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountService accountService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public Account getById(@PathVariable Long id) {
        return accountService.getById(id);
    }
}