package cinema.seat_service.util;

public final class SeatCodeGenerator {
    private SeatCodeGenerator() {
    }

    public static String fromRowCol(int row, int col) {
        return toRowLabel(row) + col;
    }

    private static String toRowLabel(int row) {
        int n = row;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.append((char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.reverse().toString();
    }
}
