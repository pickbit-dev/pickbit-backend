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
@Transactional(readOnly = true)
public class ProductAiService {

    private static final Sort CATEGORY_SORT = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));

    private final ChatClient.Builder chatClientBuilder;
    private final CategoryRepository categoryRepository;

    public ProductListingSuggestionResponse suggestListing(ProductListingSuggestionRequest request) {

        List<Category> categories = categoryRepository.findAllByActiveTrue(CATEGORY_SORT);
        if (categories.isEmpty()) {
            throw new ProductAiSuggestionException("활성화된 카테고리가 없어 추천할 수 없습니다.");
        }

        Map<Long, Category> categoryById = categories.stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        AiListingSuggestion suggestion = chatClientBuilder.build()
                .prompt()
                .system(systemPrompt())
                .user(userPrompt(request, categories))
                .call()
                .entity(AiListingSuggestion.class);

        if (suggestion == null || suggestion.categoryId() == null || !StringUtils.hasText(suggestion.description())) {
            throw new ProductAiSuggestionException("AI 추천 결과가 올바르지 않습니다.");
        }

        Category category = categoryById.get(suggestion.categoryId());

        if (category == null) {
            throw new ProductAiSuggestionException("AI가 존재하지 않는 카테고리를 추천했습니다. categoryId=" + suggestion.categoryId());
        }

        return new ProductListingSuggestionResponse(
                category.getId(),
                category.getName(),
                suggestion.description().trim()
        );
    }

    private String systemPrompt() {
        return """
                너는 경매 상품 등록을 도와주는 한국어 AI다.
                반드시 제공된 카테고리 목록 중 하나의 categoryId만 선택한다.
                상품 설명은 과장 없이 자연스럽고 신뢰감 있는 판매글로 작성한다.
                상품 설명에는 입력에 없는 구성품, 성능, 하자 여부를 지어내지 않는다.
                응답은 categoryId, description 필드를 가진 JSON 객체로만 반환한다.
                """;
    }

    private String userPrompt(ProductListingSuggestionRequest request, List<Category> categories) {
        return """
                카테고리 목록:
                %s
                
                상품 제목:
                %s
                
                사용자 메모:
                %s
                
                상품 컨디션:
                %s
                
                응답 형식:
                {"categoryId": 1, "description": "상품 설명"}
                """.formatted(
                formatCategories(categories),
                request.title(),
                StringUtils.hasText(request.memo()) ? request.memo() : "없음",
                request.productCondition() == null ? "미입력" : request.productCondition().getDescription()
        );
    }

    private String formatCategories(List<Category> categories) {
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
            String description
    ) {
    }
}
