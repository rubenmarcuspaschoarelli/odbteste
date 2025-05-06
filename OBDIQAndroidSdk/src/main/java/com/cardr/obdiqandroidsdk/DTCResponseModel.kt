import java.util.UUID

data class DTCResponse(
    var dtcErrorCode: String = "",
    var desc: String = "",
    var status: String = ""
){
    public var name: String = ""
    public var section: String = ""
}

class DTCResponseModel {
    var id: String? = null
    var moduleName: String = ""
    var responseStatus: String? = null
    var identifier: String = ""
    var dtcCodeArray: MutableList<DTCResponse> = mutableListOf()

    // Function to remove duplicate DTCResponses based on dtcErrorCode
   public fun removeDuplicateDTCResponses() {
        val uniqueDTCErrorCodes = mutableSetOf<String>()
        val uniqueDTCResponses = mutableListOf<DTCResponse>()

        for (dtcResponse in dtcCodeArray) {
            if (uniqueDTCErrorCodes.add(dtcResponse.dtcErrorCode)) {
                uniqueDTCResponses.add(dtcResponse)
            }
        }

        dtcCodeArray = uniqueDTCResponses
    }
}
