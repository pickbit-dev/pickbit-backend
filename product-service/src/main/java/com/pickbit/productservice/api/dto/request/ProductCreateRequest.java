package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ListingType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProductCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotNull
    @Positive
    private BigDecimal startingPrice;

    @NotNull
    private ProductCondition productCondition;

    @NotNull
    private ListingType listingType;

    private Long categoryId;

    @Valid
    @NotEmpty
    private List<ProductImageRequest> images;
}
