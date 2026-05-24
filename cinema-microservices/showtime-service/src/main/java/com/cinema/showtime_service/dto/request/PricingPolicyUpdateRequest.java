package com.cinema.showtime_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PricingPolicyUpdateRequest {
    @NotNull(message = "cinemaId khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    UUID cinemaId;

    @NotBlank(message = "TÃªn chÃ­nh sÃ¡ch giÃ¡ khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    String name;

    @NotNull(message = "GiÃ¡ STANDARD khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @PositiveOrZero(message = "GiÃ¡ STANDARD pháº£i lá»›n hÆ¡n hoáº·c báº±ng 0")
    Long standardPrice;

    @NotNull(message = "GiÃ¡ VIP khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @PositiveOrZero(message = "GiÃ¡ VIP pháº£i lá»›n hÆ¡n hoáº·c báº±ng 0")
    Long vipPrice;

    @NotNull(message = "GiÃ¡ COUPLE khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @PositiveOrZero(message = "GiÃ¡ COUPLE pháº£i lá»›n hÆ¡n hoáº·c báº±ng 0")
    Long couplePrice;
}
