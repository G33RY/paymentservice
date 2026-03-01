package hu.imregergo.paymentservice.account.controller;


import hu.imregergo.paymentservice.account.dto.AccountResponse;
import hu.imregergo.paymentservice.account.dto.CreateAccountDto;
import hu.imregergo.paymentservice.account.mapper.AccountMapper;
import hu.imregergo.paymentservice.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountDto dto) {
        return AccountMapper.toResponse(accountService.createAccount(dto));
    }

    @GetMapping
    public List<AccountResponse> getAccounts() {
        return accountService.getAccounts().stream()
                .map(AccountMapper::toResponse)
                .toList();
    }
}
