package com.expense.calculator.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileRequest(@NotBlank String name, String emoji) {
}
