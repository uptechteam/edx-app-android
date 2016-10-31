package org.edx.mobile.http.cookie;

import android.content.Context;
import android.support.annotation.NonNull;
import android.webkit.CookieManager;

import org.edx.mobile.util.Config;

import java.util.Collections;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import roboguice.RoboGuice;

public class WebViewCookieManagerDelegate implements CookieJar {
    private final String apiHostUrl;

    public WebViewCookieManagerDelegate(@NonNull final Context context) {
        final Config config = RoboGuice.getInjector(context).getInstance(Config.class);
        apiHostUrl = config.getApiHostURL();
    }

    @Override
    public List<Cookie> loadForRequest(@NonNull final HttpUrl url) {
        return Collections.emptyList();
    }

    @Override
    public void saveFromResponse(@NonNull final HttpUrl url, @NonNull final List<Cookie> cookies) {
        final String urlString = url.toString();
        if (urlString.equals(apiHostUrl) || urlString.startsWith(apiHostUrl + '/')) {
            CookieManager.getInstance().setCookie(urlString,
                    cookies.get(cookies.size() - 1).toString());
        }
    }
}
