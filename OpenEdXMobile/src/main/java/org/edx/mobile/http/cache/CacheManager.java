package org.edx.mobile.http.cache;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.edx.mobile.logger.Logger;
import org.edx.mobile.util.IOUtils;
import org.edx.mobile.util.Sha1Util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * The cache manager for HTTP responses. The cache is stored on the filesystem, within the
 * application directory.
 *
 * @deprecated This is deprecated since with the transition to OkHttp, we're now relying on it's own
 * automatic caching mechanism. However, this is still being kept around as a read-only resource for
 * a while, being queried as a last resort by as a last resort by the CustomCacheQueryInterceptor,
 * to facilitate the transition without any user-facing issues. This may be removed later after a
 * significant percentage of the user base have upgraded to a version that uses the OkHttp API in
 * the Courseware module.
 */
@Singleton
@Deprecated
public class CacheManager {
    /**
     * The logger for this class.
     */
    private final Logger logger = new Logger(getClass().getName());

    /**
     * The application context.
     */
    @NonNull
    private final Context context;

    /**
     * Create a new instance of the cache manager.
     *
     * @param context The application context, to use for querying the app directory location.
     */
    @Inject
    public CacheManager(@NonNull final Context context) {
        this.context = context;
    }

    /**
     * Check whether there is a response body cached for the provided URL, and return the result.
     *
     * @param url The response URL.
     * @return True if there is a cached response body available, and false otherwise.
     */
    public boolean has(@NonNull final String url) {
        final File file = getFile(url);
        return file != null && file.exists();
    }

    /**
     * Get the cached response body for the provided URL.
     *
     * @param url The response URL.
     * @return The cached response body if available, and null otherwise.
     */
    @Nullable
    public String get(@NonNull final String url) {
        final File file = getFile(url);
        if (file != null) {
            try {
                return IOUtils.toString(file, Charset.defaultCharset());
            } catch (IOException e) {
                logger.error(e, true);
            }
        }
        return null;
    }

    /**
     * Remove the cached response body for the provided URL.
     *
     * @param url The response URL.
     */
    public void remove(@NonNull final String url) {
        final File file = getFile(url);
        if (file != null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * Return the cache file for the provided URL.
     *
     * @param url The response URL.
     * @return The cache file, or null if the cache directory couldn't be created.
     */
    @Nullable
    private File getFile(@NonNull final String url) {
        final File cacheDir = new File(context.getFilesDir(), "http-cache");
        if (cacheDir.mkdirs() || cacheDir.isDirectory()) {
            final String hash = Sha1Util.SHA1(url);
            return new File(cacheDir, hash);
        }
        return null;
    }
}
