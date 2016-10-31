package org.edx.mobile.http.interceptor;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.inject.Inject;

import org.edx.mobile.http.HttpStatus;
import org.edx.mobile.http.cache.CacheManager;

import java.io.IOException;
import java.util.Date;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.CacheStrategy;
import okhttp3.internal.http.HttpEngine;
import okhttp3.internal.http.HttpMethod;
import roboguice.RoboGuice;

/**
 * An OkHttp interceptor that adds support for querying the deprecated {@link CacheManager}
 * initially, if the OkHttp client doesn't have the entry in it's own cache.
 *
 * @deprecated This is only provided so that the transition from the Apache client to OkHttp can be
 * performed smoothly without any user-facing issues. After a significant percentage of the userbase
 * have upgraded to a version that uses the OkHttp API in the Courseware module, this may be removed
 * along with the CacheManager class itself.
 */
@Deprecated
public class CustomCacheQueryInterceptor implements Interceptor {
    @Inject
    private CacheManager cacheManager;

    public CustomCacheQueryInterceptor(@NonNull final Context context) {
        RoboGuice.getInjector(context).injectMembers(this);
    }

    @Override
    public Response intercept(@NonNull final Chain chain) throws IOException {
        final Request request = chain.request();
        Response response = chain.proceed(request);
        final String urlString = request.url().toString();
        // If the OkHttp client
        if (response.cacheResponse() != null) {
            cacheManager.remove(urlString);
        } else {
            final String cachedBody = cacheManager.get(urlString);
            if (cachedBody != null) {
                /* Since we don't cache the metadata, the cached entries should always be assumed to
                 * have a 200 response code. The body is not provided in this response, because it
                 * would only be needed if we don't have a network response.
                 */
                Response cacheResponse = response.newBuilder()
                        .code(HttpStatus.OK)
                        .body(null)
                        .build();
                final CacheStrategy cacheStrategy = new CacheStrategy.Factory(
                        System.currentTimeMillis(), request, cacheResponse).get();
                cacheResponse = cacheStrategy.cacheResponse;
                if (cacheResponse != null) {
                    // If querying the server is forbidden, just return the cache response.
                    if (cacheStrategy.networkRequest == null) {
                        response = cacheResponse.newBuilder()
                                .cacheResponse(cacheResponse)
                                .body(ResponseBody.create(MediaType.parse("application/json"),
                                        cachedBody))
                                .build();
                    } else {
                        final Response networkResponse = response.networkResponse();
                        // If there is a network response in addition to the cache response, then do
                        // a conditional get.
                        if (shouldUseCachedResponse(cacheResponse, networkResponse)) {
                            response = cacheResponse.newBuilder()
                                    .headers(combine(cacheResponse.headers(), networkResponse.headers()))
                                    .cacheResponse(cacheResponse)
                                    .build();
                        } else {
                            response = response.newBuilder()
                                    .cacheResponse(cacheResponse)
                                    .build();
                            if (HttpEngine.hasBody(response) &&
                                    HttpMethod.invalidatesCache(request.method())) {
                                cacheManager.remove(urlString);
                            }
                        }
                    }
                }
            }
        }
        return response;
    }

    /**
     * Check whether the cached or network response is more suitable to return to the user agent,
     * and return the result.
     *
     * @param cachedResponse The cached response.
     * @param networkResponse The network response.
     * @return True if {@code cachedResponse} should be used; false if {@code networkResponse}
     * should be used.
     */
    private static boolean shouldUseCachedResponse(@NonNull final Response cachedResponse,
                                                   @NonNull final Response networkResponse) {
        if (networkResponse.code() == HttpStatus.NOT_MODIFIED) {
            return true;
        }

        /* The HTTP spec says that if the network's response is older than our cached response, we
         * may return the cache's response. Like Chrome (but unlike Firefox), the OkHttp client
         * prefers to return the newer response.
         */
        final Date lastModified = cachedResponse.headers().getDate("Last-Modified");
        if (lastModified != null) {
            final Date networkLastModified = networkResponse.headers().getDate("Last-Modified");
            if (networkLastModified != null
                    && networkLastModified.getTime() < lastModified.getTime()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Combine cached headers with network headers as defined by RFC 2616, 13.5.3.
     *
     * @param cachedHeaders The cache headers.
     * @param networkHeaders The network headers.
     * @return The combined headers.
     */
    private static Headers combine(@NonNull final Headers cachedHeaders,
                                   @NonNull final Headers networkHeaders) {
        final Headers.Builder result = new Headers.Builder();

        for (int i = 0, size = cachedHeaders.size(); i < size; i++) {
            final String fieldName = cachedHeaders.name(i);
            final String value = cachedHeaders.value(i);
            if ("Warning".equalsIgnoreCase(fieldName) && value.startsWith("1")) {
                continue; // Drop 100-level freshness warnings.
            }
            if (!isEndToEndHeader(fieldName) || networkHeaders.get(fieldName) == null) {
                result.add(fieldName, value);
            }
        }

        for (int i = 0, size = networkHeaders.size(); i < size; i++) {
            final String fieldName = networkHeaders.name(i);
            if ("Content-Length".equalsIgnoreCase(fieldName)) {
                continue; // Ignore content-length headers of validating responses.
            }
            if (isEndToEndHeader(fieldName)) {
                result.add(fieldName, networkHeaders.value(i));
            }
        }

        return result.build();
    }

    /**
     * Check whether a given field is an end-to-end header, as defined by RFC 2616, 13.5.1, and
     * return the result.
     *
     * @param fieldName The header field name.
     * @return True if {@code fieldName} is an end-to-end HTTP header, and false otherwise.
     */
    private static boolean isEndToEndHeader(String fieldName) {
        return !"Connection".equalsIgnoreCase(fieldName)
                && !"Keep-Alive".equalsIgnoreCase(fieldName)
                && !"Proxy-Authenticate".equalsIgnoreCase(fieldName)
                && !"Proxy-Authorization".equalsIgnoreCase(fieldName)
                && !"TE".equalsIgnoreCase(fieldName)
                && !"Trailers".equalsIgnoreCase(fieldName)
                && !"Transfer-Encoding".equalsIgnoreCase(fieldName)
                && !"Upgrade".equalsIgnoreCase(fieldName);
    }
}
