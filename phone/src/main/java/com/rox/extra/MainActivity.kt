package com.rox.extra

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.extra.ui.theme.ExtraTheme
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    var activityContext: Context?=null

    // Estado para mostrar en la UI
    var datosRecibidos by mutableStateOf("Esperando datos...")
        private set

    var statusMessage by mutableStateOf("Presiona CONECTAR para iniciar")
        private set

    var nodeID: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityContext=this
        enableEdgeToEdge()
        setContent {
            ExtraTheme {
                PhoneApp(
                    onConnect = { getNodes(activityContext!!) },
                    datosRecibidos = datosRecibidos,
                    statusMessage = statusMessage
                )
            }
        }
    }
    private fun getNodes(context: Context) {
        launch(Dispatchers.Default) {
            val nodeList = Wearable.getNodeClient(context).connectedNodes
            try {
                val nodes = Tasks.await(nodeList)
                if (nodes.isNotEmpty()) {
                    for (node in nodes) {
                        nodeID = node.id
                        launch(Dispatchers.Main) {
                            statusMessage = "Conectado con: ${node.displayName}"
                        }
                        Log.d("NODO", node.toString())
                        Log.d("NODO", "El id del nodo es: ${node.id}")
                    }
                } else {
                    launch(Dispatchers.Main) {
                        statusMessage = "No se encontró smartwatch"
                    }
                    Log.d("NODO", "No se encontraron nodos")
                }
            } catch (exception: Exception) {
                launch(Dispatchers.Main) {
                    statusMessage = "Error al conectar"
                }
                Log.d("Error al obtener nodos", exception.toString())
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener (this)
            Wearable.getMessageClient(activityContext!!).removeListener (this)
            Wearable.getCapabilityClient(activityContext!!).removeListener (this)
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

    override fun onMessageReceived(ME: MessageEvent) {
        Log.d("onMessageReceived", ME.toString())
        Log.d("onMessageReceived", "nodo ${ME.sourceNodeId}")
        Log.d("onMessageReceived", "Payload: ${ME.path}")
        val message= String(ME.data, StandardCharsets.UTF_8)
        Log.d("onMessageReceived", "Mensaje: ${message}")

        // Actualizar UI con los datos recibidos
        datosRecibidos = message
        statusMessage = "Datos recibidos correctamente"
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {

    }
}

@Composable
fun PhoneApp(
    onConnect: () -> Unit,
    datosRecibidos: String,
    statusMessage: String
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título
            Text(
                text = "Control Smartwatch",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botón CONECTAR
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "CONECTAR",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mensaje de estado
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Label "DATOS RECIBIDOS"
            Text(
                text = "DATOS RECIBIDOS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mostrar datos recibidos
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = datosRecibidos,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneAppPreview() {
    ExtraTheme {
        PhoneApp(
            onConnect = {},
            datosRecibidos = "25.43",
            statusMessage = "Conectado con: Galaxy Watch"
        )
    }
}