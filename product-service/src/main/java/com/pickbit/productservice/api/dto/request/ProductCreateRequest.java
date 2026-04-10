package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import com.pickbit.productservice.domain.product.entity.enums.ListingType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 등록 요청 DTO.
 *
 * <p>{@code POST /products} 호출 시 신규 상품 등록에 필요한 정보를 전달합니다.
 * 이미지 파일은 multipart의 {@code files} 파트로 함께 전송합니다.
 */
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

    @NotEmpty
    private List<ImageType> imageTypes;

    @NotEmpty
    private List<Integer> sortOrders;

    @NotEmpty
    private List<MultipartFile> files;

    @AssertTrue(message = "파일 수와 imageTypes, sortOrders의 개수가 일치해야 합니다.")
    private boolean isImageCountValid() {
        if (files == null || imageTypes == null || sortOrders == null) return true;
        return files.size() == imageTypes.size() && files.size() == sortOrders.size();
    }
}
