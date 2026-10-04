package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import java.util.Collections;
import java.util.List;

/** Initializes WorkManager on TV firmware that defaults applications to device-protected storage. */
public class SmartTubeWorkManagerInitializer implements Initializer<WorkManager> {
    @NonNull
    @Override
    public WorkManager create(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Context workContext = getWorkContext(appContext);
        WorkManager.initialize(workContext, new Configuration.Builder().build());
        return WorkManager.getInstance(appContext);
    }

    static Context getWorkContext(Context appContext) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !appContext.isDeviceProtectedStorage()) {
            return appContext;
        }
        UserManager userManager = (UserManager) appContext.getSystemService(Context.USER_SERVICE);
        if (userManager == null) {
            return appContext;
        }
        return new ContextWrapper(appContext) {
            @Override
            public Context getApplicationContext() {
                // WorkManager normalizes its context repeatedly. Returning the original application
                // would discard this firmware compatibility check and trigger the startup crash.
                return this;
            }

            @Override
            public boolean isDeviceProtectedStorage() {
                // Some TV firmware forces all apps into device storage even after user unlock.
                // WorkManager rejects that flag unconditionally. Only relax its check once the
                // user is unlocked; all data paths and preferences still delegate to the app.
                return super.isDeviceProtectedStorage() && !userManager.isUserUnlocked();
            }
        };
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
