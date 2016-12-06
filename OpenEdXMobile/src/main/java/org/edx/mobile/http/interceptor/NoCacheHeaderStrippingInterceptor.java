package org.edx.mobile.http.interceptor;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * An OkHttp network interceptor that handles the qualified no-cache="header" Cache-Control
 * directive in the response, which is currently handled by the OkHttp client just like the
 * unqualified directive. The interceptor strips these headers and the directive itself so that the
 * OkHttp client doesn't see it. It also takes care of passing along any cookies to the cookie jar
 * before stripping them. A ticket has been filed in the OkHttp project to handle this properly at
 * https://github.com/square/okhttp/issues/3022
 */
public class NoCacheHeaderStrippingInterceptor implements Interceptor {
    private static final Pattern PATTERN_ONLY_NO_CACHE_HEADER = Pattern.compile(
            "(^\\s*)no-cache\\s*=\\s*\"([^,;\"\\s]+)\"\\s*");
    private static final Pattern PATTERN_NO_CACHE_HEADER = Pattern.compile(
            "((^|[,;])\\s*)no-cache\\s*=\\s*\"([^,;\\s]+)\"(\\s*)");
    private static final String REPLACEMENT_STRIP_NO_CACHE = "$1$3";
    private static final int GROUP_NO_CACHE_HEADERS = 2;

    @NonNull
    private final CookieJar cookieJar;

    public NoCacheHeaderStrippingInterceptor(@NonNull final CookieJar cookieJar) {
        this.cookieJar = cookieJar;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull final Chain chain) throws IOException {
        final Request request = chain.request();
        final Response response = chain.proceed(request);
        final Headers headers = response.headers();
        Headers.Builder strippedHeadersBuilder = null;
        List<String> headersToStrip = null;
        for (int i = 0, headersCount = headers.size(); i < headersCount; i++) {
            final String headerName = headers.name(i);
            final String headerValue = headers.value(i);
            if (headerName.equalsIgnoreCase("Cache-Control")) {
                boolean noCacheHeaderFound;
                boolean onlyNoCacheHeaderFound;
                Matcher directiveMatcher = PATTERN_ONLY_NO_CACHE_HEADER.matcher(headerValue);
                noCacheHeaderFound = onlyNoCacheHeaderFound = directiveMatcher.find();
                if (!onlyNoCacheHeaderFound) {
                    directiveMatcher = PATTERN_NO_CACHE_HEADER.matcher(headerValue);
                    noCacheHeaderFound = directiveMatcher.find();
                }
                if (noCacheHeaderFound) {
                    if (strippedHeadersBuilder == null) {
                        strippedHeadersBuilder = new Headers.Builder();
                        for (int j = 0; j < i; j++) {
                            strippedHeadersBuilder.add(headers.name(j), headers.value(j));
                        }
                        headersToStrip = new ArrayList<>();
                    }
                    final StringBuffer newHeaderValueBuffer = new StringBuffer();
                    do {
                        /* Verify that the directive is ended properly by the matcher either
                         * reaching the end of the header value string, or a comma or semicolon
                         * separator. Otherwise this directive isn't validly constructed, it will be
                         * skipped.
                         */
                        final int nextCharIndex = directiveMatcher.end();
                        if (nextCharIndex != headerValue.length()) {
                            final char nextChar = headerValue.charAt(nextCharIndex);
                            if (nextChar != ',' && nextChar != ';') {
                                continue;
                            }
                        }
                        Collections.addAll(headersToStrip,
                                directiveMatcher.group(GROUP_NO_CACHE_HEADERS).split(","));
                        if (!onlyNoCacheHeaderFound) {
                            directiveMatcher.appendReplacement(
                                    newHeaderValueBuffer, REPLACEMENT_STRIP_NO_CACHE);
                        }
                    } while (directiveMatcher.find());
                    if (!onlyNoCacheHeaderFound) {
                        directiveMatcher.appendTail(newHeaderValueBuffer);
                        strippedHeadersBuilder.add(headerName, newHeaderValueBuffer.toString());
                    }
                    continue;
                }
            }
            if (strippedHeadersBuilder != null) {
                strippedHeadersBuilder.add(headerName, headerValue);
            }
        }
        if (strippedHeadersBuilder == null) {
            return response;
        }
        final HttpUrl url = request.url();
        List<Cookie> cookies = null;
        for (final String headerToStrip : headersToStrip) {
            strippedHeadersBuilder.removeAll(headerToStrip);
            if (headerToStrip.equalsIgnoreCase("Set-Cookie")) {
                if (cookieJar != CookieJar.NO_COOKIES) {
                    for (final String cookieString : headers.values(headerToStrip)) {
                        Cookie cookie = Cookie.parse(url, cookieString);
                        if (cookie != null) {
                            if (cookies == null) {
                                cookies = new ArrayList<>();
                            }
                            cookies.add(cookie);
                        }
                    }
                }
            }
        }
        if (cookies != null) {
            cookieJar.saveFromResponse(url, cookies);
        }
        return response.newBuilder()
                .headers(strippedHeadersBuilder.build())
                .build();
    }
}
