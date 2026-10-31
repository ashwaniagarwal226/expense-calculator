package com.expense.calculator.controller;

import com.expense.calculator.dto.ProfileDto;
import com.expense.calculator.dto.ProfileRequest;
import com.expense.calculator.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("v1/profiles")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public List<ProfileDto> list() {
        return profiles.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileDto create(@Valid @RequestBody ProfileRequest request) {
        return profiles.create(request.name(), request.emoji());
    }

    @PutMapping("/{id}")
    public ProfileDto update(@PathVariable Long id, @Valid @RequestBody ProfileRequest request) {
        return profiles.update(id, request.name(), request.emoji());
    }

    /** Deletes the profile, its transactions and its own rules. */
    @DeleteMapping("/{id}")
    public Map<String, Integer> delete(@PathVariable Long id) {
        return Map.of("transactionsDeleted", profiles.delete(id));
    }
}
