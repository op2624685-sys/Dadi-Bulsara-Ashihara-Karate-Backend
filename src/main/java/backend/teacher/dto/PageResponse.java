package backend.teacher.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Standardized, framework-agnostic pagination envelope. Wraps a Spring
 * {@link Page} so the frontend gets a stable shape
 * ({@code content, page, size, totalElements, totalPages, ...}) instead of
 * Spring's {@code PageImpl} JSON (which leaks Hibernate internals).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        int numberOfElements
) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getNumberOfElements());
    }
}
