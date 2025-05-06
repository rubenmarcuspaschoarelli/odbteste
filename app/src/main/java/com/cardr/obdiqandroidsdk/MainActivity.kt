package com.cardr.obdiqandroidsdk

import DTCResponseModel
import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.androidisland.ezpermission.EzPermission
import com.cardr.obdiqandroidsdk.ui.theme.OBDIQAndroidSdkTheme
import com.cardr.obdiqsdk.ConnectionListner
import com.repairclub.repaircludsdk.models.DeviceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proceedWithPermissionsCheck(this)

        setContent {
            OBDIQAndroidSdkTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }
}

private fun proceedWithPermissionsCheck(
    context: Context
) {
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )

    } else {
        arrayOf()
    }

    EzPermission.with(context)
        .permissions(*bluetoothPermissions)
        .request { granted, denied, permanentlyDenied ->
            CoroutineScope(Dispatchers.Main).launch {
                if (granted.size == bluetoothPermissions.size) {
                    val cnn = ConnectionManager(context)
                    cnn.initialize(context,object :ConnectionListner{
                        override fun didDevicesFetch(foundedDevices: List<DeviceItem>?) {
                            TODO("Not yet implemented")
                        }

                        override fun didCheckScanStatus(status: String) {
                            TODO("Not yet implemented")
                        }

                        override fun didFetchVehicalInfo(vehicleEntry: VehicleEntries) {
                            TODO("Not yet implemented")
                        }

                        override fun didFetchMil(mil: Boolean) {
                            TODO("Not yet implemented")
                        }

                        override fun isReadyForScan(status: Boolean, isGenric: Boolean) {
                            TODO("Not yet implemented")
                        }

                        override fun didUpdateProgress(progressStatus: String, percent: String) {
                            TODO("Not yet implemented")
                        }

                        override fun didReceivedCode(model: List<DTCResponseModel>?) {
                            TODO("Not yet implemented")
                        }

                        override fun didReceivedRepairCost(jsonString: String) {
                            TODO("Not yet implemented")
                        }

                    })
                }
                if (denied.isNotEmpty() || permanentlyDenied.isNotEmpty()) {

                }
            }
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
    OBDIQAndroidSdkTheme {
        Greeting("Android")
    }
}