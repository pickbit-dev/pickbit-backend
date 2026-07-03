package com.pickbit.productservice.application;

import com.pickbit.productservice.api.dto.request.ProductListingSuggestionRequest;
import com.pickbit.productservice.api.dto.response.ProductListingSuggestionResponse;
import com.pickbit.productservice.domain.Category;
import com.pickbit.productservice.exception.ProductAiSuggestionException;
import com.pickbit.productservice.infrastructure.persistence.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductAiService {

    private static final Sort CATEGORY_SORT = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));
    private static final int CATEGORY_SORT_ORDER_STEP = 10;

    private final ChatClient.Builder chatClientBuilder;
    private final CategoryRepository categoryRepository;

    public ProductListingSuggestionResponse suggestListing(ProductListingSuggestionRequest request) {

        List<Category> categories = categoryRepository.findAllByActiveTrue(CATEGORY_SORT);

        Map<Long, Category> categoryById = categories.stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        AiListingSuggestion suggestion = chatClientBuilder.build()
                .prompt()
                .system(systemPrompt())
                .user(userPrompt(request, categories))
                .call()
                .entity(AiListingSuggestion.class);

        if (suggestion == null || !StringUtils.hasText(suggestion.description())) {
            throw new ProductAiSuggestionException("AI 추천 결과가 올바르지 않습니다.");
        }

        Category category;
        boolean createdCategory = false;

        if (suggestion.categoryId() != null) {
            category = resolveExistingCategory(suggestion, categoryById);
        } else {
            category = createCategory(suggestion);
            createdCategory = true;
        }

        return new ProductListingSuggestionResponse(
                category.getId(),
                category.getName(),
                suggestion.description().trim(),
                createdCategory
        );
    }

    private Category resolveExistingCategory(AiListingSuggestion suggestion, Map<Long, Category> categoryById) {
        if (StringUtils.hasText(suggestion.categoryName()) || StringUtils.hasText(suggestion.categoryDescription())) {
            throw new ProductAiSuggestionException("AI 추천 결과가 올바르지 않습니다. 기존 카테고리와 신규 카테고리 정보를 동시에 반환했습니다.");
        }

        Category category = categoryById.get(suggestion.categoryId());
        if (category == null) {
            throw new ProductAiSuggestionException("AI가 존재하지 않는 활성 카테고리를 추천했습니다. categoryId=" + suggestion.categoryId());
        }
        return category;
    }

    private Category createCategory(AiListingSuggestion suggestion) {
        String categoryName = trimToNull(suggestion.categoryName());
        String categoryDescription = trimToNull(suggestion.categoryDescription());

        if (categoryName == null || categoryDescription == null) {
            throw new ProductAiSuggestionException("AI가 신규 카테고리 정보를 올바르게 반환하지 않았습니다.");
        }
        if (categoryName.length() > 100) {
            throw new ProductAiSuggestionException("AI가 생성한 카테고리명이 너무 깁니다.");
        }
        if (categoryDescription.length() > 500) {
            throw new ProductAiSuggestionException("AI가 생성한 카테고리 설명이 너무 깁니다.");
        }

        return categoryRepository.findByName(categoryName)
                .map(category -> {
                    if (!category.isActive()) {
                        category.updateActive(true);
                    }
                    return category;
                })
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(categoryName)
                        .description(categoryDescription)
                        .active(true)
                        .sortOrder(categoryRepository.findMaxSortOrder() + CATEGORY_SORT_ORDER_STEP)
                        .build()));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String systemPrompt() {
        return """
                너는 경매 상품 등록을 도와주는 한국어 AI다.
                제공된 활성 카테고리 중 상품에 적합한 카테고리가 있으면 반드시 해당 categoryId만 선택한다.
                제공된 활성 카테고리 중 적합한 카테고리가 정말 없을 때만 categoryId를 null로 두고 신규 categoryName, categoryDescription을 제안한다.
                신규 카테고리는 중고거래/경매 플랫폼에서 재사용 가능한 일반 카테고리여야 하며, 브랜드명/모델명/개별 상품명으로 만들지 않는다.
                신규 categoryName은 100자 이하, categoryDescription은 500자 이하로 작성한다.
                상품 설명은 과장 없이 자연스럽고 신뢰감 있는 판매글로 작성한다.
                상품 설명에는 입력에 없는 구성품, 성능, 하자 여부를 지어내지 않는다.
                기존 카테고리를 선택할 때는 categoryName, categoryDescription을 null로 둔다.
                신규 카테고리를 제안할 때는 categoryId를 null로 둔다.
                응답은 categoryId, categoryName, categoryDescription, description 필드를 가진 JSON 객체로만 반환한다.
                """;
    }

    private String userPrompt(ProductListingSuggestionRequest request, List<Category> categories) {
        return """
                활성 카테고리 목록:
                %s
                
                상품 제목:
                %s
                
                사용자 메모:
                %s
                
                상품 컨디션:
                %s
                
                응답 형식:
                기존 카테고리를 선택하는 경우:
                {"categoryId": 1, "categoryName": null, "categoryDescription": null, "description": "상품 설명"}
                신규 카테고리가 필요한 경우:
                {"categoryId": null, "categoryName": "카테고리명", "categoryDescription": "카테고리 설명", "description": "상품 설명"}
                """.formatted(
                formatCategories(categories),
                request.title(),
                StringUtils.hasText(request.memo()) ? request.memo() : "없음",
                request.productCondition() == null ? "미입력" : request.productCondition().getDescription()
        );
    }

    private String formatCategories(List<Category> categories) {
        if (categories.isEmpty()) {
            return "- 활성 카테고리 없음. 상품에 맞는 일반 카테고리를 새로 제안하세요.";
        }
        return categories.stream()
                .map(category -> "- categoryId=%d, name=%s, description=%s".formatted(
                        category.getId(),
                        category.getName(),
                        category.getDescription()
                ))
                .collect(Collectors.joining("\n"));
    }

    private record AiListingSuggestion(
            Long categoryId,
            String categoryName,
            String categoryDescription,
            String description
    ) {
    }
}
