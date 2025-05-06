package com.cardr.obdiqsdk

import DTCResponseModel
import com.cardr.obdiqandroidsdk.VehicleEntries
import com.repairclub.repaircludsdk.models.DeviceItem

public interface ConnectionListner {
    public  fun didDevicesFetch(foundedDevices:List<DeviceItem>?)
    public  fun didCheckScanStatus(status:String)
    public fun didFetchVehicalInfo(vehicleEntry: VehicleEntries)
    public fun didFetchMil(mil: Boolean)
    public fun isReadyForScan(status: Boolean,isGenric:Boolean)

    public fun didUpdateProgress(progressStatus: String, percent:String)
    public fun didReceivedCode(model: List<DTCResponseModel>?)
    public fun didReceivedRepairCost(jsonString: String)

}