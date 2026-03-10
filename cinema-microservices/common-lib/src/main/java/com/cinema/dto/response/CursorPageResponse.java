package com.cinema.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CursorPageResponse<T> {
    private List<T> data;
    private String nextCursor;  // null nếu đây là trang cuối
    private String prevCursor;  // null nếu đây là trang đầu
    private boolean hasNext;    // true nếu còn dữ liệu phía sau
    private int size;           // số bản ghi thực tế trong data
}