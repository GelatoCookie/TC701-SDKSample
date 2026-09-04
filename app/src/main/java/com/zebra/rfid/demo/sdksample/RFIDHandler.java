package com.zebra.rfid.demo.sdksample;

import static android.util.Log.d;
import static android.util.Log.i;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;
import android.media.AudioManager;
import android.media.ToneGenerator;

import com.zebra.rfid.api3.ACCESS_OPERATION_CODE;
import com.zebra.rfid.api3.ACCESS_OPERATION_STATUS;
import com.zebra.rfid.api3.Antennas;
import com.zebra.rfid.api3.BuildConfig;
import com.zebra.rfid.api3.ENUM_TAGQUIET_MASK;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE;
import com.zebra.rfid.api3.FILTER_ACTION;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.IRFIDLogger;
import com.zebra.rfid.api3.ImpinjExtensions;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.LOCK_DATA_FIELD;
import com.zebra.rfid.api3.LOCK_PRIVILEGE;
import com.zebra.rfid.api3.MEMORY_BANK;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.PreFilters;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RegionInfo;
import com.zebra.rfid.api3.RegulatoryConfig;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.SL_FLAG;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATE_AWARE_ACTION;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.TARGET;
import com.zebra.rfid.api3.TRUNCATE_ACTION;
import com.zebra.rfid.api3.TagAccess;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.TriggerInfo;
import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.FirmwareUpdateEvent;
import com.zebra.scannercontrol.IDcsSdkApiDelegate;
import com.zebra.scannercontrol.SDKHandler;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


class RFIDHandler implements IDcsSdkApiDelegate, Readers.RFIDReaderEventHandler {

    final static String TAG = "RFID_SAMPLE";
    private Readers readers;
    private ArrayList<ReaderDevice> availableRFIDReaderList;
    private ReaderDevice readerDevice;
    private RFIDReader reader;
    TextView textView;
    private EventHandler eventHandler;
    private MainActivity context;
    private SDKHandler sdkHandler;
    private ArrayList<DCSScannerInfo> scannerList;
    private ImpinjExtensions impinjExtensions;
    private int scannerID;
    private static final int DEFAULT_MAX_POWER = 270;
    private static final int DEVICE_STD_MODE = 0;
    private static final int DEVICE_PREMIUM_PLUS_MODE = 1;
    private int maxPower = DEFAULT_MAX_POWER;
    private ToneGenerator toneGenerator;
    private volatile boolean bIsReading = false;
    private volatile boolean bAccessSequenceRunning = false;
    private boolean hasConnectedBefore = false;

    // In case of RFD8500 change reader name with intended device below from list of paired RFD8500
    // If barcode scan is available in RFD8500, for barcode scanning change mode using mode button on RFD8500 device. By default it is set to RFID mode
    String readerNamebt = "RFD40+_211545201D0011";
    String readerName = "RFD4031-G10B700-US";
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    void onCreate(MainActivity activity) {
        context = activity;
        textView = activity.statusTextViewRFID;
        scannerList = new ArrayList<>();
        if (toneGenerator == null) {
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize ToneGenerator", e);
            }
        }
        InitSDK();
        context.updateTitleWithSdkVersion(getVersionInfo());
    }

    // App entered the foreground: (re)initialize the SDK and connect the reader.
    void onForeground(MainActivity activity) {
        onCreate(activity);
    }

    // App entered the background: disconnect, clean up and dispose the SDK.
    void onBackground() {
        dispose();
    }

    private void playTone(int toneType) {
        if (toneGenerator != null) {
            toneGenerator.startTone(toneType, 500);
        }
    }

    // Descending two-beep "phone drop" sound played when the reader disconnects.
    private void playPhoneDropTone() {
        if (toneGenerator == null) {
            return;
        }
        executorService.execute(() -> {
            toneGenerator.startTone(ToneGenerator.TONE_SUP_INTERCEPT_ABBREV, 150);
            try {
                Thread.sleep(180);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_CALLDROP_LITE, 300);
        });
    }

    @Override
    public void dcssdkEventScannerAppeared(DCSScannerInfo dcsScannerInfo) {

    }

    @Override
    public void dcssdkEventScannerDisappeared(int i) {

    }

    @Override
    public void dcssdkEventCommunicationSessionEstablished(DCSScannerInfo dcsScannerInfo) {

    }

    @Override
    public void dcssdkEventCommunicationSessionTerminated(int i) {

    }

    @Override
    public void dcssdkEventBarcode(byte[] barcodeData, int barcodeType, int fromScannerID) {
        String s = new String(barcodeData);
        context.barcodeData(s);
        Log.d(TAG,"barcaode ="+ s);
    }

    @Override
    public void dcssdkEventImage(byte[] bytes, int i) {

    }

    @Override
    public void dcssdkEventVideo(byte[] bytes, int i) {

    }

    @Override
    public void dcssdkEventBinaryData(byte[] bytes, int i) {

    }

    @Override
    public void dcssdkEventFirmwareUpdate(FirmwareUpdateEvent firmwareUpdateEvent) {

    }

    @Override
    public void dcssdkEventAuxScannerAppeared(DCSScannerInfo dcsScannerInfo, DCSScannerInfo dcsScannerInfo1) {

    }



// TEST BUTTON functionality
    // following two tests are to try out different configurations features

    public String Test1() {
        if (!isReaderConnected())
            return "Not connected";
        try {
            applyAntennaConfig(100, 0, 0);
        } catch (Throwable t) {
            return "Failed: " + t.getMessage();
        }
        return "Antenna power Set to 220";
    }



    public String Test2() {
        if (!isReaderConnected())
            return "Not connected";
        try {
            applySingulationControl(SESSION.SESSION_S2, INVENTORY_STATE.INVENTORY_STATE_A);
        } catch (Throwable t) {
            return "Failed: " + t.getMessage();
        }
        return "Session set to S2";
    }

    public String Defaults() {
        if (!isReaderConnected())
            return "Not connected";
        try {
            applyAntennaConfig(maxPower, 0, 0);
            applySingulationControl(SESSION.SESSION_S0, INVENTORY_STATE.INVENTORY_STATE_A);
        } catch (Throwable t) {
            return "Failed to apply defaults: " + t.getMessage();
        }
        return "Default settings applied";
    }

    private void applyAntennaConfig(int powerIndex, int rfModeIndex, int tari) throws InvalidUsageException, OperationFailureException {
        Antennas.AntennaRfConfig config = reader.Config.Antennas.getAntennaRfConfig(1);
        config.setTransmitPowerIndex(powerIndex);
        config.setrfModeTableIndex(rfModeIndex);
        config.setTari(tari);
        reader.Config.Antennas.setAntennaRfConfig(1, config);
    }

    private void applySingulationControl(SESSION session, INVENTORY_STATE state) throws InvalidUsageException, OperationFailureException {
        Antennas.SingulationControl singulationControl = reader.Config.Antennas.getSingulationControl(1);
        singulationControl.setSession(session);
        singulationControl.Action.setInventoryState(state);
        singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL);
        reader.Config.Antennas.setSingulationControl(1, singulationControl);
    }

    private boolean isReaderConnected() {
        if (reader != null && reader.isConnected())
            return true;
        else {
            Log.d(TAG, "reader is not connected");
            return false;
        }
    }

    //
    //  Activity life cycle behavior
    //

    String onResume() {
        return handleConnect();
    }

    void onPause() {
        handleDisconnect();
    }

    void onDestroy() {
        dispose();
    }

    //
    // RFID SDK
    //
    private void InitSDK() {
        Log.d(TAG, "InitSDK");
        IRFIDLogger.getLogger("sample app").EnableDebugLogs(true);
        if (readers == null) {
            context.showProgress(true);
            context.setRfidStatus(hasConnectedBefore ? "Reconnecting..." : "Connecting...");
            executorService.execute(this::createInstance);
        } else
            performConnect();
    }

    private void createInstance() {
        Log.d(TAG, "createInstance");
        InvalidUsageException invalidUsageException = null;
        ENUM_TRANSPORT transport = ENUM_TRANSPORT.ALL;
        try {
            readers = new Readers(context, ENUM_TRANSPORT.BLUETOOTH);
            availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
            if (availableRFIDReaderList.isEmpty()) {
                Log.d(TAG, "Reader not available in SERVICE_USB Transport trying with BLUETOOTH transport");
                readers.setTransport(ENUM_TRANSPORT.BLUETOOTH);
                transport = ENUM_TRANSPORT.BLUETOOTH;
                availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
            }
            if (availableRFIDReaderList.isEmpty()) {
                Log.d(TAG, "Reader not available in BLUETOOTH Transport trying with SERVICE_SERIAL transport");
                readers.setTransport(ENUM_TRANSPORT.SERVICE_SERIAL);
                transport = ENUM_TRANSPORT.SERVICE_SERIAL;
                availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
            }
            if (availableRFIDReaderList.isEmpty()) {
                Log.d(TAG, "Reader not available in SERVICE_SERIAL Transport trying with SERVICE_USB transport");
                readers.setTransport(ENUM_TRANSPORT.SERVICE_USB);
                transport = ENUM_TRANSPORT.SERVICE_USB;
                availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
            }
            if (availableRFIDReaderList.isEmpty()) {
                Log.d(TAG, "Reader not available in SERVICE_USB Transport trying with QC_SERIAL transport");
                readers.setTransport(ENUM_TRANSPORT.QC_SERIAL);
                transport = ENUM_TRANSPORT.QC_SERIAL;
                availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
            }
            if (availableRFIDReaderList.isEmpty()) {
                Log.d(TAG, "Reader not available in QC_SERIAL Transport trying with RE_SERIAL transport");
                readers.setTransport(ENUM_TRANSPORT.RE_SERIAL);
                transport = ENUM_TRANSPORT.RE_SERIAL;
                availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
            }

            if (!availableRFIDReaderList.isEmpty()) {
                Log.d(TAG, "Found Reader, Size=" + availableRFIDReaderList.size());
                Log.d(TAG, "Init OK for Transport = " + transport.toString());
            }
        } catch (Throwable t) {
            reportError(t);
            if (t instanceof InvalidUsageException) {
                invalidUsageException = (InvalidUsageException) t;
            } else {
                invalidUsageException = new InvalidUsageException("SDK Error: " + t.getMessage(), "");
            }
            t.printStackTrace();
        }

        final InvalidUsageException finalException = invalidUsageException;
        context.runOnUiThread(() -> {
            if (finalException != null) {
                context.sendToast(context.getString(R.string.status_failed_get_readers) + "\n" + finalException.getInfo());
                readers = null;
                context.showProgress(false);
                context.setConnectionButtons(false);
            } else if (availableRFIDReaderList.isEmpty()) {
                context.sendToast(context.getString(R.string.status_no_readers));
                textView.setText("No Reader Found\r\nPower on Reader or Attach");
                readers = null;
                context.showProgress(false);
                context.setConnectionButtons(false);
            } else {
                connectReader();
            }
        });
    }

    private synchronized void connectReader() {
        if (!isReaderConnected()) {
            context.showProgress(true);
            executorService.execute(this::connectionTask);
        }
    }

    public void performConnect() {
        connectReader();
    }

    public void performDisconnect() {
        executorService.execute(this::handleDisconnect);
    }

    private synchronized void handleDisconnect() {
        disconnectInternal();
        playPhoneDropTone();
        context.setConnectionButtons(false);
        context.UpdateUI_statusTextViewRFID("Disconnected");
    }

    private void connectionTask() {
        Log.d(TAG, "connectionTask");
        context.clearStatusMessages();
        GetAvailableReader();
        String res = "";
        if (reader != null) {
            res = handleConnect();
            if (res.contains("RFID_READER_REGION_NOT_CONFIGURED")) {
                RegulatoryConfig regCfg = null;
                try {
                    regCfg = reader.Config.getRegulatoryConfig();
                    if (regCfg != null) {
                        RegionInfo regionInfo = reader.ReaderCapabilities.SupportedRegions.getRegionInfo(0);
                        regCfg.setRegion(regionInfo.getRegionCode());
                        regCfg.setIsHoppingOn(regionInfo.isHoppingConfigurable());
                        regCfg.setEnabledChannels(regionInfo.getSupportedChannels());
                        regCfg.setStandardName(regionInfo.getName());
                        reader.Config.setRegulatoryConfig(regCfg);
                        res = handleConnect();
                    }
                } catch (Throwable e) {
                    reportError(e);
                    e.printStackTrace();
                }
            }
            ConfigureReader();
        } else {
            res = context.getString(R.string.status_reader_not_found);
        }

        final String result = res;
        context.runOnUiThread(() -> {
            Log.d(TAG, "UpdateUI_statusTextViewRFID " + result);
            textView.setText(result);
            context.UpdateUI_statusTextViewRFID(result);
            context.showProgress(false);
            context.setConnectionButtons(result != null && result.startsWith("Connected"));
        });
    }

    private synchronized void GetAvailableReader() {
        Log.d(TAG, "GetAvailableReader");
        if (readers != null) {
            readers.attach(this);
            try {
                ArrayList<ReaderDevice> availableReaders = readers.GetAvailableRFIDReaderList();
                if (availableReaders != null && !availableReaders.isEmpty()) {
                    availableRFIDReaderList = availableReaders;
                    // if single reader is available then connect it
                    Log.d(TAG, "Available readers to connect = " + availableRFIDReaderList.size());
                    if (availableRFIDReaderList.size() == 1) {
                        readerDevice = availableRFIDReaderList.get(0);
                        reader = readerDevice.getRFIDReader();
                    } else {
                        // search reader specified by name
                        for (ReaderDevice device : availableRFIDReaderList) {
                            Log.d(TAG, "device: " + device.getName());
                            if (device.getName().startsWith(readerName)) {
                                readerDevice = device;
                                reader = readerDevice.getRFIDReader();
                            }
                        }
                    }
                    if (impinjExtensions == null)
                        impinjExtensions = new ImpinjExtensions(reader);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    // handler for receiving reader appearance events
    @Override
    public void RFIDReaderAppeared(ReaderDevice readerDevice) {
        Log.d(TAG, "RFIDReaderAppeared " + readerDevice.getName());
        context.sendToast("Reader appeared: " + readerDevice.getName());
        if(reader!=null && reader.isConnected()) {
            context.sendToast("Reader in USB host mode");
            if (readerDevice.getName().equals(reader.getHostName()))
                performDisconnect();
        }
        connectReader();
    }

    @Override
    public void RFIDReaderDisappeared(ReaderDevice readerDevice) {
        Log.d(TAG, "RFIDReaderDisappeared " + readerDevice.getName());
        context.sendToast("Reader disappeared: " + readerDevice.getName());
        // Only tear down if the reader that vanished is the one we are connected to.
        if (reader != null && readerDevice.getName().equals(reader.getHostName()))
            performDisconnect();
    }


    private synchronized String handleConnect() {
        if (reader != null) {
            Log.d(TAG, "connect " + reader.getHostName());
            try {
                if (!reader.isConnected()) {
                    // Establish connection to the RFID Reader
                    reader.connect();
                    //Call this function if the readerdevice supports scanner to setup scanner SDK
                    //setupScannerSDK();
                }
                if (reader.isConnected()) {
                    hasConnectedBefore = true;
                    playTone(ToneGenerator.TONE_SUP_CONFIRM);
                    return "Connected: " + reader.getHostName();
                }
            } catch (InvalidUsageException e) {
                e.printStackTrace();
            } catch (OperationFailureException e) {
                e.printStackTrace();
                Log.d(TAG, "OperationFailureException " + e.getVendorMessage());
                String des = e.getResults().toString();
                return "Connection failed: " + e.getVendorMessage() + " " + des;
            } catch (Throwable t) {
                t.printStackTrace();
                return "Connection failed: " + t.getMessage();
            }
        }
        return "";
    }

    private void ConfigureReader() {
        Log.d(TAG, "ConfigureReader " + reader.getHostName());
        IRFIDLogger.getLogger("SDKSAmpleApp").EnableDebugLogs(true);
        if (reader.isConnected()) {
//            TriggerInfo triggerInfo = new TriggerInfo();
//            triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
//            triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
            try {
                // receive events from reader
                if (eventHandler == null)
                    eventHandler = new EventHandler();
                reader.Events.addEventsListener(eventHandler);
                // HH event
                reader.Events.setHandheldEvent(true);
                // tag event with tag data
                reader.Events.setTagReadEvent(true);
                reader.Events.setAttachTagDataWithReadEvent(false);

                reader.Events.setInventoryStartEvent(true);
                reader.Events.setInventoryStopEvent(true);

                bIsReading = false;

                context.UpdateUI_statusTextViewRFID("Connected: " + reader.getHostName());

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }


    public void setupScannerSDK(){
        if (sdkHandler == null)
        {
            sdkHandler = new SDKHandler(context);
            //For cdc device
            DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_USB_CDC);

            //For bluetooth device
            DCSSDKDefs.DCSSDK_RESULT btResult = sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_LE);
            DCSSDKDefs.DCSSDK_RESULT btNormalResult = sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL);

            Log.d(TAG,btNormalResult+ " results "+ btResult);
            sdkHandler.dcssdkSetDelegate(this);

            int notifications_mask = 0;
            // We would like to subscribe to all scanner available/not-available events
            notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value;

            // We would like to subscribe to all scanner connection events
            notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;


            // We would like to subscribe to all barcode events
            // subscribe to events set in notification mask
            sdkHandler.dcssdkSubsribeForEvents(notifications_mask);
        }
        if (sdkHandler != null)
        {
            ArrayList<DCSScannerInfo> availableScanners = new ArrayList<>();
            availableScanners  = (ArrayList<DCSScannerInfo>) sdkHandler.dcssdkGetAvailableScannersList();

            scannerList.clear();
            if (availableScanners != null)
            {
                for (DCSScannerInfo scanner : availableScanners)
                {

                    scannerList.add(scanner);
                }
            }
            else
                Log.d(TAG,"Available scanners null");

        }
        if (reader != null )
        {
            for (DCSScannerInfo device : scannerList)
            {
                if (device.getScannerName().contains(reader.getHostName()))
                {
                    try
                    {
                        sdkHandler.dcssdkEstablishCommunicationSession(device.getScannerID());
                        scannerID= device.getScannerID();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private synchronized void disconnectInternal() {
        Log.d(TAG, "Disconnect");
        try {
            if (reader != null) {
                if (eventHandler != null)
                    reader.Events.removeEventsListener(eventHandler);
                if (sdkHandler != null) {
                    sdkHandler.dcssdkTerminateCommunicationSession(scannerID);
                    scannerList = null;
                }
                try {
                    reader.disconnect();
                } catch (OperationFailureException ofe) {
                    Log.d(TAG, "OperationFailureException ofe=" + ofe.getVendorMessage());
                } catch (Throwable e1) {
                    Log.d(TAG, "Exception/Error e=" + e1.getMessage());
                }

                context.sendToast("Disconnecting reader");
                //reader = null;
            }
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void dispose() {
        disconnectInternal();
        try {
            if (toneGenerator != null) {
                toneGenerator.release();
                toneGenerator = null;
            }
            impinjExtensions = null;
            if (reader != null) {
                //Toast.makeText(getApplicationContext(), "Disconnecting reader", Toast.LENGTH_LONG).show();
                reader = null;
                readers.Dispose();
                readers = null;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void performInventory() {
        if(reader!=null && reader.isConnected()) {
            try {
                reader.Actions.Inventory.perform();
            } catch (Throwable t) {
                reportError(t);
                t.printStackTrace();
            }
        }else{
            context.sendToast("Reader not connected. Cannot start inventory.");
        }
    }

    public void stopInventory() {
        if(reader!=null && reader.isConnected()) {
            try {
                bAccessSequenceRunning = false;
                reader.Actions.Inventory.stop();
            } catch (Throwable t) {
                reportError(t);
                t.printStackTrace();
            }
        }else{
            context.sendToast("Reader not connected. Cannot stop inventory.");
        }
    }

    boolean addOperationSequenceReadMemoryBank(int iWordOffset, int iWordCount, MEMORY_BANK memoryBank) {
        boolean bResult = false;
        Log.d(TAG, "addOperationSequenceRead Memory Bank = " + memoryBank);
        //TagAccess.Sequence.Operation opTC53_Dummy = opSequence.new Operation();
        //this.m_pReader.Actions.TagAccess.OperationSequence.delete(opTC53_Dummy);
        TagAccess tagAccess = new TagAccess();
        TagAccess.Sequence sequence = tagAccess.new Sequence(tagAccess);
        TagAccess.Sequence.Operation operation = sequence.new Operation();
        operation.setAccessOperationCode(ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ);
        operation.ReadAccessParams.setMemoryBank(memoryBank);
        operation.ReadAccessParams.setAccessPassword(0);
        operation.ReadAccessParams.setCount(iWordCount);
        operation.ReadAccessParams.setOffset(iWordOffset);
        try {
            reader.Actions.TagAccess.OperationSequence.add(operation);
            bResult = true;
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        }
        return bResult;
    }

    private class RfidAccessReadSequenceStart extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                addOperationSequenceReadMemoryBank(0, 2, MEMORY_BANK.MEMORY_BANK_TID);
                addOperationSequenceReadMemoryBank(2, 6, MEMORY_BANK.MEMORY_BANK_EPC);
            } catch (Exception ex) {
                Log.d("ECRT", "UI Input Paramaters Exception=" + ex.getMessage());
            }

            try {
                d("ECRT", "Start Multi-Read...");
                reader.Actions.TagAccess.OperationSequence.performSequence();
            } catch (InvalidUsageException e) {
                e.printStackTrace();
            } catch (OperationFailureException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    synchronized public void accessSequence() {
        Log.d(TAG, "accessSequence");
        bAccessSequenceRunning = true;
        new RfidAccessReadSequenceStart().execute();
    }

    public String getVersionInfo() {
        return BuildConfig.VERSION_NAME;
    }

    private void reportError(Throwable t) {
        Log.e(TAG, "RFID Error Reported: " + t.getMessage(), t);
        // This is where you would integrate Firebase Crashlytics:
        // FirebaseCrashlytics.getInstance().recordException(t);
    }

    public void scanCode() {
        String in_xml = "<inArgs><scannerID>" + scannerID + "</scannerID></inArgs>";
        executorService.execute(() -> executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DEVICE_PULL_TRIGGER, in_xml, null, scannerID));
    }
    public boolean executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE opCode, String inXML, StringBuilder outXML, int scannerID) {
        if (sdkHandler != null) {
            if (outXML == null) {
                outXML = new StringBuilder();
            }
            DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(opCode, inXML, outXML, scannerID);
            Log.d(TAG, "execute command returned " + result.toString() );
            if(result== DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS)
                return true;
            else if(result==DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_FAILURE)
                return false;
        }
        return false;
    }

    // Reads the device serial number from the connected reader's capabilities (see sn.md).
    public String getSerialNumber() {
        try {
            if (reader != null && reader.isConnected() && reader.ReaderCapabilities != null) {
                String serial = reader.ReaderCapabilities.getSerialNumber();
                Log.d(TAG, "ReaderCapabilities serial='" + serial + "'");
                return serial;
            }
            Log.d(TAG, "Reader not connected; cannot read serial number");
        } catch (Exception e) {
            Log.e(TAG, "Failed to read serial number from reader capabilities", e);
        }
        return null;
    }
    // Read/Status Notify handler
    // Implement the RfidEventsLister class to receive event notifications
    public class EventHandler implements RfidEventsListener {
        // Read Event Notification
        public void eventReadNotify(RfidReadEvents e) {
            TagData[] myTags = reader.Actions.getReadTags(100);
            if (myTags != null) {
                context.showReadingProgress(false);
                for (int index = 0; index < myTags.length; index++) {
                    //  Log.d(TAG, "Tag ID " + myTags[index].getTagID());
                    Log.d(TAG, "Tag ID" + myTags[index].getTagID() +"RSSI value "+ myTags[index].getPeakRSSI());
                    Log.d(TAG, "RSSI value "+ myTags[index].getPeakRSSI());
                    /* To get the RSSI value*/   //   Log.d(TAG, "RSSI value "+ myTags[index].getPeakRSSI());

                    // Decode access sequence operation results (non-null opCode means it is an access op result)
                    if (myTags[index].getOpCode() != null) {
                        ACCESS_OPERATION_CODE opCode = myTags[index].getOpCode();
                        ACCESS_OPERATION_STATUS opStatus = myTags[index].getOpStatus();
                        MEMORY_BANK memoryBank = myTags[index].getMemoryBank();
                        String bankData = myTags[index].getMemoryBankData();
                        Log.d(TAG, "AccessSeq result -> tag=" + myTags[index].getTagID()
                                + " op=" + opCode
                                + " status=" + opStatus
                                + " bank=" + memoryBank
                                + " data=" + bankData);
                        if (opStatus == ACCESS_OPERATION_STATUS.ACCESS_SUCCESS
                                && opCode == ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ
                                && bankData != null && !bankData.isEmpty()) {
                            Log.d(TAG, "Read " + memoryBank + " data (hex): " + bankData);
                        } else if (opStatus != ACCESS_OPERATION_STATUS.ACCESS_SUCCESS) {
                            Log.d(TAG, "AccessSeq operation " + opCode + " failed: " + opStatus);
                        }
                    }
                }
                final TagData[] tags = myTags;
                if (bAccessSequenceRunning) {
                    context.runOnUiThread(() -> context.handleAccessSequenceData(tags));
                } else {
                    context.runOnUiThread(() -> context.handleTagdata(tags));
                }
            }
        }

        // Status Event Notification
        public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
            Log.d(TAG, "Status Notification: " + rfidStatusEvents.StatusEventData.getStatusEventType());
            if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
                if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
                    executorService.execute(() -> {
                        Log.d(TAG, "HANDHELD_TRIGGER_PRESSED");
                        context.runOnUiThread(() -> context.handleTriggerPress(true));
                    });
                }
                if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                    executorService.execute(() -> {
                        context.runOnUiThread(() -> context.handleTriggerPress(false));
                        Log.d(TAG, "HANDHELD_TRIGGER_RELEASED");
                    });
                }
            }
            if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.DISCONNECTION_EVENT) {
                executorService.execute(RFIDHandler.this::handleDisconnect);
            }
            if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.INVENTORY_START_EVENT){
                bIsReading = true;
                Log.d(TAG, "Reading INVENTORY_START_EVENT.....");
                context.showReadingProgress(true);
                context.setStartButtonReading(true);
            }
            if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.INVENTORY_STOP_EVENT){
                bIsReading = false;
                Log.d(TAG, "Stopped: INVENTORY_STOP_EVENT!!!!!");
                context.showReadingProgress(false);
                context.setStartButtonReading(false);
            }

        }
    }

    public boolean isbIsReading(){
        return bIsReading;
    }

    public boolean isAccessSequenceRunning(){
        return bAccessSequenceRunning;
    }

    interface ResponseHandlerInterface {
        void handleTagdata(TagData[] tagData);

        void handleAccessSequenceData(TagData[] tagData);

        void handleTriggerPress(boolean pressed);

        void barcodeData(String val);
        void sendToast(String val);
        //void handleStatusEvents(Events.StatusEventData eventData);
    }

    //M781 - E28011112222333344445555 passowrd - 00000000
    // E28011C1A5000062F792696D
    public String impinjTag ;

    public String password ;
    public String enableImpinjVisibility() {
        try {
            impinjExtensions.enableTagVisibility(password, (short) 1);
            //reader.enableImpinjVisibility("","");
        } catch (OperationFailureException e) {
            e.printStackTrace();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        }

        return  "Impinj Visibility Enabled";
    }

    public String disableImpinjVisibilty() {
        try {
            impinjExtensions.disableTagVisibility(password, (short) 1);
            //reader.enableImpinjVisibility("","");
        }  catch (OperationFailureException | InvalidUsageException | IllegalStateException e) {
            e.printStackTrace();
            Log.d(TAG, "Impinj Disable Visibility exception " + e.getMessage());
            return "Failed to disable Impinj Visibility: " + e.getMessage();
        }
        return  "Impinj Visibility disabled";
    }


    public String enableImpinjProtection() {
        try {
            TagData tagData = new TagData();
            impinjExtensions.enableTagProtection(impinjTag, password, tagData);

        } catch (OperationFailureException | InvalidUsageException e) {
            e.printStackTrace();
            return "Failed to enable Impinj Protection: " + e.getMessage();
        }
        return "Impinj Protection Enabled";
    }

    public String disableImpinjProtection() {
        try {
            TagData tagData = new TagData();
            impinjExtensions.disableTagProtection(impinjTag, password,tagData, (short) 1);
            // return " Impinj Protection Disabled";
        } catch (OperationFailureException e) {
            e.printStackTrace();
            return "Failed ";
        } catch (InvalidUsageException e) {
            e.printStackTrace();
            return "Failed ";
        }

        //String tagId = "1234ABCD00000000000025B1";

        return "Impinj Protection Disabled";
    }

    public String tagFocus(boolean tagFocus) {
        try {
            impinjExtensions.setTagFocus(tagFocus, (short) 1);
            return "Success";
        } catch (InvalidUsageException e) {
            e.printStackTrace();
            return "fail";

        } catch (OperationFailureException e) {
            e.printStackTrace();
            Log.d(TAG,"Tag focus exception "+e.getMessage()+ e.getVendorMessage());
            return "Fail";

        }catch (IllegalStateException e) {
            e.printStackTrace();
            Log.d(TAG,"Tag focus exception "+e.getMessage());
            return "Fail";
        }
    }

    //    D4 - S2
//11010100
//
//    F5 - S3
//11110101
//
//        90 - S0
//10010000
//    B1  - S3
//10110001
    public String tagQuiet(ENUM_TAGQUIET_MASK[] tagMask, TARGET target, STATE_AWARE_ACTION stateAwareAction) {
        try {
            impinjExtensions.setTagQuiet(tagMask,target,stateAwareAction,(short) 1);
        }catch (InvalidUsageException e) {
            e.printStackTrace();
            return "fail";

        } catch (OperationFailureException e) {
            e.printStackTrace();
            return "Fail";
        }

        return "Tagquiet Set to TID mask " ;
    }

    public String singulation(SESSION session, INVENTORY_STATE inventoryState){
        try {
            Antennas.SingulationControl s1_singulationControl = reader.Config.Antennas.getSingulationControl(1);
            s1_singulationControl.setSession(session);
            s1_singulationControl.Action.setInventoryState(inventoryState);
            s1_singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL);
            reader.Config.Antennas.setSingulationControl(1, s1_singulationControl);
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
            return e.getResults().toString() + " " + e.getVendorMessage();
        }
        return "Session set to "+ session.toString()+ " " + "Inventory State set to " + inventoryState.toString();
    }

    public  String setPrefilter(MEMORY_BANK mb, STATE_AWARE_ACTION stateAwareAction, TARGET target, String tagPattern, int offset, int length) {
        PreFilters preFilters = new PreFilters();
        PreFilters.PreFilter filter = preFilters.new PreFilter();
        filter.setAntennaID((short) 1);
        filter.setBitOffset(offset);
        filter.setTagPatternBitCount(length);
        filter.setTagPattern(tagPattern);
        filter.setMemoryBank(mb);
        filter.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE);
        filter.StateAwareAction.setTarget(target);
        filter.StateAwareAction.setStateAwareAction(stateAwareAction);

        try {
            reader.Actions.PreFilters.add(filter);
        } catch (InvalidUsageException e) {
            throw new RuntimeException(e);
        } catch (OperationFailureException e) {
            throw new RuntimeException(e);
        }
        return "Prefilter set true";
    }

//    public String removeTagQuiet() {
//        try {
//            impinjExtensions.removeTagQuiet();
//        } catch (InvalidUsageException e) {
//            e.printStackTrace();
//            return "fail";
//
//        } catch (OperationFailureException e) {
//            e.printStackTrace();
//            return "Fail";
//        }catch (IllegalStateException e) {
//            e.printStackTrace();
//            Log.d(TAG,"Tag quiet exception "+e.getMessage());
//            return "Fail";
//        }
//        return "Tagquiet removed";
//    }


}
