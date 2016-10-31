package org.edx.mobile.util;

import android.content.Context;

import org.edx.mobile.http.HttpResponseStatusException;
import org.edx.mobile.http.util.OkHttpUtil;
import org.edx.mobile.logger.Logger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public abstract class TranscriptDownloader implements Runnable {

    private String srtUrl;
    private OkHttpClient okHttpClient;
    private final Logger logger = new Logger(TranscriptDownloader.class.getName());

    public TranscriptDownloader(Context context, String url) {
        this.srtUrl = url;
        okHttpClient = OkHttpUtil.getOAuthBasedClientWithOfflineCache(context);
    }

    @Override
    public void run() {
        try {
            final Response response = okHttpClient.newCall(new Request.Builder()
                    .url(srtUrl)
                    .get()
                    .build())
                    .execute();
            if (!response.isSuccessful()) {
                throw new HttpResponseStatusException(response);
            }
            onDownloadComplete(response.body().string());
        } catch (Exception localException) {
            handle(localException);
            logger.error(localException);
        }
    }

    public abstract void handle(Exception ex);

    public abstract void onDownloadComplete(String response);
}
