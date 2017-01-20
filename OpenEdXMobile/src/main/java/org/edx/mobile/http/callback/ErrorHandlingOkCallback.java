package org.edx.mobile.http.callback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.edx.mobile.http.HttpStatusException;
import org.edx.mobile.util.images.ErrorUtils;
import org.edx.mobile.view.common.TaskMessageCallback;
import org.edx.mobile.view.common.TaskProgressCallback;

import java.io.IOException;
import java.lang.reflect.Type;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import roboguice.RoboGuice;

/**
 * Generic abstract implementation of OkHttps's {@link Callback}
 * interface, that takes care of converting the response body to an
 * object of the specified model class, and delivering status and
 * error information to the proper callbacks. It also provides (and
 * delegates to) a simpler callback interface for subclasses,
 * stripping out unnecessary parameters, and redirecting all responses
 * with error codes to the failure callback method. The callbacks are
 * guaranteed to be invoked on the main thread.
 */
public abstract class ErrorHandlingOkCallback<T> implements Callback {
    /**
     * A Handler for the main looper, for delivering messages on the main thread.
     */
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * A Context for resolving the error message strings.
     */
    @NonNull
    private final Context context;

    /**
     * The response body type.
     */
    @NonNull
    private final Type responseBodyType;

    /**
     * The trigger for initiating the call. This is used to determine the type of error message to
     * deliver.
     */
    @NonNull
    private final CallTrigger callTrigger;

    /**
     * The callback to invoke on start and finish of the request.
     */
    @Nullable
    private final TaskProgressCallback progressCallback;

    /**
     * The callback to invoke for delivering any error messages.
     */
    @Nullable
    private final TaskMessageCallback messageCallback;

    /**
     * The Gson instance for converting the response body to the desired type.
     */
    @Inject
    private Gson gson;

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings.
     * @param responseBodyClass The response body class.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     * @param progressCallback The callback to invoke on start and finish of the request. Note that
     *                         since no callback method in this class is invoked upon request
     *                         initiation, it assumes that it's being initiated immediately, and
     *                         thus invokes that start callback immediately as well.
     * @param messageCallback The callback to invoke for delivering any error messages.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final Class<T> responseBodyClass,
                                   @NonNull final CallTrigger callTrigger,
                                   @Nullable final TaskProgressCallback progressCallback,
                                   @Nullable final TaskMessageCallback messageCallback) {
        this(context, (Type) responseBodyClass, callTrigger, progressCallback, messageCallback);
    }

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings.
     * @param responseBodyTypeToken The response body type token.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     * @param progressCallback The callback to invoke on start and finish of the request. Note that
     *                         since no callback method in this class is invoked upon request
     *                         initiation, it assumes that it's being initiated immediately, and
     *                         thus invokes that start callback immediately as well.
     * @param messageCallback The callback to invoke for delivering any error messages.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final TypeToken<T> responseBodyTypeToken,
                                   @NonNull final CallTrigger callTrigger,
                                   @Nullable final TaskProgressCallback progressCallback,
                                   @Nullable final TaskMessageCallback messageCallback) {
        this(context, responseBodyTypeToken.getType(),
                callTrigger, progressCallback, messageCallback);
    }

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings. Note that for convenience,
     *                this will be checked to determine whether it's implementing any of the
     *                callback interfaces, and will be registered as such if so. If this is not the
     *                desired outcome, then one of the alternative constructors should be used
     *                instead, with the relevant callback parameters explicitly passed as null (this
     *                may require casting the null in case of ambiguity when using a constructor
     *                that only sets one callback explicitly).
     * @param responseBodyClass The response body class.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final Class<T> responseBodyClass,
                                   @NonNull final CallTrigger callTrigger) {
        this(context, responseBodyClass, callTrigger,
                context instanceof TaskProgressCallback ? (TaskProgressCallback) context : null,
                context instanceof TaskMessageCallback ? (TaskMessageCallback) context : null);
    }

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings. Note that for convenience,
     *                this will be checked to determine whether it's implementing any of the
     *                callback interfaces, and will be registered as such if so. If this is not the
     *                desired outcome, then one of the alternative constructors should be used
     *                instead, with the relevant callback parameters explicitly passed as null (this
     *                may require casting the null in case of ambiguity when using a constructor
     *                that only sets one callback explicitly).
     * @param responseBodyTypeToken The response body type token.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final TypeToken<T> responseBodyTypeToken,
                                   @NonNull final CallTrigger callTrigger) {
        this(context, responseBodyTypeToken, callTrigger,
                context instanceof TaskProgressCallback ? (TaskProgressCallback) context : null,
                context instanceof TaskMessageCallback ? (TaskMessageCallback) context : null);
    }

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings. Note that for convenience,
     *                this will be checked to determine whether it's implementing the
     *                {@link TaskMessageCallback} interface, and will be registered as such if so.
     *                If this is not the desired outcome, then the other constructor should be used
     *                that takes this callback parameter, and it should be explicitly set as null.
     * @param responseBodyClass The response body Class.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     * @param progressCallback The callback to invoke on start and finish of the request. Note that
     *                         since no callback method in this class is invoked upon request
     *                         initiation, it assumes that it's being initiated immediately, and
     *                         thus invokes that start callback immediately as well.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final Class<T> responseBodyClass,
                                   @NonNull final CallTrigger callTrigger,
                                   @Nullable final TaskProgressCallback progressCallback) {
        this(context, responseBodyClass, callTrigger,
                progressCallback,
                context instanceof TaskMessageCallback ? (TaskMessageCallback) context : null);
    }

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings. Note that for convenience,
     *                this will be checked to determine whether it's implementing the
     *                {@link TaskMessageCallback} interface, and will be registered as such if so.
     *                If this is not the desired outcome, then the other constructor should be used
     *                that takes this callback parameter, and it should be explicitly set as null.
     * @param responseBodyTypeToken The response body type token.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     * @param progressCallback The callback to invoke on start and finish of the request. Note that
     *                         since no callback method in this class is invoked upon request
     *                         initiation, it assumes that it's being initiated immediately, and
     *                         thus invokes that start callback immediately as well.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final TypeToken<T> responseBodyTypeToken,
                                   @NonNull final CallTrigger callTrigger,
                                   @Nullable final TaskProgressCallback progressCallback) {
        this(context, responseBodyTypeToken, callTrigger,
                progressCallback,
                context instanceof TaskMessageCallback ? (TaskMessageCallback) context : null);
    }

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings. Note that for convenience,
     *                this will be checked to determine whether it's implementing the
     *                {@link TaskProgressCallback} interface, and will be registered as such if so.
     *                If this is not the desired outcome, then the other constructor should be used
     *                that takes this callback parameter, and it should be explicitly set as null.
     * @param responseBodyClass The response body class.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     * @param messageCallback The callback to invoke for delivering any error messages.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final Class<T> responseBodyClass,
                                   @NonNull final CallTrigger callTrigger,
                                   @Nullable final TaskMessageCallback messageCallback) {
        this(context, responseBodyClass, callTrigger,
                context instanceof TaskProgressCallback ? (TaskProgressCallback) context : null,
                messageCallback);
    }

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings. Note that for convenience,
     *                this will be checked to determine whether it's implementing the
     *                {@link TaskProgressCallback} interface, and will be registered as such if so.
     *                If this is not the desired outcome, then the other constructor should be used
     *                that takes this callback parameter, and it should be explicitly set as null.
     * @param responseBodyTypeToken The response body type token.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     * @param messageCallback The callback to invoke for delivering any error messages.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final TypeToken<T> responseBodyTypeToken,
                                   @NonNull final CallTrigger callTrigger,
                                   @Nullable final TaskMessageCallback messageCallback) {
        this(context, responseBodyTypeToken, callTrigger,
                context instanceof TaskProgressCallback ? (TaskProgressCallback) context : null,
                messageCallback);
    }

    /**
     * Create a new instance of this class.
     *
     * @param context A Context for resolving the error message strings.
     * @param responseBodyType The response body type.
     * @param callTrigger The trigger for initiating the call. This is used to determine the type of
     *                    error message to deliver.
     * @param progressCallback The callback to invoke on start and finish of the request. Note that
     *                         since no callback method in this class is invoked upon request
     *                         initiation, it assumes that it's being initiated immediately, and
     *                         thus invokes that start callback immediately as well.
     * @param messageCallback The callback to invoke for delivering any error messages.
     */
    public ErrorHandlingOkCallback(@NonNull final Context context,
                                   @NonNull final Type responseBodyType,
                                   @NonNull final CallTrigger callTrigger,
                                   @Nullable final TaskProgressCallback progressCallback,
                                   @Nullable final TaskMessageCallback messageCallback) {
        this.context = context;
        this.responseBodyType = responseBodyType;
        this.callTrigger = callTrigger;
        this.progressCallback = progressCallback;
        this.messageCallback = messageCallback;
        // For the convenience of subclasses
        RoboGuice.injectMembers(context, this);
        if (progressCallback != null) {
            progressCallback.startProcess();
        }
    }

    /**
     * The original callback method invoked by OkHttp upon receiving an HTTP response. This method
     * definition provides extra information that's not needed by most individual callback
     * implementations, and is also invoked when HTTP error status codes are encountered (forcing
     * the implementation to manually check for success in each case). Therefore this implementation
     * delegates to {@link #onResponse(T)} in the case where it receives a successful HTTP status
     * code, and to {@link #onFailure(Throwable)} otherwise, passing an instance of
     * {@link HttpStatusException} with the relevant error status code. This method is declared as
     * final, as subclasses are meant to be implementing the abstract {@link #onResponse(T)} method
     * instead of this one.
     * <p>
     * This implementation takes care of delivering the appropriate error message to it's registered
     * callback, and invoking the callback for request process completion.
     *
     * @param call The Call object that was used to enqueue the request.
     * @param response The HTTP response data.
     */
    @Override
    public final void onResponse(@NonNull Call call, @NonNull Response response) {
        if (!response.isSuccessful()) {
            deliverFailure(new HttpStatusException(response));
        } else {
            final String responseBodyString;
            try {
                responseBodyString = response.body().string();
            } catch (IOException error) {
                deliverFailure(error);
                return;
            }
            final T responseBody = gson.fromJson(responseBodyString, responseBodyType);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (progressCallback != null) {
                        progressCallback.finishProcess();
                    }
                    onResponse(responseBody);
                }
            });
        }
    }

    /**
     * The original callback method invoked by OkHttp upon failure to receive an HTTP response,
     * whether due to cancellation, a connectivity problem, or a timeout. This method definition
     * provides extra information that's not needed by most individual callback implementations, so
     * this implementation only delegates to {@link #onFailure(Throwable)}. This method is declared
     * as final, as subclasses are meant to be implementing the abstract {@link #onResponse(T)}
     * method instead of this one.
     *
     * @param call The Call object that was used to enqueue the request.
     * @param error The cause of the request being interrupted.
     */
    @Override
    public final void onFailure(@NonNull Call call, @NonNull IOException error) {
        deliverFailure(error);
    }

    /**
     * Convenience method for taking care of invoking the failure callbacks and invoking the new
     * failure callback method in the case on an error, used by both the original failure callback
     * method, and the success callback method in the case where it encounters an HTTP error status
     * code.
     *
     * @param error An {@link IOException} if the request failed due to a network failure, or an
     *              {HttpResponseStatusException} if the failure was due to receiving an error code.
     */
    private void deliverFailure(@NonNull final Throwable error) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (progressCallback != null) {
                    progressCallback.finishProcess();
                }
                if (messageCallback != null) {
                    messageCallback.onMessage(callTrigger.getMessageType(),
                            ErrorUtils.getErrorMessage(error, context));
                }
                onFailure(error);
            }
        });
    }

    /**
     * Callback method for a successful HTTP response.
     *
     * @param responseBody The response body, converted to an instance of it's associated Java
     *                     class.
     */
    protected abstract void onResponse(@NonNull final T responseBody);

    /**
     * Callback method for when the HTTP response was not received successfully, whether due to
     * cancellation, a connectivity problem, or a timeout, or receiving an HTTP error status code.
     *
     * @param error An {@link IOException} if the request failed due to a network failure, or an
     *              {HttpResponseStatusException} if the failure was due to receiving an error code.
     */
    protected void onFailure(@NonNull final Throwable error) {}
}
