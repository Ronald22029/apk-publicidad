package com.santidad.app.app_movil_publi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Evento de arranque recibido: $action")

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        // BOOT_COMPLETED es una excepcion valida para iniciar un foreground
        // service. El servicio espera a que termine de cargar el launcher del TV
        // y hace varios intentos; el permiso de superposicion permite mostrar la
        // Activity desde segundo plano en Android 11+.
        if (action == Intent.ACTION_BOOT_COMPLETED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                val serviceIntent = Intent(context, BootService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "Servicio de auto-inicio solicitado")
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar el servicio de auto-inicio", e)
            }
        }

        // Intento inmediato adicional para firmwares permisivos o cuando la app
        // ya es HOME. singleTask evita crear instancias duplicadas.
        try {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_AUTOSTART_LAUNCH, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.w(TAG, "El intento inmediato fue rechazado; continuara el servicio", e)
        }
    }
}
