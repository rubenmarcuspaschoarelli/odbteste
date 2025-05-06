package com.cardr.obdiqandroidsdk

import DTCResponse
import DTCResponseModel
import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.cardr.obdiqsdk.ConnectionListner
import com.google.gson.Gson
import com.repairclub.repaircludsdk.coreobjects.FirmwareProgress
import com.repairclub.repaircludsdk.manager.RepairClubManager
import com.repairclub.repaircludsdk.models.ConnectionEntry
import com.repairclub.repaircludsdk.models.ConnectionStage
import com.repairclub.repaircludsdk.models.ConnectionState
import com.repairclub.repaircludsdk.models.DeviceItem
import com.repairclub.repaircludsdk.models.FirmwareReleaseType
import com.repairclub.repaircludsdk.models.ModuleItem
import com.repairclub.repaircludsdk.models.ResponseStatus
import com.repairclub.repaircludsdk.models.ScanProgressUpdate
import com.repairclub.repaircludsdk.models.VehicleEntry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

class ConnectionManager(
    private val context: Context,
) {
    private val foundDevices = CopyOnWriteArrayList<DeviceItem>()
    private var timerStarted = false
    private var connectedStatus = false
    private var disconnectionHandler: (() -> Unit)? = null
    var disconnectEmissionHandler: (() -> Unit)? = null
    var repairClubManager: RepairClubManager? = null

    private var connectionHandler: ((connectionEntry: ConnectionEntry, connectionStage: ConnectionStage, connectionState: ConnectionState?) -> Unit)? =
        null
    private var connectionEntry: ConnectionEntry? = null
    private var latestConnectionStage: ConnectionStage? = null
    val connectionStates = mutableMapOf<ConnectionStage, ConnectionState>()

    var vehicleEntry: VehicleEntry? = null
    val dtcErrorCodeArray = CopyOnWriteArrayList<DTCResponseModel>()

    var isAlreadyConnected = false
    var appVersion: String = ""
    var appName: String = ""
    var vinNumber = ""
    var yearstr = ""
    var make = ""
    var model = ""
    var carName = ""
    var fuelType = ""
    var hardwareIdentifier = ""
    var isRedinessComplete = false
    var isMilOn: Boolean = false
    var scanID = ""
    var passFail = ""
    private val _progress = MutableLiveData<ScanProgressUpdate?>()
    private val _connectionState = MutableLiveData<ConnectionState?>()

    public val _emmissionList = MutableLiveData<ArrayList<EmissionRediness>?>()
    var connectionListner:ConnectionListner? = null
    var currentFirmwareVersion = ""
    var accesstoken = ""
    var apiKey = ""


    public fun initialize(context: Context,connectionListner: ConnectionListner){

        Timber.tag("ConnectionManager").i("initialize")
        scanID = ""
        this.connectionListner = connectionListner
        repairClubManager = RepairClubManager.getInstance()
        Timber.tag("ConnectionManager").i("getInstance")
        repairClubManager?.initialize(context)
        Timber.tag("ConnectionManager").i("initialize")

        repairClubManager?.configureSDK(BuildConfig.SDK_KEY,"OBDIQ ULTRA SDK Android",BuildConfig.SDK_VERSION,"")
        Timber.tag("ConnectionManager").i("configureSDK")
        subscribeToDisconnections()
        connectToDevice()

    }

    fun registerDisconnectionHandler(handler: () -> Unit) {
        disconnectionHandler = handler
    }

    fun registerConnectionHandler(handler: (connectionEntry: ConnectionEntry?, connectionStage: ConnectionStage, connectionState: ConnectionState?) -> Unit) {
        connectionHandler = handler
        println("Connection:: registerConnectionHandler")
        Timber.tag("ConnectionManager").i("registerConnectionHandler")
    }


    public fun subscribeToDisconnections() {
        repairClubManager?.subscribeToDisconnections {
            connectedStatus = false
            connectionStates.clear()
            latestConnectionStage = null
            connectionEntry = null
            isMilOn = false
            isAlreadyConnected = false
            disconnectionHandler?.invoke()
            println("Connection:: registerConnectionHandler - reset connection")
            Timber.tag("Connection").i("registerConnectionHandler - reset connection")

        }
        println("Connection:: subscribeToDisconnections")
    }

   public fun connectToDevice() {
//        noVinFunction?.invoke()
       scanID = ""
        repairClubManager?.returnDevices { devices ->
            devices.forEach { device ->
                if (!foundDevices.contains(device)) {
                    foundDevices.add(device)
                    connectionListner?.didDevicesFetch(foundDevices)
                    println("Connection:: Device found - ${device.name} ${device.rssi}")
                    Timber.tag("ConnectionDevice").i("Device found - ${device.name} ${device.rssi}")
                    if (!timerStarted) {
                        timerStarted = true
                        startSelectDeviceTimer()
                    }
                }
            }
        }
    }

  public  fun stopTroubleCodeScan() {
        repairClubManager?.stopTroubleCodeScan()
    }

    private fun startSelectDeviceTimer() {
        println("startSelectDeviceTimer ")
        Handler(Looper.getMainLooper()).postDelayed({
            selectAndConnectToClosestDevice()
        }, 1000)
    }

    private fun selectAndConnectToClosestDevice() {
        if (foundDevices.isEmpty()) {
            println("Connection:: No devices found")
            return
        }

        val closestDevice = foundDevices.minByOrNull { it.rssi } // Chooses lowest RSSI
        println("closestDevice "+closestDevice)
        closestDevice?.blePeripheral?.let { blePeripheral ->
            repairClubManager?.connectTo(closestDevice) { connectionEntry, connectionStage, connectionState ->
                repairClubManager?.stopScanning()
                timerStarted = false
                handleConnectionUpdates(connectionEntry, connectionStage, connectionState)

            }
        }
    }

    private fun handleConnectionUpdates(
        connectionEntry: ConnectionEntry,
        connectionStage: ConnectionStage,
        connectionState: ConnectionState?
    ) {
        if (connectionState != null) {
            connectionStates[connectionStage] = connectionState
        }
        this.connectionEntry = connectionEntry
        connectionHandler?.invoke(connectionEntry, connectionStage, connectionState)
        connectionListner?.didCheckScanStatus(connectionStage.name)
        when (connectionStage) {
            ConnectionStage.DEVICE_HANDSHAKE -> {
                println("Connection:: DEVICE_HANDSHAKE - $connectionState")
                when (connectionState){
                    ConnectionState.COMPLETED ->{
                        connectionEntry.deviceItem?.let { device ->
                            hardwareIdentifier = device.hardwareIdentifier ?: device.deviceIdentifier
                        }

                    }

                    else -> {}
                }

                this.connectedStatus = connectionState == ConnectionState.COMPLETED
            }

            ConnectionStage.MAIN_BUS_FOUND -> {
                println("Connection:: mainBusFound - $connectionState")

            }

            ConnectionStage.VIN_RECEIVED -> {
                println("Connection:: vinReceived - $connectionState")

                if (connectionState == ConnectionState.COMPLETED) {
                    vinNumber = connectionEntry.vin ?: ""

                    timer.cancel()
                    // Stop timer and stop to open No vin screen
                } else if (connectionState is ConnectionState.FAILED) {
                    // start 40 sec timer after complete
                    timer.start()
                }
            }

            ConnectionStage.VEHICLE_DECODED -> {
                println("Connection:: vehicleDecoded - $connectionState ${connectionEntry.vehicleEntry}")

                connectionEntry.vehicleEntry?.let { vehicleEntry ->
                    this.vehicleEntry = vehicleEntry

                    carName = vehicleEntry.shortDescription
                    yearstr = vehicleEntry.yearString
                    make = vehicleEntry.make
                    model = vehicleEntry.model
                    isAlreadyConnected = true
                    val entry = VehicleEntries()
                    entry.VIN = vehicleEntry.VIN
                    entry.shortDescription = vehicleEntry.shortDescription
                    entry.make = vehicleEntry.make
                    entry.model = vehicleEntry.model
                    entry.description = vehicleEntry.description
                    entry.engine = vehicleEntry.engine
                    entry.vehiclePowertrainType = vehicleEntry.vehiclePowertrainType.toString()
                    connectionListner?.didFetchVehicalInfo(entry)
                }

                getDeviceFirmwareVersion()

            }

            ConnectionStage.CONFIG_DOWNLOADED -> {
                println("Connection:: configDownloaded - $connectionState")
                CoroutineScope(Dispatchers.Main).launch {
                    connectionListner?.isReadyForScan(true,true)

                }
            }

            ConnectionStage.BUS_SYNCED_TO_CONFIG -> {
                println("Connection:: busSyncedToConfig - $connectionState")
                CoroutineScope(Dispatchers.Main).launch {
                        connectionListner?.isReadyForScan(true,false)
                }
            }

            ConnectionStage.MIL_CHECKING -> {
                println("Connection:: milChecking - $connectionState")
                if (!isMilOn) {
                    if (connectionState == ConnectionState.COMPLETED && connectionEntry.milOn != null) {
                        isMilOn = connectionEntry.milOn ?: false

                    }
                }
                connectionListner?.didFetchMil(isMilOn)
            }

            else -> {
                // Handle other stages
            }
        }
    }



    var noVinFunction = openNoVin()

    private val timer = object : CountDownTimer(40000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
        }

        override fun onFinish() {
            // do something
            noVinFunction?.let { it() }

        }
    }

    private fun openNoVin(): (() -> Unit)? = null

    public fun startScan() {
        scanID = ""
        timer.cancel()
        if (connectionStates[ConnectionStage.CONFIG_DOWNLOADED] is ConnectionState.FAILED) {
            startGenericScan()
        }
        if (connectionStates[ConnectionStage.BUS_SYNCED_TO_CONFIG] == ConnectionState.COMPLETED) {
            startAdvancedScan()
        } else if (connectionStates[ConnectionStage.BUS_SYNCED_TO_CONFIG] is ConnectionState.FAILED) {
            startGenericScan()
        }
    }

    fun startAdvancedScan() {

        repairClubManager?.startTroubleCodeScan(true) {
            GlobalScope.launch(Dispatchers.Main) {
                setProgressValue(it)
            }
        }
    }

    fun startGenericScan() {
        repairClubManager?.startTroubleCodeScan(false) {
            GlobalScope.launch(Dispatchers.Main) {
                setProgressValue(it)
            }
        }
    }

    fun setProgressValue(value: ScanProgressUpdate?) {
        // _progress.value = value
        value?.let { handleScanProgressUpdate(it) }
    }

    private fun handleScanProgressUpdate(update: ScanProgressUpdate) {
        when (update) {
            is ScanProgressUpdate.ScanStarted -> {
                connectionListner?.didUpdateProgress("ScanStarted","")
            }

            is ScanProgressUpdate.ModuleScanningUpdate -> {
                connectionListner?.didUpdateProgress("ModuleScanningUpdate","")

            }

            is ScanProgressUpdate.ModulesUpdate -> {
                connectionListner?.didUpdateProgress("DTC Scan update - ModulesUpdate, Number of Modules: ${update.modules.size}","")
            }

            is ScanProgressUpdate.ProgressUpdate -> {

                val progressPercent = (update.progress * 100).toInt()
                connectionListner?.didUpdateProgress("ProgressUpdate","${progressPercent}")
            }

            is ScanProgressUpdate.ScanFailed -> {
                connectionListner?.didUpdateProgress("Scanning:: DTC Scan update - ScanFailed - ${update.errors}","")
            }

            is ScanProgressUpdate.ScanSucceeded -> {
                connectionListner?.didUpdateProgress("ScanSucceeded","")

                repairClubManager?.stopTroubleCodeScan()
                // Copy the modules list to avoid concurrent modification during sorting
                val modulesCopy = update.modules.toList()

                val responseOrder = mapOf(
                    ResponseStatus.RESPONDED to 1,
                    ResponseStatus.AWAITING_DECODE to 2,
                    ResponseStatus.DID_NOT_RESPOND to 3,
                    ResponseStatus.UNKNOWN to 4
                )

                val sortedModules = modulesCopy.sortedWith(Comparator { module1, module2 ->
                    when {
                        module1.name.contains("Generic Codes") -> return@Comparator -1
                        module2.name.contains("Generic Codes") -> return@Comparator 1
                        responseOrder[module1.responseStatus]!! < responseOrder[module2.responseStatus]!! -> return@Comparator -1
                        responseOrder[module1.responseStatus]!! > responseOrder[module2.responseStatus]!! -> return@Comparator 1
                        module1.codes.isNotEmpty() && module2.codes.isEmpty() -> return@Comparator -1
                        module1.codes.isEmpty() && module2.codes.isNotEmpty() -> return@Comparator 1
                        else -> module1.name.compareTo(module2.name)
                    }
                })
                handleResponse(sortedModules)

            }
        }
    }

    fun handleResponse(modules: List<ModuleItem>) {
        dtcErrorCodeArray.clear()
        GlobalScope.launch(Dispatchers.IO) {
            val dtcErrorCodeList = mutableListOf<DTCResponseModel>()
            val distinctModules = modules.distinctBy { it.name }
            distinctModules.forEach { module ->
                var moduleName = module.name
                val dtcResponse = DTCResponseModel().apply {
                    id = module.id
                    moduleName = module.name
                    responseStatus = module.responseStatus.toString()
                    identifier = module.identifier
                }
                val codesList = module.codes.distinctBy { it.code }
                    .map { code ->
                        DTCResponse(
                            code.code,
                            code.description ?: "",
                            code.statusesDescription
                        ).apply {
                            moduleName = moduleName
                        }
                    }

                dtcResponse.dtcCodeArray = ArrayList(codesList)
                dtcErrorCodeList.add(dtcResponse)
            }

            withContext(Dispatchers.Main) {
                dtcErrorCodeArray.clear()
                val array =  dtcErrorCodeList.distinctBy { it.moduleName }
                dtcErrorCodeArray.addAll(array)
                connectionListner?.didReceivedCode(dtcErrorCodeArray)
            }
        }
        callScanApi()
    }


    fun callScanApi() {
        if (vinNumber.isEmpty()) {
            Log.e("callScanApi", "VIN number is empty")
            return
        }

        val dtcArr = dtcErrorCodeArray.flatMap { model ->
            model.dtcCodeArray.map { dtc ->
                val (category, _, subCategory) = separateArrays(dtc, model.moduleName)
                mapOf(
                    "dtc_status" to dtc.status,
                    "dtc_code" to dtc.dtcErrorCode,
                    "dtc_desc" to dtc.desc,
                    "modulename" to model.moduleName,
                    "category_name" to category,
                    "sub_category_name" to subCategory,
                    "category_id" to 1,
                    "sub_category_id" to 1
                )
            }
        }

        val (genericCount, oemCount) = dtcErrorCodeArray.partition {
            it.moduleName.contains("generic", ignoreCase = true) ||
                    it.moduleName.contains("standard", ignoreCase = true)
        }.let { (generic, oem) -> generic.sumOf { it.dtcCodeArray.size } to oem.sumOf { it.dtcCodeArray.size } }

        val uniqueControllerArr = dtcErrorCodeArray
            .filter { it.responseStatus == ResponseStatus.RESPONDED.name }
            .map { it.moduleName }
            .filterNot { it.contains("generic", ignoreCase = true) || it.contains("standard", ignoreCase = true) }
            .distinct()


        val parm = mapOf(
            "modules" to uniqueControllerArr,
            "vin_number" to vinNumber,
            "count_generic" to genericCount,
            "odometer" to "",
            "milcheck" to isMilOn,
            "scan_date" to getCurrentDateFormatted(),
            "version_firmware" to currentFirmwareVersion,
            "appVersion" to BuildConfig.SDK_VERSION,
            "count_oem" to oemCount,
            "year" to yearstr,
            "make" to make,
            "model" to model,
            "device_type" to "SDK Android",
            "serial_number" to hardwareIdentifier,
            "dtc_codes" to dtcArr,
        )


        val parameters = JSONObject().apply {
            parm.forEach { (key, value) ->
                when (value) {
                    is List<*> -> put(key, JSONArray(value)) // Convert lists to JSONArray
                    else -> put(key, value) // Put other values directly
                }
            }
        }


        val request = Request.Builder()
            .url(BASE_URL + scan)
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), parameters.toString()))
            .addHeader("Content-Type", "application/json")
            .addHeader("access-token", accesstoken)
            .addHeader("server-key", apiKey)
            .build()

        OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("callScanApi", "API Call failed: ${e.localizedMessage}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.body?.string()?.let { responseBody ->
                    Log.d("callScanApi", "Response: $responseBody")
                    JSONObject(responseBody).optString("id").takeIf { it.isNotEmpty() }?.let {
                        scanID = it
                    }
                }
            }
        })
    }


    fun getCurrentDateFormatted(): String {
        val currentDate = Date()
        val dateFormatter = SimpleDateFormat("MM-dd-yyyy h:mm a", Locale.getDefault())
        return dateFormatter.format(currentDate)
    }



    fun separateArrays(): Pair<List<DTCResponse>, List<DTCResponse>> {
        val activeStatusArray = ArrayList<DTCResponse>()
        val otherStatusArray = ArrayList<DTCResponse>()
        dtcErrorCodeArray.forEach { model ->
            model.dtcCodeArray.forEach { response ->
                response.name = model.moduleName
                response.section = getResponseFromJSON(model.moduleName)
                if (response.status?.lowercase() == "active" || response.status?.lowercase() == "current" ||
                    response.status?.lowercase() == "permanent" || response.status?.lowercase() == "warning light" ||
                    response.status?.lowercase()?.contains("confirmed") == true
                ) {
                    activeStatusArray.add(response)
                } else {
                    otherStatusArray.add(response)
                }
            }

        }


        if (otherStatusArray.isEmpty()) {
//        hideHistoryViews()
        }
        if (activeStatusArray.isEmpty()) {
//        hideSafetyViews()
        }
        //        var activeArray = appDelegate.dtcErrorCodeArray.filter { $0.status.lowercased() == "active" || $0.status.lowercased() == "current" || $0.status.lowercased() == "current" }
        //        let otherStatusArray = appDelegate.dtcErrorCodeArray.filter { $0.status.lowercased() != "active" }
        return Pair(activeStatusArray, otherStatusArray)
    }

    fun separateArrays(response: DTCResponse, moduleName: String): Triple<String, String, String> {
        val cat = getResponseFromJSON(msg = moduleName)
        return if (response.status.lowercase() in listOf("active", "current", "permanent", "warning light") ||
            response.status.lowercase().contains("confirmed")) {
            Triple("Attention", "", cat)
        } else {
            Triple("INFORMATIONAL", "INFORMATIONAL", cat)
        }
    }


    data class Section(
        var section: String = "",
        val dtcCodeArray: ArrayList<DTCResponse> = arrayListOf()
    )

    val sampleMap = mapOf(
        "modgenericule" to "Performance & Compliance",
        "generic codes" to "Other & Non Categorized",
        "electric power steering" to "Safety & Operability",
        "drive door motor" to "Comfort & Convenience"
    )

    fun getResponseFromJSON(msg: String): String {
        var value = "Other & Non Categorized"
        if (sampleMap.isNotEmpty()) {
            value = sampleMap[msg] ?: "Other & Non Categorized"
        }
        return value

    }


    public fun disconnectOBD() {
        scanID = ""
        repairClubManager?.stopTroubleCodeScan()
        repairClubManager?.disconnectFromDevice()
    }


    //MARK  Firmware update code below

    public fun updateFirmWare(reqVersion: String, reqReleaseLevel: FirmwareReleaseType?) {
        repairClubManager?.startDeviceFirmwareUpdate(
            reqVersion,
            reqReleaseLevel
        )
    }

    public fun getDeviceFirmwareVersion(): Result<String?>? {
        currentFirmwareVersion =
            repairClubManager?.getDeviceFirmwareVersion()?.getOrNull()
                ?: ""
        return repairClubManager?.getDeviceFirmwareVersion()
    }


    public  fun stopDeviceFirmwareUpdate() {
        repairClubManager?.stopDeviceFirmwareUpdate()
    }


    public fun subscribeToFirmwareVersionChanges(completionCallback: (String, String) -> Unit) {
        repairClubManager?.subscribeToFirmwareVersionChanges { s, s2 ->
            completionCallback.invoke(s, s2)
        }
    }

    public fun subscribeToFirmwareProgress(
        completionCallback: (FirmwareProgress) -> Unit,
        progressUpdate: (Double) -> Unit
    ) {
        repairClubManager?.subscribeToFirmwareProgress {
            progressUpdate.invoke(completionPercentageText(it))
            completionCallback.invoke(it)
        }
    }

    private fun completionPercentageText(progress: FirmwareProgress): Double {
        val currentBlockNumber = progress.currentBlockNumber.toDouble()
        val currentBlockChunkTotal = progress.currentBlockChunkTotal.toDouble()
        val currentBlockCurrentChunkNumber = progress.currentBlockCurrentChunkNumber.toDouble()
        val blockTotal = progress.blockTotal.toDouble()
        /*     var completionPercentageTextValue = 0.0
             // Avoid division by zero
             if (blockTotal > 0 || currentBlockChunkTotal > 0) else {
                 completionPercentageTextValue = 0.0
             }*/

        // Calculate total chunks so far
        val totalChunksSoFar =
            (currentBlockNumber * currentBlockChunkTotal) + currentBlockCurrentChunkNumber
        // Calculate total chunks overall
        val totalChunksOverall = blockTotal * currentBlockChunkTotal
        // Ensure totalChunksOverall is greater than 0 to avoid division by zero
        return if (totalChunksOverall > 0) {
            val completionPercentage = (totalChunksSoFar / totalChunksOverall) * 100
            completionPercentage
        } else {
            0.00
        }
    }


    //Emission Rediness
    val emissionList = ArrayList<EmissionRediness>()
    public fun getEmissionMonitors(callback: (List<EmissionRediness>) -> Unit) {
        isRedinessComplete = false
        emissionList.clear()
        repairClubManager?.subscribeToMonitors {
            if (it.isSuccess) {
                if (emissionList.isEmpty() || emissionList.size < 5) {
                    emissionList.clear()


                    it.getOrNull()?.forEach { monitor ->
                        Log.d("getEmissionRediness", "getEmissionRediness: $monitor")
                        val avail = monitor.readinessStatus?.first() ?: false
                        if (avail) {
                            emissionList.add(
                                EmissionRediness(
                                    name = monitor.valueName,
                                    description = monitor.description,
                                    available = monitor.readinessStatus?.first() ?: false,
                                    complete = monitor.readinessStatus?.last() ?: false
                                )
                            )
                        }
                    }
                } else {

                    repairClubManager?.endRequestMonitorsTimer()
                    disconnectEmissionHandler?.invoke()
                    isRedinessComplete = true
                }


                CoroutineScope(Dispatchers.Main).launch {
                    emissionList.removeAll { it.name.contains("MIL") }
                    //val checkPassFail = checkPassFailEmission()
                   // emissionList.map { it.finalstatus = checkPassFail }
                   callback.invoke(emissionList)

                }

            } else if (it.isFailure) {


            }
        }


    }

     fun checkPassFailEmission(): String {
        val nonComplete = emissionList.filter { !it.complete }
        if (emissionList.isEmpty() || emissionList.size <= 5){
            passFail = ""
            return ""
        }
        if(fuelType == "Gasoline"){
            val name = nonComplete.filter{it.name == "Evaporative System"}
            passFail = if(nonComplete.size == 1 && !name.isEmpty()){
                "PASS"
            }else if(nonComplete.size == 1 && name.isEmpty()){
                "FAIL"
            }else if(nonComplete.size > 1){
                "FAIL"
            }else{
                "PASS"
            }
        }else{
            val name = nonComplete.filter{it.name.contains("EGR/VVT System") || it.name.contains("NMHC Catalyst")}
            passFail = if(nonComplete.size >= 1 && name.isEmpty()){
                "FAIL"
            }else if(nonComplete.size == 2 && name.size == 2){
                "PASS"
            }else if(nonComplete.size == 1 && name.size == 1){
                "PASS"
            }else if(nonComplete.size > 2){
                "FAIL"
            }else{
                "PASS"
            }
        }

        return passFail
    }




    var warmUpCyclesSinceCodesCleared = 0.0
    var warmUpCyclesSinceCodesClearedstr = "-"

    var distanceSinceCodesCleared = 0
    var distanceSinceCodesClearedstr = "-"

    var timeSinceTroubleCodesCleared = 0
    var timeSinceTroubleCodesClearedstr = "-"

    var timeRunWithMILOn = 0
    var timeRunWithMILOnstr = "-"

    public  fun getRecentCodeReset(callbackWarupCycle: (String) -> Unit,callbackDistanceSinceCodeCleared: (String) -> Unit,callbackTimeSinceCodeCleared: (String) -> Unit,callbackTimeRunWithMilOn: (String) -> Unit){
        clearCodesReset()
        warm_up_cycles_since_codes_cleared{
            callbackWarupCycle.invoke(it)
        }
        distance_since_codes_cleared{
            callbackDistanceSinceCodeCleared.invoke(it)
        }
        time_since_trouble_codes_cleared {
            callbackTimeSinceCodeCleared.invoke(it)
        }
        time_run_with_MIL_on{
            callbackTimeRunWithMilOn.invoke(it)
        }

    }

    public fun warm_up_cycles_since_codes_cleared(callback: (String) -> Unit) {
        repairClubManager?.requestDataPoint("0130") {
            if (it.isNotEmpty()) {

                val scientificNotation = getScientificNotation(inputString = it)

                // Safely parse to Double or set to 0.0 if empty
                warmUpCyclesSinceCodesCleared = scientificNotation.toDoubleOrNull() ?: 0.0
                warmUpCyclesSinceCodesClearedstr = "-"
                warmUpCyclesSinceCodesClearedstr = warmUpCyclesSinceCodesCleared.toInt().toString()
                if(warmUpCyclesSinceCodesCleared == 0.0){
                    warmUpCyclesSinceCodesClearedstr = "-"
                }
                callback.invoke(warmUpCyclesSinceCodesClearedstr)
            }
        }
    }




    public fun distance_since_codes_cleared(callback: (String) -> Unit) {
        repairClubManager?.requestDataPoint("0131") { code ->
            if(code.isNullOrEmpty()){
                return@requestDataPoint
            }
            val notation = getScientificNotation(code)
            val distanceSinceCodesCleareddouble = (notation.toDoubleOrNull() ?: 0.0) / 1.609
            distanceSinceCodesCleared = distanceSinceCodesCleareddouble.toInt()
            distanceSinceCodesClearedstr = distanceSinceCodesCleared.toString()
            if(distanceSinceCodesCleared == 0){
                distanceSinceCodesClearedstr = "-"
            }
            callback.invoke(distanceSinceCodesClearedstr)
        }
    }


    public fun time_since_trouble_codes_cleared(callback: (String) -> Unit) {
        repairClubManager?.requestDataPoint("014E") {
            // if (!statusCodes.contains("time_since_trouble_codes_cleared")) {
            if(it.isNotEmpty()) {
                timeSinceTroubleCodesClearedstr = "-"
                val notation = getScientificNotation(it)
                val timeSinceTroubleCodesCleareddoble = (notation.toDoubleOrNull() ?: 0.0) / 60
                timeSinceTroubleCodesCleared = timeSinceTroubleCodesCleareddoble.toInt()
                timeSinceTroubleCodesClearedstr = timeSinceTroubleCodesCleared.toString()
                if(timeSinceTroubleCodesCleared == 0){
                    timeSinceTroubleCodesClearedstr = "-"
                }}
            callback.invoke(timeSinceTroubleCodesClearedstr)

        }


    }

    fun clearCode(){
        repairClubManager?.clearGenericCodes {

        }
    }

    public fun  clearCodesReset(){
        timeRunWithMILOn = 0
        timeRunWithMILOnstr = "-"
        timeSinceTroubleCodesCleared = 0
        timeSinceTroubleCodesClearedstr = "-"
        distanceSinceCodesCleared = 0
        distanceSinceCodesClearedstr = "-"
        warmUpCyclesSinceCodesCleared = 0.0
        warmUpCyclesSinceCodesClearedstr = "-"
    }

    public fun time_run_with_MIL_on(callback: (String) -> Unit) {

        repairClubManager?.requestDataPoint("014D") {
            if(it.isNotEmpty()) {
                timeRunWithMILOnstr = "-"
                val notation = getScientificNotation(it)
                val timeSinceTroubleCodesCleareddoble = (notation.toDoubleOrNull() ?: 0.0) / 60
                timeRunWithMILOn = timeSinceTroubleCodesCleareddoble.toInt()
                timeRunWithMILOnstr = timeRunWithMILOn.toString()
                if(timeRunWithMILOn == 0){
                    timeRunWithMILOnstr = "-"
                }}
            callback.invoke(timeRunWithMILOnstr)
        }

    }

    fun getScientificNotation(inputString: String): String {
        var notation = ""

        // Check if inputString is empty before conversion
        if (inputString.isNotBlank()) {
            // Attempt to convert the input string to a Double
            try {
                val sciNotationValue = inputString.toDoubleOrNull()

                if (sciNotationValue != null) {
                    println("Scientific Notation Value: $sciNotationValue")
                    notation = sciNotationValue.roundToInt().toString()
                }
            }catch (ex:Exception){

            }

        }
        return notation
    }




    public fun isManualResetSuspected(): Int {
        return if(warmUpCyclesSinceCodesClearedstr == "-" || distanceSinceCodesClearedstr == "-"){
            -1
        }else if (distanceSinceCodesCleared >= 100 && warmUpCyclesSinceCodesCleared > 25){
            1
        }else{
            0
        }
    }

   public fun getRepairCostSummary(vinNumber:String,dtcErrorCodeArray:List<DTCResponseModel>,callback: (Boolean,JSONObject?) -> Unit) {
       if(dtcErrorCodeArray.isEmpty()){
           callback.invoke(false,null)
           return
       }
       processDtcCodes(vinNumber,dtcErrorCodeArray) { status, json ->
            callback.invoke(status,json)
        }
    }


    fun processDtcCodes(
        vinNumber:String,
        dtcErrorCodeArray: List<DTCResponseModel>,
        callback: (Boolean, JSONObject?) -> Unit
    ) {
         val dtcArr = mutableListOf<Map<String, String>>()
        dtcErrorCodeArray.forEach { dtcResponseModel ->
            val module = dtcResponseModel.moduleName
            dtcResponseModel.dtcCodeArray.forEach { dtcResponse ->
                val existingCodes = dtcArr.map { it["code"] }
                if (!existingCodes.contains(dtcResponse.dtcErrorCode)) {
                    val str = mapOf("code" to dtcResponse.dtcErrorCode, "module" to module)
                    dtcArr.add(str)
                }
            }
        }


        if (!scanID.isEmpty()) {
            val dtcArrChunkSize = 5
            val dtcArrChunks = dtcArr.chunked(dtcArrChunkSize)
            var successfulChunks = 0
            var failedChunks = 0

            dtcArrChunks.forEach { chunk ->
                val chunkParams = mapOf("dtcCode" to chunk, "vin" to vinNumber)
                callApiJSON(BASE_URL+ chatgpt,chunkParams) { status,response ->
                    CoroutineScope(Dispatchers.Main).launch {
                        if (status) {
                            successfulChunks++
                        } else {
                            failedChunks++
                        }

                        if (successfulChunks + failedChunks == dtcArrChunks.size) {
                            if (failedChunks == dtcArrChunks.size) {
                                callback.invoke(status,null)
                                return@launch
                            }
                            val jsonResponse = response?.body?.string()
                            val jsonObject = jsonResponse?.let { JSONObject(it) }
                            postRepairCost(dtcErrorCodeArray,jsonObject)
                            callback.invoke(status,jsonObject)
                        }
                    }
                }
            }
        }
    }

    private fun callApiJSON(url:String,params: Map<String, Any>, callback: (Boolean,Response?) -> Unit) {
        val client = OkHttpClient()
        val requestBody = RequestBody.create("application/json".toMediaType(), Gson().toJson(params))
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false,null)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful,response)
            }
        })
    }

    private fun postRepairCost(dtcErrorCodeArray: List<DTCResponseModel>, jsonObject: JSONObject?) {
        // Implement the API call for repair cost
            if(!scanID.isEmpty() && !dtcErrorCodeArray.isEmpty()){
                val response = makeJsonOfResponse(jsonObject)
                callApiJSON(BASE_URL+ repaircost,response){status,response ->

                }

            }

    }
    private fun makeJsonOfResponse(jsonObject: JSONObject?): Map<String, Any> {
        return mapOf(
            "scan_id" to scanID,
            "repaircost" to jsonObject.toString(),
        )
    }


}
public  data class  EmissionRediness (
    var name:String = "",
    var available:Boolean = false,
    var complete:Boolean = false,
    var description:String = ""

    )