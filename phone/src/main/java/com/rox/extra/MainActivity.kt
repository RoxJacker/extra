package com.rox.extra

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.tagKey
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.rox.extra.ui.theme.ExtraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets
import kotlin.math.log

class MainActivity : ComponentActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

        var activityContext: Context?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityContext=this
       /* enableEdgeToEdge()
        setContent {
            ExtraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        } */
    }

    private fun getNodes(context: Context){
        launch (context = Dispatchers.Default){
            val nodeList= Wearable.getNodeClient(context).connectedNodes
            try {
                val nodes= Tasks.await(nodeList)
                for (node in nodes){
                    Log.d("Nodo", node.toString())
                    Log.d("Nodo","El id dek nodo es:${node.id}")
                }
            } catch (exception: Exception){
         Log.d("Error al obtener nodos", exception.toString)
            }
    }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!). removeListener (this)
            Wearable. getMessageClient(activityContext!!). removeListener (this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)
        }catch (e: Exception){
        Log. d ("onPause", e.toString())
    }
    }


    override fun onDataChanged(p0: DataEventBuffer) {
    }

    override fun onMessageReceived(ME: MessageEvent) {
        Log.d("onMessageReceived",ME.toString())
        Log.d("OnMessageReceived","nodo${ME.sourceNodeId}")
        Log.d("OnMessageReceived", "Payload:${ME.path}")
        val=message= String(ME.data, StandardCharsets.UTF_8)
        Log.d("OnMessageReceived", "Mensaje:${message}")

    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ExtraTheme {
        Greeting("Android")
    }
}