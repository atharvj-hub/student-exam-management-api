package com.internship.student_exam_api.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * PagedResponse — stable pagination wrapper for all paginated API endpoints.
 *
 * WHY a custom wrapper instead of returning Spring's Page<T> directly?
 *   Spring's Page<T> serializes internal Spring types into the JSON response
 *   (e.g. "pageable", "sort" objects with framework-specific structure).
 *   This couples API consumers to Spring's internal representation.
 *   If Spring changes its Page serialization format, consumer contracts break.
 *
 *   This record exposes only the fields consumers actually need:
 *     content       → the items on this page
 *     page          → current page number (0-indexed)
 *     size          → requested page size
 *     totalElements → total records across all pages
 *     totalPages    → total number of pages
 *     last          → true if this is the final page
 *
 * Usage in controllers:
 *   return ResponseEntity.ok(PagedResponse.from(service.getAllResults(pageable)));
 */
public record PagedResponse<T>(
        List<T>  content,
        int      page,
        int      size,
        long     totalElements,
        int      totalPages,
        boolean  last
) {
    /**
     * Static factory — converts Spring's Page<T> to our stable contract.
     * Called at the controller layer, never in service or repository.
     */
    public static <T> PagedResponse<T> from(Page<T> springPage) {
        return new PagedResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast()
        );
    }
}
