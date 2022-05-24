package me.iacn.biliroaming.hook

import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.BuildConfig
import me.iacn.biliroaming.utils.*
import java.io.File

class AppUpgradeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val upgradeApi = "https://www.kofua.top/bapp/version/upgrade/m%s"

    override fun startHook() {
        if (platform == "android") {
            BiliBiliPackage.instance.updaterOptionsClass?.run {
                BiliBiliPackage.instance.upgradeApiMethod?.let {
                    replaceMethod(it) { upgradeApi.format(BuildConfig.VERSION_CODE) }
                }
            }
            BiliBiliPackage.instance.upgradeUtilsClass?.run {
                BiliBiliPackage.instance.writeChannelMethod?.let {
                    replaceMethod(it, File::class.java, String::class.java) { null }
                }
            }
        }
        BiliBiliPackage.instance.helpFragmentClass?.hookAfterMethod(
            "onActivityCreated", Bundle::class.java
        ) { param ->
            val preference = param.thisObject
                .callMethodOrNull("findPreference", "CheckUpdate")
                ?: return@hookAfterMethod
            val pm = currentContext.packageManager
            val verName = pm.getPackageInfo(packageName, 0).versionName
            val buildSn = pm.getApplicationInfo(
                packageName, PackageManager.GET_META_DATA
            ).metaData.getInt("BUILD_SN")
            val mVerName = BuildConfig.VERSION_NAME
            val mVerCode = BuildConfig.VERSION_CODE
            val summary = "当前版本: $verName (release-b$buildSn)\n当前内置漫游版本: $mVerName ($mVerCode)"
            preference.callMethodOrNull("setSummary", summary)
        }
        Dialog::class.java.hookBeforeMethod(
            "show"
        ) { param ->
            val thiz = param.thisObject
            if (thiz is AlertDialog && thiz.getObjectField("mAlert")
                    ?.getObjectField("mMessage")
                    ?.toString() == "新版本安装包下载完成，立即更新?"
            ) {
                thiz.callMethod("sendDismissMessage")
                param.result = null
            }
        }
    }
}
