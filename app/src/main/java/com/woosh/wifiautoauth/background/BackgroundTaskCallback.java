package com.woosh.wifiautoauth.background;

import com.woosh.wifiautoauth.utils.ResultHolder;

public interface BackgroundTaskCallback<T> {
    void onComplete(ResultHolder<T> resultHolder);
}
