package com.santidad.app.app_movil_publi

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        const val EXTRA_AUTOSTART_LAUNCH = "com.santidad.app.extra.AUTOSTART_LAUNCH"
        private const val AUTOSTART_CHANNEL = "com.santidad.app/autostart"
        private const val HOME_ROLE_REQUEST_CODE = 6101
        private const val HOME_SETTINGS_REQUEST_CODE = 6102
        private const val OVERLAY_REQUEST_CODE = 6103
        private const val MAX_HOME_VERIFICATION_ATTEMPTS = 6
    }

    private enum class PendingRequest {
        HOME,
        OVERLAY,
    }

    private var pendingResult: MethodChannel.Result? = null
    private var pendingRequest: PendingRequest? = null
    private var externalScreenOpened = false
    private var verificationScheduled = false
    private var homeVerificationAttempts = 0
    private var setupSuppressedForInitialLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Capturar solo el Intent que creo la Activity. Al conceder ROLE_HOME,
        // algunos TV envian un onNewIntent HOME; no debe convertir una
        // configuracion manual en un arranque desatendido a mitad del flujo.
        setupSuppressedForInitialLaunch =
            intent.getBooleanExtra(EXTRA_AUTOSTART_LAUNCH, false) ||
                (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME))

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AUTOSTART_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getStatus" -> result.success(getAutoStartStatus())
                    "requestHomeRole" -> requestHomeRole(result)
                    "requestOverlayPermission" -> requestOverlayPermission(result)
                    else -> result.notImplemented()
                }
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onPause() {
        if (pendingResult != null) externalScreenOpened = true
        super.onPause()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (pendingResult != null && externalScreenOpened) {
            schedulePendingVerification()
        }
    }

    @Deprecated("Deprecated in Android; retained for system settings compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HOME_ROLE_REQUEST_CODE ||
            requestCode == HOME_SETTINGS_REQUEST_CODE ||
            requestCode == OVERLAY_REQUEST_CODE
        ) {
            schedulePendingVerification()
        }
    }

    private fun getAutoStartStatus(): Map<String, Boolean> =
        mapOf(
            "home" to isHomeConfigured(),
            "overlay" to canDrawOverlays(),
            "bootLaunch" to isNonInteractiveLaunch(),
        )

    @Suppress("DEPRECATION")
    private fun requestHomeRole(result: MethodChannel.Result) {
        if (isHomeConfigured()) {
            result.success(true)
            return
        }
        if (isNonInteractiveLaunch()) {
            result.success(false)
            return
        }
        if (!beginRequest(PendingRequest.HOME, result)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                        HOME_ROLE_REQUEST_CODE,
                    )
                    return
                } catch (_: Exception) {
                    clearPendingRequest()
                    requestHomeSettings(result)
                    return
                }
            }
        }

        clearPendingRequest()
        requestHomeSettings(result)
    }

    @Suppress("DEPRECATION")
    private fun requestHomeSettings(result: MethodChannel.Result) {
        if (!beginRequest(PendingRequest.HOME, result)) return
        try {
            startActivityForResult(Intent(Settings.ACTION_HOME_SETTINGS), HOME_SETTINGS_REQUEST_CODE)
        } catch (_: Exception) {
            completePendingRequest(forceResult = false)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestOverlayPermission(result: MethodChannel.Result) {
        if (canDrawOverlays()) {
            result.success(true)
            return
        }
        if (isNonInteractiveLaunch()) {
            result.success(false)
            return
        }
        if (!beginRequest(PendingRequest.OVERLAY, result)) return

        val packageSettings = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivityForResult(packageSettings, OVERLAY_REQUEST_CODE)
        } catch (_: Exception) {
            try {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                    OVERLAY_REQUEST_CODE,
                )
            } catch (_: Exception) {
                completePendingRequest(forceResult = false)
            }
        }
    }

    private fun beginRequest(type: PendingRequest, result: MethodChannel.Result): Boolean {
        if (pendingResult != null) {
            result.error("REQUEST_IN_PROGRESS", "Ya hay una configuracion del sistema abierta.", null)
            return false
        }

        pendingResult = result
        pendingRequest = type
        externalScreenOpened = false
        verificationScheduled = false
        homeVerificationAttempts = 0
        return true
    }

    private fun schedulePendingVerification(delayMillis: Long = 500L) {
        if (pendingResult == null || verificationScheduled) return
        verificationScheduled = true
        window.decorView.postDelayed(
            {
                verificationScheduled = false
                verifyPendingRequest()
            },
            delayMillis,
        )
    }

    private fun verifyPendingRequest() {
        when (pendingRequest) {
            PendingRequest.HOME -> {
                if (isHomeConfigured()) {
                    completePendingRequest(forceResult = true)
                } else if (homeVerificationAttempts < MAX_HOME_VERIFICATION_ATTEMPTS) {
                    homeVerificationAttempts += 1
                    schedulePendingVerification()
                } else {
                    completePendingRequest(forceResult = false)
                }
            }

            PendingRequest.OVERLAY -> {
                if (canDrawOverlays()) {
                    completePendingRequest(forceResult = true)
                } else if (homeVerificationAttempts < MAX_HOME_VERIFICATION_ATTEMPTS) {
                    homeVerificationAttempts += 1
                    schedulePendingVerification()
                } else {
                    completePendingRequest(forceResult = false)
                }
            }
            null -> Unit
        }
    }

    private fun completePendingRequest(forceResult: Boolean) {
        val result = pendingResult ?: return
        clearPendingRequest()
        result.success(forceResult)
    }

    private fun clearPendingRequest() {
        pendingResult = null
        pendingRequest = null
        externalScreenOpened = false
        verificationScheduled = false
        homeVerificationAttempts = 0
    }

    private fun isHomeConfigured(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return true
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolved = packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun isNonInteractiveLaunch(): Boolean = setupSuppressedForInitialLaunch
}
