package com.rox.extra.presentation

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.rox.extra.R
import com.rox.extra.presentation.theme.ExtraTheme
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity(),
    SensorEventListener,
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener{

    var activityContext: Context?=null

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor?=null
    private var sensorType=Sensor.TYPE_GYROSCOPE

    lateinit var nodeID: String
    private lateinit var PAYLOAD: String

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp("Android")
        }
    }

    private fun sendMessage(){
        val sendMessage= Wearable.getMessageClient(activityContext!!)
            .sendMessage(nodeID, PAYLOAD, "mensaje aenviar".toByteArray())
            .addOnSuccessListener {
                Log.d("sendMessage", "Mesnaje envisdo con éxito")
            }
            .addOnFailureListener { exception ->
                Log.d("sendMessage", "Error al enviar mensaje ${exception.message}")
            }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener (this)
            Wearable.getMessageClient(activityContext!!).removeListener (this)
            Wearable.getCapabilityClient(activityContext!!).removeListener (this)
            sensorManager.unregisterListener(this)
        }catch (e: Exception){
            Log.d("onPause", e.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(activityContext!!).addListener (this)
            Wearable.getMessageClient(activityContext!!).addListener (this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener (this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        }catch (e: Exception){
            Log.d("onResume", e.toString())
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {

    }

    override fun onMessageReceived(p0: MessageEvent) {

    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {

    }

    private fun startSensor(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BODY_SENSORS), 1001)
            return
        }
        if (sensor!=null){
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    override fun onSensorChanged(SE: SensorEvent?) {
        if (SE?.sensor?.type==sensorType){
            val lectura=SE.values[0]
            Log.d("onSensorChanged", "lectura: ${lectura}")
        }
    }
}

@Composable
fun WearApp(greetingName: String) {
    ExtraTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            Greeting(greetingName = greetingName)
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}