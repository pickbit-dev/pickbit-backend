package com.pickbit.productservice.api;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.library.dto.PageableRequest;
import com.pickbit.productservice.api.dto.request.ProductCreateRequest;
import com.pickbit.productservice.api.dto.request.ProductImageRequest;
import com.pickbit.productservice.api.dto.request.ProductUpdateRequest;
import com.pickbit.productservice.api.dto.response.ImageUploadResponse;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.application.ImageUploadService;
import com.pickbit.productservice.application.ProductService;
import com.pickbit.productservice.exception.InvalidImageFileException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 외부 클라이언트용 상품 API 컨트롤러.
 * 상품 등록, 조회, 수정, 삭제와 같은 상품 관리 기능을 제공합니다.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String NICKNAME_HEADER = "nickname";

    private final ProductService productService;
    private final ImageUploadService imageUploadService;

    /**
     * 이미지를 NCP Object Storage에 업로드합니다.
     *
     * @param files 업로드할 이미지 파일 목록
     * @return 업로드된 이미지 URL 목록 (HTTP 201)
     */
    @PostMapping("/images")
    public ResponseEntity<List<ImageUploadResponse>> uploadImages(
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageUploadService.uploadImages(files));
    }

    /**
     * 신규 상품을 등록합니다. 상품 데이터와 이미지 파일을 한 번에 전송합니다.
     *
     * @param nickname 요청한 판매자 닉네임
     * @param request 등록할 상품 정보 (imageTypes, sortOrders 포함)
     * @param files 업로드할 이미지 파일 목록 (imageTypes, sortOrders와 순서 일치)
     * @return 등록된 상품 상세 정보 (HTTP 201)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDetailResponse> createProduct(
            @RequestHeader(NICKNAME_HEADER) String nickname,
            @RequestPart("request") @Valid ProductCreateRequest request,
            @RequestPart("files") List<MultipartFile> files
    ) {
        List<ImageUploadResponse> uploaded = imageUploadService.uploadImages(files);
        List<ProductImageRequest> imageRequests = buildImageRequests(uploaded, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(nickname, request, imageRequests));
    }

    private List<ProductImageRequest> buildImageRequests(
            List<ImageUploadResponse> uploaded, ProductCreateRequest request) {
        if (uploaded.size() != request.imageTypes().size()
                || uploaded.size() != request.sortOrders().size()) {
            throw new InvalidImageFileException("파일 수와 imageTypes, sortOrders의 개수가 일치해야 합니다.");
        }
        return IntStream.range(0, uploaded.size())
                .mapToObj(i -> new ProductImageRequest(
                        uploaded.get(i).imageUrl(),
                        request.imageTypes().get(i),
                        request.sortOrders().get(i)))
                .toList();
    }

    /**
     * 상품 목록을 조회합니다.
     *
     * @param categoryId 카테고리별 조회를 위한 카테고리 ID
     * @param pageableRequest 페이지 및 정렬 요청 정보
     * @return 상품 요약 정보 목록
     */
    @GetMapping
    public ResponseEntity<PageResponse<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(productService.getProducts(categoryId, pageableRequest.toPageable(20)));
    }

    /**
     * 상품 상세 정보를 조회합니다.
     *
     * @param id 조회할 상품 ID
     * @return 상품 상세 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    /**
     * 기존 상품 정보를 수정합니다.
     *
     * @param nickname 요청한 판매자 닉네임
     * @param id 수정할 상품 ID
     * @param request 수정할 상품 정보
     * @return 수정된 상품 상세 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @RequestHeader(NICKNAME_HEADER) String nickname,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(nickname, id, request));
    }

    /**
     * 상품을 삭제합니다.
     *
     * @param nickname 요청한 판매자 닉네임
     * @param id 삭제할 상품 ID
     * @return 응답 본문 없음 (HTTP 204)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @RequestHeader(NICKNAME_HEADER) String nickname,
            @PathVariable Long id
    ) {
        productService.deleteProduct(nickname, id);
        return ResponseEntity.noContent().build();
    }
}
