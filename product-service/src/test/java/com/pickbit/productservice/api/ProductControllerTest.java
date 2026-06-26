package com.pickbit.productservice.api;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.productservice.api.dto.request.ProductSearchCondition;
import com.pickbit.productservice.application.ProductCommandService;
import com.pickbit.productservice.application.ProductQueryService;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductQueryService productQueryService;

    @MockitoBean
    private ProductCommandService productCommandService;

    @Test
    @DisplayName("productStatus 쿼리 파라미터를 중복 지정하면 List 로 바인딩되어 서비스에 전달된다")
    void searchProducts_bindsMultipleProductStatusValuesAsList() throws Exception {
        given(productQueryService.searchProducts(any(ProductSearchCondition.class), any(Pageable.class)))
                .willReturn(PageResponse.from(org.springframework.data.domain.Page.empty()));

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "LATEST")
                        .param("productStatus", "ACTIVE")
                        .param("productStatus", "AUCTION_SCHEDULED"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductSearchCondition> captor = ArgumentCaptor.forClass(ProductSearchCondition.class);
        org.mockito.Mockito.verify(productQueryService).searchProducts(captor.capture(), any(Pageable.class));

        ProductSearchCondition captured = captor.getValue();
        assertThat(captured.productStatus())
                .containsExactly(ProductStatus.ACTIVE, ProductStatus.AUCTION_SCHEDULED);
    }
}
