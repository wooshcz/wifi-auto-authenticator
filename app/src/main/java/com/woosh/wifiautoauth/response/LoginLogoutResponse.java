package com.woosh.wifiautoauth.response;

public class LoginLogoutResponse {

    private int response;

    public LoginLogoutResponse(int response) {
        this.response = response;
    }

    public int getResponse() {
        return response;
    }

    public void setResponse(int response) {
        this.response = response;
    }
}
