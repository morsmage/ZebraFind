package com.example.zebrafind

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.zebra.rfid.api3.*
import com.zebra.rfid.api3.Antennas.AntennaRfConfig
import com.zebra.rfid.api3.Antennas.SingulationControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

var readers = Readers()
var availableRFIDReaderList = ArrayList<ReaderDevice>()
var readerDevice = ReaderDevice()
lateinit var reader: RFIDReader
lateinit var rfModeTable: RFModeTable
var eventHandler = EventHandler()
var MAX_POWER = 200
var licensePlate = ""

class MainActivity : AppCompatActivity() {

    val textView: TextView by lazy { findViewById(R.id.textView) }
    val scanText: EditText by lazy { findViewById(R.id.scanText) }

    val TAG = "RFID"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find the first RFID reader and connect to it
        readers = Readers(applicationContext, ENUM_TRANSPORT.SERVICE_SERIAL)
        CoroutineScope(Dispatchers.IO).launch {
            availableRFIDReaderList = readers.GetAvailableRFIDReaderList()
            withContext(Dispatchers.Main) {
                readerDevice = availableRFIDReaderList[0]
                reader = readerDevice.rfidReader
                try {
                    reader.connect()
                    configureReader()
                    textView.text = "Reader Connected"
                } catch (e: InvalidUsageException) {
                    e.printStackTrace()
                } catch (e: OperationFailureException) {
                    e.printStackTrace()
                }

                // Get reader capabilities
                Log.d(TAG, "Reader ID: " + reader.ReaderCapabilities.ReaderID.id)
                Log.d(TAG, "ModelName: " + reader.ReaderCapabilities.modelName)
                Log.d(TAG, "Communication Standard: " + reader.ReaderCapabilities.communicationStandard)
                Log.d(TAG, "Country Code: " + reader.ReaderCapabilities.countryCode)
                Log.d(TAG, "FirmwareVersion: " + reader.ReaderCapabilities.firwareVersion)
                Log.d(TAG, "RSSI Filter: " + reader.ReaderCapabilities.isRSSIFilterSupported)
                Log.d(TAG, "Tag Event Reporting: " + reader.ReaderCapabilities.isTagEventReportingSupported)
                Log.d(TAG, "Tag Locating Reporting: " + reader.ReaderCapabilities.isTagLocationingSupported)
                Log.d(TAG, "NXP Command Support: " + reader.ReaderCapabilities.isNXPCommandSupported)
                Log.d(TAG, "BlockEraseSupport: " + reader.ReaderCapabilities.isBlockEraseSupported)
                Log.d(TAG, "BlockWriteSupport: " + reader.ReaderCapabilities.isBlockWriteSupported)
                Log.d(TAG, "BlockPermalockSupport:" + reader.ReaderCapabilities.isBlockPermalockSupported)
                Log.d(TAG, "RecommissionSupport: " + reader.ReaderCapabilities.isRecommisionSupported)
                Log.d(TAG, "WriteWMISupport: " + reader.ReaderCapabilities.isWriteUMISupported)
                Log.d(TAG, "RadioPowerControlSupport: " + reader.ReaderCapabilities.isRadioPowerControlSupported)
                Log.d(TAG, "HoppingEnabled: " + reader.ReaderCapabilities.isHoppingEnabled)
                Log.d(TAG, "StateAwareSingulationCapable: " + reader.ReaderCapabilities.isTagInventoryStateAwareSingulationSupported)
                Log.d(TAG, "UTCClockCapable: " + reader.ReaderCapabilities.isUTCClockSupported)
                Log.d(TAG, "NumOperationsInAccessSequence: " + reader.ReaderCapabilities.maxNumOperationsInAccessSequence)
                Log.d(TAG, "NumPreFilters: " + reader.ReaderCapabilities.maxNumPreFilters)
                Log.d(TAG, "NumAntennaSupported: " + reader.ReaderCapabilities.numAntennaSupported)

                // Create rf mode table
                rfModeTable = reader.ReaderCapabilities.RFModes.getRFModeTableInfo(0)
            }
        }

        scanText.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable) {
                licensePlate = scanText.text.toString().replace("\\s+".toRegex(), "")
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })
    }

    fun configureReader() {
        if (reader.isConnected) {
            val triggerInfo = TriggerInfo()
            triggerInfo.StartTrigger.triggerType = START_TRIGGER_TYPE.START_TRIGGER_TYPE_HANDHELD
            triggerInfo.StartTrigger.Handheld.handheldTriggerEvent = HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED
            triggerInfo.StopTrigger.triggerType = STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_HANDHELD_WITH_TIMEOUT
            triggerInfo.StopTrigger.Handheld.handheldTriggerEvent = HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED
            triggerInfo.StopTrigger.Handheld.handheldTriggerTimeout = 3000

            triggerInfo.StartTrigger.triggerType = START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE
            triggerInfo.StopTrigger.triggerType = STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE

            try {
                reader.Events.addEventsListener(eventHandler) // Receive events from reader
                reader.Events.setHandheldEvent(true) // Handheld event
                reader.Events.setInventoryStartEvent(true)
                reader.Events.setInventoryStopEvent(true)
                reader.Events.setTagReadEvent(true) // Tag read event
                reader.Events.setAttachTagDataWithReadEvent(true) // Tag read with data
                reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true) // Trigger mode without barcode scanner
                reader.Config.startTrigger = triggerInfo.StartTrigger // Set start trigger
                reader.Config.stopTrigger = triggerInfo.StopTrigger // Set stop trigger
                MAX_POWER = reader.ReaderCapabilities.transmitPowerLevelValues.size - 1 // Power levels are index based, set to max (300)

                // set antenna configurations
                val config: AntennaRfConfig = reader.Config.Antennas.getAntennaRfConfig(1)
                config.transmitPowerIndex = MAX_POWER
                config.setrfModeTableIndex(0)
                config.tari = 0
                reader.Config.Antennas.setAntennaRfConfig(1, config)

                // Set the singulation control
                val s1_singulationControl: SingulationControl = reader.Config.Antennas.getSingulationControl(1)
                s1_singulationControl.session = SESSION.SESSION_S0
                s1_singulationControl.Action.inventoryState = INVENTORY_STATE.INVENTORY_STATE_A
                s1_singulationControl.Action.slFlag = SL_FLAG.SL_ALL
                reader.Config.Antennas.setSingulationControl(1, s1_singulationControl)

                reader.Actions.PreFilters.deleteAll() // Delete any prefilters

            } catch (e: InvalidUsageException) {
                e.printStackTrace()
            } catch (e: OperationFailureException) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        reader.disconnect()
        readers.Dispose()
    }

}