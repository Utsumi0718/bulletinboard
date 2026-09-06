package com.example.bulletinboard.dto.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 【クラスの役割】
 * ページングされたAPIレスポンスを
 * フロントエンドへ返すための共通DTOです。
 *
 * Spring DataのPageをそのままAPIレスポンスとして返さず、
 * 必要なページ情報だけを公開します。
 *
 * @param content       現在のページに含まれるデータ一覧
 * @param page          現在のページ番号（0始まり）
 * @param size          1ページあたりの件数
 * @param totalElements 条件に一致する全データ件数
 * @param totalPages    全ページ数
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * Spring DataのPageを
     * PageResponseへ変換します。
     *
     * @param page 変換元のPage
     * @param <T>  ページ内データの型
     * @return APIレスポンス用PageResponse
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}