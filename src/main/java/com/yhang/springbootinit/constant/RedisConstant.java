package com.yhang.springbootinit.constant;

public interface RedisConstant {
    String USER_SIGN_IN="user:signs";
    static String getUserSignIn(int year,long userId)
    {
        return String.format("%S:%S:%S",USER_SIGN_IN,year,userId);
    }
}
