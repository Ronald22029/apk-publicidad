# Santidad TV

Reproductor de publicidad para Android TV.

## Inicio automatico en Android TV 11+

Android bloquea que una aplicacion comun abra una pantalla desde segundo plano.
Santidad TV combina la aplicacion de Inicio (HOME) con un servicio temporal de
arranque y el permiso **Mostrar sobre otras apps** para televisores cuyo launcher
no respeta la seleccion HOME durante el reinicio.

Despues de instalar la APK:

1. Abre Santidad TV manualmente una vez.
2. Selecciona y guarda la sucursal.
3. Cuando Android lo solicite, elige **Santidad TV** como aplicacion de inicio.
   Algunos televisores no muestran la palabra "Siempre"; elegir la app es suficiente.
4. Pulsa **Configurar**, activa **Permitir mostrar sobre otras apps** y vuelve
   con el boton Atras.
5. Reinicia la TV para comprobar el auto-inicio.

Si se usa **Forzar detencion** en los ajustes de Android, el sistema no permite
que ninguna aplicacion se auto-inicie hasta que se abra manualmente otra vez.

## Compilacion

```powershell
flutter build apk --release
```

La APK universal se genera en
`build/app/outputs/flutter-apk/app-release.apk`.
