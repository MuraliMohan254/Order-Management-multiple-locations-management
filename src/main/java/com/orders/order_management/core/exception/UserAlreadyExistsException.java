package com.orders.order_management.core.exception;


import com.orders.order_management.auth.entity.User;
public class UserAlreadyExistsException extends BaseCustomException {
    public UserAlreadyExistsException(String username) {
        super("User with username '" + username + "' already exists", "USER_ALREADY_EXISTS");
    }
}
