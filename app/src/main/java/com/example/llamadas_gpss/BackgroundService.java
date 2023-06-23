package com.example.llamadas_gpss;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

public class BackgroundService extends Service implements LocationListener {
    private LocationManager locationManager;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private Handler handler;
    private Runnable runnable;
    private boolean callAnswered = false;
    private String incomingPhoneNumber;


    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (!callAnswered) {
                    // Obtener las coordenadas actuales
                    double latitude = 0.0;  // Valor de latitud predeterminado
                    double longitude = 0.0; // Valor de longitud predeterminado

                    // Verificar si se ha obtenido la última ubicación conocida
                    if (ActivityCompat.checkSelfPermission(BackgroundService.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (lastKnownLocation != null) {
                            latitude = lastKnownLocation.getLatitude();
                            longitude = lastKnownLocation.getLongitude();
                        }
                    }

                    // Llamar al método sendMessageWithCoordinates con las coordenadas
                    sendMessageWithCoordinates(latitude, longitude);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            incomingPhoneNumber = intent.getStringExtra("numero_guardado");
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            startCallDetection();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        stopCallDetection();
        handler.removeCallbacks(runnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this);
    }

    private void stopLocationUpdates() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    private boolean isGPSEnabled() {
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void stopCallDetection() {
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private void sendMessageWithCoordinates(double latitude, double longitude) {
        // Componer el mensaje con las coordenadas
        String message = "Hola, mis coordenadas son: " + latitude + ", " + longitude;

        // Verificar si la aplicación tiene permiso para enviar mensajes de texto
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {

        } else {
            // Enviar el mensaje de texto automáticamente
            sendSMS(incomingPhoneNumber, message);
        }
    }

    private void sendSMS(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al enviar el mensaje", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onLocationChanged(Location location) {
        // Actualizar las coordenadas en la interfaz de usuario
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // Enviar mensaje después de 7 segundos si la llamada no ha sido contestada
        if (!callAnswered) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Verificar nuevamente si la llamada ha sido contestada
                    if (!callAnswered) {
                        // Obtener las coordenadas actuales
                        double currentLatitude = location.getLatitude();
                        double currentLongitude = location.getLongitude();

                        // Verificar si las coordenadas actuales son diferentes de las anteriores
                        if (latitude != currentLatitude || longitude != currentLongitude) {
                            // Llamar al método sendMessageWithCoordinates con las coordenadas
                            sendMessageWithCoordinates(currentLatitude, currentLongitude);
                        }
                    }
                }
            }, 7000);
        }
    }

    private void startCallDetection() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        Toast.makeText(BackgroundService.this, "Llamada entrante: " + phoneNumber, Toast.LENGTH_SHORT).show();
                        // Resetear el indicador de respuesta de llamada
                        callAnswered = false;
                        // Verificar si el número de teléfono coincide con el número guardado
                        if (phoneNumber.equals(incomingPhoneNumber)) {
                            // Iniciar el temporizador de 7 segundos si los números coinciden
                            handler.postDelayed(runnable, 7000);
                        } else {
                            // Mostrar notificación de número no coincidente
                            showNumberMismatchNotification();
                        }
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        Toast.makeText(BackgroundService.this, "Llamada saliente: " + phoneNumber, Toast.LENGTH_SHORT).show();
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        Toast.makeText(BackgroundService.this, "Llamada finalizada", Toast.LENGTH_SHORT).show();
                        // Detener el temporizador si la llamada ha sido contestada
                        if (!callAnswered) {
                            handler.removeCallbacks(runnable);
                        }
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void showNumberMismatchNotification() {
        // Mostrar una notificación indicando que el número de teléfono no coincide
        Toast.makeText(BackgroundService.this, "El número no coincide", Toast.LENGTH_SHORT).show();
    }

}