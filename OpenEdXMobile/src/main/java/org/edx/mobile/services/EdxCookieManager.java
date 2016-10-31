package org.edx.mobile.services;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.CookieSyncManager;

import com.google.inject.Inject;

import org.edx.mobile.authentication.LoginService;
import org.edx.mobile.event.SessionIdRefreshEvent;
import org.edx.mobile.http.callback.Callback;
import org.edx.mobile.logger.Logger;

import java.io.File;
import java.util.concurrent.TimeUnit;

import de.greenrobot.event.EventBus;
import okhttp3.RequestBody;
import retrofit2.Call;
import roboguice.RoboGuice;

/**
 *  A central place for course data model transformation
 */
public class EdxCookieManager {

    // We'll assume that cookies are valid for at least one hour; after that
    // they'll be requeried on API levels lesser than Marshmallow (which
    // provides an error callback with the HTTP error code) prior to usage.
    private static final long FRESHNESS_INTERVAL = TimeUnit.HOURS.toMillis(1);

    private long authSessionCookieExpiration = -1;

    protected final Logger logger = new Logger(getClass().getName());

    private static EdxCookieManager instance;

    @Inject
    private LoginService loginService;

    private Call<RequestBody> loginCall;

    public static synchronized EdxCookieManager getSharedInstance(@NonNull final Context context) {
        if ( instance == null ) {
            instance = new EdxCookieManager();
            RoboGuice.getInjector(context).injectMembers(instance);
        }
        return instance;
    }

    public void clearWebWiewCookie(Context context){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().removeAllCookie();
        } else {
            try {
                CookieSyncManager.createInstance(context);
                android.webkit.CookieManager.getInstance().removeAllCookie();
            }catch (Exception ex){
                logger.debug(ex.getMessage());
            }
        }
        authSessionCookieExpiration = -1;
    }

    public synchronized  void tryToRefreshSessionCookie( ){
        if (loginCall == null || loginCall.isCanceled()) {
            loginCall = loginService.login();
            loginCall.enqueue(new Callback<RequestBody>() {
                @Override
                protected void onResponse(@NonNull final RequestBody responseBody) {
                    authSessionCookieExpiration = System.currentTimeMillis() + FRESHNESS_INTERVAL;
                    EventBus.getDefault().post(new SessionIdRefreshEvent(true));
                    loginCall = null;
                }

                @Override
                protected void onFailure(@NonNull Throwable error) {
                    super.onFailure(error);
                    EventBus.getDefault().post(new SessionIdRefreshEvent(false));
                    loginCall = null;
                }
            });
        }
    }

    public boolean isSessionCookieMissingOrExpired() {
        return authSessionCookieExpiration < System.currentTimeMillis();
    }
}
