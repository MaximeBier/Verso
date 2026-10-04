package com.maximebier.verso

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.Properties
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class NoPermissionsTest {

    @Test
    fun requestsOnlyInternetForTranslationAndAndroidxSignatureReceiver() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)

        // androidx.core déclare une permission de niveau « signature », propre à l'app, jamais montrée à l'utilisateur.
        val internal = "${ctx.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val requested = info.requestedPermissions.orEmpty().filterNot { it == internal }

        // V3 : INTERNET sert uniquement à la traduction de la sélection.
        assertWithMessage("permissions demandées").that(requested).containsExactly("android.permission.INTERNET")
    }

    @Test
    fun androidBackupIsDisabled() {
        // « Rien ne quitte le téléphone » : la sauvegarde automatique enverrait livres, base et réglages sur Drive.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val app = ctx.packageManager.getApplicationInfo(ctx.packageName, 0)

        assertWithMessage("android:allowBackup").that(app.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }

    @Test
    fun dataExtractionRulesExcludeCloudBackupAndDeviceTransfer() {
        // Android 12+ ignore allowBackup pour le transfert d'appareil : règles explicites.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Manifeste fusionné vu par Robolectric (chemin donné par AGP) : ApplicationInfo n’expose pas ce champ.
        val config = Properties().apply {
            ClassLoader.getSystemResourceAsStream("com/android/tools/test_config.properties")!!.use(::load)
        }
        val manifest = File(config.getProperty("android_merged_manifest")).readText()
        assertWithMessage("manifeste fusionné").that(manifest).contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        // Android 11 et moins : allowBackup="false" suffit ; fullBackupContent exclut tout par précaution.
        assertWithMessage("manifeste fusionné").that(manifest).contains("android:fullBackupContent=\"@xml/backup_rules\"")

        val excluded = mutableMapOf<String, MutableSet<String>>()
        val parser = ctx.resources.getXml(R.xml.data_extraction_rules)
        var section: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer" -> section = parser.name
                "exclude" -> excluded.getOrPut(section.orEmpty()) { mutableSetOf() } += parser.getAttributeValue(null, "domain")
            }
        }
        val all = setOf("root", "file", "database", "sharedpref", "external")
        assertWithMessage("exclusions cloud-backup").that(excluded["cloud-backup"].orEmpty()).containsAtLeastElementsIn(all)
        assertWithMessage("exclusions device-transfer").that(excluded["device-transfer"].orEmpty()).containsAtLeastElementsIn(all)
    }
}
