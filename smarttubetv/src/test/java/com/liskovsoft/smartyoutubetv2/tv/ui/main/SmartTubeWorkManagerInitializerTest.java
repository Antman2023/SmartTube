package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.TestWorkerBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE)
public class SmartTubeWorkManagerInitializerTest {
    @Test
    public void keepsNormalApplicationContext() {
        Context appContext = RuntimeEnvironment.getApplication();
        assertSame(appContext, SmartTubeWorkManagerInitializer.getWorkContext(appContext));
    }

    @Test
    @Config(sdk = 23)
    public void supportsDevicesWithoutStorageContextApis() {
        Context appContext = RuntimeEnvironment.getApplication();
        assertSame(appContext, SmartTubeWorkManagerInitializer.getWorkContext(appContext));
    }

    @Test
    public void unlockedCompatibilityCheckSurvivesApplicationContextNormalization() {
        Context appContext = deviceProtectedApplication();
        assertTrue(appContext.isDeviceProtectedStorage());
        Context workContext = SmartTubeWorkManagerInitializer.getWorkContext(appContext);
        assertFalse(workContext.isDeviceProtectedStorage());
        assertEquals(appContext.getFilesDir(), workContext.getFilesDir());
        assertEquals(appContext.getNoBackupFilesDir(), workContext.getNoBackupFilesDir());
        assertSame(workContext, workContext.getApplicationContext());
        assertSame(workContext, workContext.getApplicationContext().getApplicationContext());
    }

    @Test
    public void preservesStorageGuardBeforeUserUnlock() {
        Context appContext = deviceProtectedApplication();
        UserManager userManager = (UserManager) appContext.getSystemService(Context.USER_SERVICE);
        Shadows.shadowOf(userManager).setUserUnlocked(false);
        Context workContext = SmartTubeWorkManagerInitializer.getWorkContext(appContext);
        assertTrue(workContext.isDeviceProtectedStorage());
        Shadows.shadowOf(userManager).setUserUnlocked(true);
        assertFalse(workContext.isDeviceProtectedStorage());
    }

    @Test
    public void initializesAndEnqueuesWorkOnDeviceProtectedFirmware() throws Exception {
        Context appContext = deviceProtectedApplication();
        WorkManager manager = new SmartTubeWorkManagerInitializer().create(appContext);
        assertSame(manager, WorkManager.getInstance(appContext));
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PreferenceWorker.class)
                .setInitialDelay(1, TimeUnit.DAYS).build();
        manager.enqueue(request).getResult().get(10, TimeUnit.SECONDS);
        assertEquals(WorkInfo.State.ENQUEUED,
                manager.getWorkInfoById(request.getId()).get(10, TimeUnit.SECONDS).getState());
    }

    @Test
    public void workersKeepExistingDeviceProtectedPreferences() {
        Context appContext = deviceProtectedApplication();
        appContext.getSharedPreferences("existing", Context.MODE_PRIVATE).edit()
                .putString("setting", "preserved").commit();
        Context workContext = SmartTubeWorkManagerInitializer.getWorkContext(appContext);
        Worker worker = TestWorkerBuilder.from(workContext, PreferenceWorker.class, Runnable::run)
                .build();
        assertEquals(appContext.getFilesDir(), worker.getApplicationContext().getFilesDir());
        assertEquals("preserved", worker.getApplicationContext()
                .getSharedPreferences("existing", Context.MODE_PRIVATE).getString("setting", null));
        assertEquals(Worker.Result.success(), worker.doWork());
    }

    private static Context deviceProtectedApplication() {
        UserManager userManager = (UserManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.USER_SERVICE);
        Shadows.shadowOf(userManager).setUserUnlocked(true);
        return new ContextWrapper(RuntimeEnvironment.getApplication().createDeviceProtectedStorageContext()) {
            @Override
            public Context getApplicationContext() {
                return this;
            }
        };
    }

    public static class PreferenceWorker extends Worker {
        public PreferenceWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
            super(context, parameters);
        }

        @NonNull
        @Override
        public Result doWork() {
            return Result.success();
        }
    }
}
