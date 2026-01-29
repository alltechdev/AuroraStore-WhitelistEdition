package com.aurora.store.data.installer

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import com.aurora.extensions.isNAndAbove
import com.aurora.extensions.isOAndAbove
import com.aurora.extensions.isSAndAbove
import com.aurora.extensions.isTAndAbove
import com.aurora.extensions.isUAndAbove
import com.aurora.store.R
import com.aurora.store.data.installer.AppInstaller.Companion.ACTION_INSTALL_STATUS
import com.aurora.store.data.installer.AppInstaller.Companion.EXTRA_DISPLAY_NAME
import com.aurora.store.data.installer.AppInstaller.Companion.EXTRA_PACKAGE_NAME
import com.aurora.store.data.installer.AppInstaller.Companion.EXTRA_VERSION_CODE
import com.aurora.store.data.installer.base.InstallerBase
import com.aurora.store.data.model.BuildType
import com.aurora.store.data.model.Installer
import com.aurora.store.data.model.InstallerInfo
import com.aurora.store.data.receiver.InstallerStatusReceiver
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.PackageUtil.isSharedLibraryInstalled
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceOwnerInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallerBase(context) {

    private val TAG = DeviceOwnerInstaller::class.java.simpleName
    private val packageInstaller = context.packageManager.packageInstaller

    companion object {
        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService<DevicePolicyManager>() ?: return false
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        val installerInfo: InstallerInfo
            get() = InstallerInfo(
                id = 6,
                installer = Installer.DEVICE_OWNER,
                packageNames = BuildType.PACKAGE_NAMES,
                installerPackageNames = BuildType.PACKAGE_NAMES,
                title = R.string.pref_install_mode_device_owner,
                subtitle = R.string.device_owner_installer_subtitle,
                description = R.string.device_owner_installer_desc
            )
    }

    override fun install(download: Download) {
        super.install(download)

        Log.i(TAG, "Received device owner install request for ${download.packageName}")

        // Install shared libraries first
        download.sharedLibs.forEach {
            if (!isSharedLibraryInstalled(context, it.packageName, it.versionCode)) {
                installPackage(download.packageName, download.versionCode, it.packageName)
            }
        }

        // Install the main package
        installPackage(download.packageName, download.versionCode)
    }

    private fun installPackage(
        packageName: String,
        versionCode: Long,
        sharedLibPkgName: String = ""
    ) {
        val resolvedPackageName = sharedLibPkgName.ifBlank { packageName }

        try {
            val sessionParams = SessionParams(SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(resolvedPackageName)
                setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
                if (isNAndAbove) setOriginatingUid(Process.myUid())
                if (isOAndAbove) setInstallReason(PackageManager.INSTALL_REASON_USER)
                if (isSAndAbove) setRequireUserAction(SessionParams.USER_ACTION_NOT_REQUIRED)
                if (isTAndAbove) setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
                if (isUAndAbove) {
                    setInstallerPackageName(context.packageName)
                    setRequestUpdateOwnership(true)
                    setApplicationEnabledSettingPersistent()
                }
            }

            val sessionId = packageInstaller.createSession(sessionParams)
            val session = packageInstaller.openSession(sessionId)

            try {
                getFiles(packageName, versionCode, sharedLibPkgName).forEach { file ->
                    file.inputStream().use { input ->
                        session.openWrite(
                            "${resolvedPackageName}_${file.name}",
                            0,
                            file.length()
                        ).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }

                val callBackIntent = Intent(context, InstallerStatusReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                    setPackage(context.packageName)
                    putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                    putExtra(EXTRA_VERSION_CODE, versionCode)
                    putExtra(EXTRA_DISPLAY_NAME, download?.displayName ?: packageName)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }

                val pendingIntent = PendingIntentCompat.getBroadcast(
                    context,
                    sessionId,
                    callBackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    true
                )

                session.commit(pendingIntent!!.intentSender)
                session.close()
            } catch (e: IOException) {
                session.abandon()
                removeFromInstallQueue(packageName)
                postError(packageName, e.localizedMessage, e.stackTraceToString())
            }
        } catch (e: Exception) {
            removeFromInstallQueue(packageName)
            postError(packageName, e.localizedMessage, e.stackTraceToString())
        }
    }
}
