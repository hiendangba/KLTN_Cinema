package com.cinema.Enum;

public class FilmEnum {
    public enum AgeRating {
        RATING_1,    // Mọi lứa tuổi (0+)
        RATING_2,    // Cần hướng dẫn của phụ huynh (6+)
        RATING_3,    // Trẻ em trên 13 tuổi (13+)
        RATING_4,    // Trẻ em trên 16 tuổi (16+)
        RATING_5     // Chỉ dành cho người từ 18 tuổi trở lên (18+)
    }

    public enum FilmStatus {
        COMING_SOON,   // Phim sắp khởi chiếu
        NOW_SHOWING,   // Đang chiếu
        ENDED,         // Đã kết thúc suất chiếu
        ARCHIVED       // Lưu trữ
    }
}
