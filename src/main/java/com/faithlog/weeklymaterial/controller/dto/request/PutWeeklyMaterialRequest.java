package com.faithlog.weeklymaterial.controller.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PutWeeklyMaterialRequest(@NotNull @Positive Long mediaAssetId) {
}
