package org.edx.mobile.base;

import com.crashlytics.android.Crashlytics;

import org.edx.mobile.view.ExtensionRegistry;

import javax.inject.Inject;

import io.fabric.sdk.android.Fabric;

/**
 * Put any custom application configuration here.
 * This file will not be edited by edX unless absolutely necessary.
 */
public class RuntimeApplication extends MainApplication {

    @SuppressWarnings("unused")
    @Inject
    ExtensionRegistry extensionRegistry;

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        // If you have any custom extensions, add them here. For example:
        // extensionRegistry.forType(SettingsExtension.class).add(new MyCustomSettingsExtension());
    }
}
