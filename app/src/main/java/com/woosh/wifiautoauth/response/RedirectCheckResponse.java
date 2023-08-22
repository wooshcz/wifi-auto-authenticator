package com.woosh.wifiautoauth.response;

public class RedirectCheckResponse {

    public RedirectCheckResponse(String detectedUrl, boolean captivePortalDetected) {
        this.detectedUrl = detectedUrl;
        this.captivePortalDetected = captivePortalDetected;
    }

    private String detectedUrl;
    private boolean captivePortalDetected;

    public String getDetectedUrl() {
        return detectedUrl;
    }

    public void setDetectedUrl(String detectedUrl) {
        this.detectedUrl = detectedUrl;
    }

    public boolean isCaptivePortalDetected() {
        return captivePortalDetected;
    }

    public void setCaptivePortalDetected(boolean captivePortalDetected) {
        this.captivePortalDetected = captivePortalDetected;
    }
}
