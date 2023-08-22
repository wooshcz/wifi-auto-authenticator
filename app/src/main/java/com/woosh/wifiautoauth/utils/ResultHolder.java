package com.woosh.wifiautoauth.utils;

public abstract class ResultHolder<T> {
    private ResultHolder() {
    }

    public static final class Success<T> extends ResultHolder<T> {
        public T data;

        public Success(T data) {
            this.data = data;
        }
    }

    public static final class Error<T> extends ResultHolder<T> {
        public Exception exception;

        public Error(Exception exception) {
            this.exception = exception;
        }
    }
}
