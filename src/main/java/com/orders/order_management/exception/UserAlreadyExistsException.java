package com.orders.order_management.exception;

public class UserAlreadyExistsException extends BaseCustomException {
    public UserAlreadyExistsException(String username) {
        super("User with username '" + username + "' already exists", "USER_ALREADY_EXISTS");
    }
}
