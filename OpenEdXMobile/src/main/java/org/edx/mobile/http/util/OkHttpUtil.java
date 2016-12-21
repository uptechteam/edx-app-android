package org.edx.mobile.http.util;

import android.content.Context;
import android.support.annotation.NonNull;

import org.edx.mobile.BuildConfig;
import org.edx.mobile.R;
import org.edx.mobile.http.interceptor.CustomCacheQueryInterceptor;
import org.edx.mobile.http.interceptor.JsonMergePatchInterceptor;
import org.edx.mobile.http.interceptor.NewVersionBroadcastInterceptor;
import org.edx.mobile.http.interceptor.NoCacheHeaderStrippingInterceptor;
import org.edx.mobile.http.interceptor.OauthHeaderRequestInterceptor;
import org.edx.mobile.http.authenticator.OauthRefreshTokenAuthenticator;
import org.edx.mobile.http.interceptor.StaleIfErrorHandlingInterceptor;
import org.edx.mobile.http.interceptor.StaleIfErrorInterceptor;
import org.edx.mobile.http.interceptor.UserAgentInterceptor;

import java.io.File;
import java.util.List;

import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class OkHttpUtil {
    private static final int cacheSize = 10 * 1024 * 1024; // 10 MiB

    public static OkHttpClient getClient(@NonNull Context context) {
        return getClient(context, false, false);
    }

    public static OkHttpClient getOAuthBasedClient(@NonNull Context context) {
        return getClient(context, true, false);
    }

    public static OkHttpClient getOAuthBasedClientWithOfflineCache(@NonNull Context context) {
        return getClient(context, true, true);
    }

    private static OkHttpClient getClient(@NonNull Context context,
                                          boolean isOAuthBased, boolean usesOfflineCache) {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
        List<Interceptor> interceptors = builder.interceptors();
        if (usesOfflineCache) {
            final File cacheDirectory = new File(context.getFilesDir(), "http-cache");
            if (!cacheDirectory.exists()) {
                cacheDirectory.mkdirs();
            }
            final Cache cache = new Cache(cacheDirectory, cacheSize);
            builder.cache(cache);
            interceptors.add(new StaleIfErrorInterceptor());
            interceptors.add(new StaleIfErrorHandlingInterceptor());
            interceptors.add(new CustomCacheQueryInterceptor(context));
            builder.networkInterceptors().add(new NoCacheHeaderStrippingInterceptor());
        }
        interceptors.add(new JsonMergePatchInterceptor());
        interceptors.add(new UserAgentInterceptor(
                System.getProperty("http.agent") + " " +
                        context.getString(R.string.app_name) + "/" +
                        BuildConfig.APPLICATION_ID + "/" +
                        BuildConfig.VERSION_NAME));
        if (isOAuthBased) {
            interceptors.add(new OauthHeaderRequestInterceptor(context));
        }
        interceptors.add(new NewVersionBroadcastInterceptor());
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            interceptors.add(loggingInterceptor);
        }

        builder.authenticator(new OauthRefreshTokenAuthenticator(context));

        return builder.build();
    }
}
