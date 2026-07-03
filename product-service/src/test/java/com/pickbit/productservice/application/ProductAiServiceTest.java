package com.pickbit.productservice.application;

import com.pickbit.productservice.api.dto.request.ProductListingSuggestionRequest;
import com.pickbit.productservice.api.dto.response.ProductListingSuggestionResponse;
import com.pickbit.productservice.domain.Category;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.infrastructure.persistence.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ProductAiServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private CategoryRepository categoryRepository;

    private ProductAiService productAiService;

    @BeforeEach
    void setUp() {
        productAiService = new ProductAiService(chatClientBuilder, categoryRepository);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
    }

    @Test
    void suggestListing_usesExistingActiveCategory() {
        Category category = Category.builder()
                .id(1L)
                .name("디지털기기")
                .description("스마트폰, 태블릿, 노트북 등")
                .active(true)
                .sortOrder(10)
                .build();

        when(categoryRepository.findAllByActiveTrue(any(Sort.class))).thenReturn(List.of(category));
        when(responseSpec.entity(any(Class.class))).thenAnswer(invocation -> aiSuggestion(
                invocation.getArgument(0),
                1L,
                null,
                null,
                "상태 좋은 아이폰입니다. 사진을 참고해 주세요."
        ));

        ProductListingSuggestionResponse response = productAiService.suggestListing(new ProductListingSuggestionRequest(
                "아이폰 15",
                "케이스 사용, 생활기스 조금",
                ProductCondition.GOOD
        ));

        assertThat(response.categoryId()).isEqualTo(1L);
        assertThat(response.categoryName()).isEqualTo("디지털기기");
        assertThat(response.description()).isEqualTo("상태 좋은 아이폰입니다. 사진을 참고해 주세요.");
        assertThat(response.createdCategory()).isFalse();
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void suggestListing_createsCategoryWhenNoSuitableActiveCategoryExists() {
        when(categoryRepository.findAllByActiveTrue(any(Sort.class))).thenReturn(List.of());
        when(categoryRepository.findByName("생활잡화")).thenReturn(Optional.empty());
        when(categoryRepository.findMaxSortOrder()).thenReturn(20);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            return Category.builder()
                    .id(3L)
                    .name(category.getName())
                    .description(category.getDescription())
                    .active(category.isActive())
                    .sortOrder(category.getSortOrder())
                    .build();
        });
        when(responseSpec.entity(any(Class.class))).thenAnswer(invocation -> aiSuggestion(
                invocation.getArgument(0),
                null,
                "생활잡화",
                "일상생활에서 사용하는 다양한 잡화와 소품 카테고리입니다.",
                "사용감이 적은 생활잡화입니다. 상세 상태는 사진을 참고해 주세요."
        ));

        ProductListingSuggestionResponse response = productAiService.suggestListing(new ProductListingSuggestionRequest(
                "원목 정리함",
                "책상 위에서 사용, 큰 흠집 없음",
                ProductCondition.GOOD
        ));

        assertThat(response.categoryId()).isEqualTo(3L);
        assertThat(response.categoryName()).isEqualTo("생활잡화");
        assertThat(response.createdCategory()).isTrue();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category savedCategory = captor.getValue();
        assertThat(savedCategory.getName()).isEqualTo("생활잡화");
        assertThat(savedCategory.getDescription()).isEqualTo("일상생활에서 사용하는 다양한 잡화와 소품 카테고리입니다.");
        assertThat(savedCategory.isActive()).isTrue();
        assertThat(savedCategory.getSortOrder()).isEqualTo(30);
    }

    private Object aiSuggestion(Class<?> type, Long categoryId, String categoryName, String categoryDescription, String description) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(Long.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(categoryId, categoryName, categoryDescription, description);
    }
}
