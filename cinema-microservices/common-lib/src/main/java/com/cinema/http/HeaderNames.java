package com.cinema.http;

import com.cinema.Enum.UserEnum;

public final class HeaderNames {

    public static final String X_USER_ID = "X-User-ID";
    public static final String X_USER_ROLE = "X-User-Role";
    public static final String ROLE_ADMIN = UserEnum.UserRole.ADMIN.name();
    public static final String ROLE_MANAGER = UserEnum.UserRole.MANAGER.name();
    public static final String ROLE_STAFF = UserEnum.UserRole.STAFF.name();
    public static final String ROLE_CUSTOMER = UserEnum.UserRole.CUSTOMER.name();

    private HeaderNames() {
    }
}
