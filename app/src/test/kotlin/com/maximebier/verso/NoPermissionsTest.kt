package com.maximebier.verso

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoPermissionsTest {

    @Test
    fun requestsNoPermissionExceptAndroidxSignatureReceiver() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)

        // androidx.core déclare une permission de niveau « signature », propre à l'app, jamais montrée à l'utilisateur.
        val internal = "${ctx.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val requested = info.requestedPermissions.orEmpty().filterNot { it == internal }

        assertWithMessage("permissions demandées").that(requested).isEmpty()
    }
}
