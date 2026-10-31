package com.expense.calculator.dto;

import com.expense.calculator.domain.Profile;

public record ProfileDto(Long id, String name, String emoji, long transactionCount) {
    public static ProfileDto of(Profile p, long count) {
        return new ProfileDto(p.getId(), p.getName(), p.getEmoji(), count);
    }
}
