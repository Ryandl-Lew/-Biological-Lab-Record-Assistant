package com.bionote.common;

import java.util.List;

public record PagedResponse<T>(List<T> data, Meta meta) {
    public record Meta(int page, int size, long totalElements, int totalPages) {}
    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long total) {
        return new PagedResponse<>(items, new Meta(page, size, total, size == 0 ? 0 : (int)Math.ceil((double)total/size)));
    }
}

