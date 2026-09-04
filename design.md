# Design Documentation: Zebra RFID Integration

This document outlines the implementation details for managing Zebra RFID readers using the RFID SDK.

## 1. Initialization (`InitSDK`)

Initialization involves creating the `Readers` object and configuring the transport layer (Bluetooth, USB, or Serial).

```java
// RFIDHandler.java
private void InitSDK() {
    if (readers == null) {
        // Run in background to avoid blocking UI
        executorService.execute(this::createInstance);
    } else {
        performConnect();
    }
}

private void createInstance() {
    try {
        // Initialize readers with Bluetooth transport
        readers = new Readers(context, ENUM_TRANSPORT.BLUETOOTH);
        availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
        
        // Fallback to other transports if needed
        if (availableRFIDReaderList.isEmpty()) {
            readers.setTransport(ENUM_TRANSPORT.SERVICE_USB);
            availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
        }
        
        if (!availableRFIDReaderList.isEmpty()) {
            connectReader();
        }
    } catch (Exception e) {
        reportError(e);
    }
}
```

## 2. Reader Discovery & Selection

The SDK provides a list of `ReaderDevice` objects. We can select one based on count or name.

```java
private synchronized void GetAvailableReader() {
    if (readers != null) {
        // Attach listener to receive appeared/disappeared events
        readers.attach(this); 
        ArrayList<ReaderDevice> availableReaders = readers.GetAvailableRFIDReaderList();
        
        if (availableReaders != null && !availableReaders.isEmpty()) {
            if (availableReaders.size() == 1) {
                // Select the only available reader
                readerDevice = availableReaders.get(0);
                reader = readerDevice.getRFIDReader();
            } else {
                // Select by specific name prefix
                for (ReaderDevice device : availableReaders) {
                    if (device.getName().startsWith("RFD40")) {
                        readerDevice = device;
                        reader = readerDevice.getRFIDReader();
                    }
                }
            }
        }
    }
}
```

## 3. Connection & Disconnection

Connecting is a blocking operation and should be performed in a background thread.

### Connect
```java
private synchronized String handleConnect() {
    if (reader != null && !reader.isConnected()) {
        try {
            reader.connect(); // Core SDK call
            if (reader.isConnected()) {
                ConfigureReader(); // Set antenna, power, etc.
                return "Connected: " + reader.getHostName();
            }
        } catch (OperationFailureException e) {
            return "Connection failed: " + e.getVendorMessage();
        }
    }
    return "";
}
```

### Disconnect
```java
private synchronized void disconnectInternal() {
    if (reader != null && reader.isConnected()) {
        try {
            reader.disconnect(); // Core SDK call
        } catch (Exception e) {
            Log.e(TAG, "Disconnect error", e);
        }
    }
}
```

## 4. Lifecycle Events (Appear/Disappear)

The `Readers.RFIDReaderEventHandler` interface allows the app to react when a reader physically becomes available or unavailable.

### Appear -> Auto-Reconnect
When a paired reader enters Bluetooth range, `RFIDReaderAppeared` is triggered.

```java
@Override
public void RFIDReaderAppeared(ReaderDevice readerDevice) {
    Log.d(TAG, "Reader appeared: " + readerDevice.getName());
    // Automatically attempt connection
    connectReader(); 
}
```

### Disappear -> Clean Disconnect
When a reader goes out of range or is powered off, `RFIDReaderDisappeared` is triggered.

```java
@Override
public void RFIDReaderDisappeared(ReaderDevice readerDevice) {
    Log.d(TAG, "Reader disappeared: " + readerDevice.getName());
    // Clean up local resources
    performDisconnect();
}
```

## 5. UI Updates & Feedback

- **Status Messages**: Aggregated and displayed in a modern Material Dialog in `MainActivity`.
- **Audible Feedback**: `ToneGenerator` plays a confirmation beep on connection and a congestion beep on disconnection.
