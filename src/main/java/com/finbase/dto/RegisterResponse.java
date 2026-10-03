package com.finbase.dto;

import com.finbase.entity.enums.FinancierStatus;
import java.util.UUID;

public record RegisterResponse(UUID financierId, String financierCode, FinancierStatus status) {
}
