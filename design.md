# Design Documentation: Zebra RFID Integration

This document outlines the implementation details for managing Zebra RFID readers using the RFID SDK.

## 1. Initialization (`InitSDK`)

Initialization creates the `Readers` object and probes the available transports.
If the SDK was previously disposed (`readers == null`) it shows progress, sets a
transient status (`Connecting...` on first launch, `Reconnecting...` afterwards)
and builds a fresh instance on a background thread; otherwise it just reconnects.

```java
// RFIDHandler.java
private void InitSDK() {
    if (readers == null) {
        context.showProgress(true);
        context.setRfidStatus(hasConnectedBefore ? "Reconnecting..." : "Connecting...");
        executorService.execute(this::createInstance); // avoid blocking the UI thread
    } else {
        performConnect();
    }
}
```

`createInstance()` walks a chain of transports, stopping at the first that
returns a non-empty reader list. On the TC701 the RFD40 is reached over a
**serial transport** (`SERVICE_SERIAL`), not the device USB port.

```java
private void createInstance() {
    // Probe transports in order, keep the first that finds a reader:
    // BLUETOOTH → SERVICE_SERIAL → SERVICE_USB → QC_SERIAL → RE_SERIAL
    readers = new Readers(context, ENUM_TRANSPORT.BLUETOOTH);
    availableRFIDReaderList = readers.GetAvailableRFIDReaderList();

    if (availableRFIDReaderList.isEmpty()) {
        readers.setTransport(ENUM_TRANSPORT.SERVICE_SERIAL);
        availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
    }
    // ... SERVICE_USB, QC_SERIAL, RE_SERIAL fallbacks follow the same pattern ...

    context.runOnUiThread(() -> {
        if (availableRFIDReaderList.isEmpty()) {
            readers = null;              // nothing found → reset to disconnected UI
            context.setConnectionButtons(false);
        } else {
            connectReader();
        }
    });
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
                // Select by configured name prefix (readerName field)
                for (ReaderDevice device : availableReaders) {
                    if (device.getName().startsWith(readerName)) {
                        readerDevice = device;
                        reader = readerDevice.getRFIDReader();
                    }
                }
            }
            if (impinjExtensions == null)
                impinjExtensions = new ImpinjExtensions(reader);
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
    if (reader != null) {
        // Stop receiving events before tearing down the link
        if (eventHandler != null)
            reader.Events.removeEventsListener(eventHandler);
        try {
            reader.disconnect(); // Core SDK call
        } catch (OperationFailureException ofe) {
            Log.d(TAG, "OperationFailureException " + ofe.getVendorMessage());
        } catch (Throwable e) {
            Log.d(TAG, "Disconnect error " + e.getMessage());
        }
    }
}
```

Every disconnect path (lifecycle, user-initiated, or reader-initiated) funnels
through `handleDisconnect()`, which calls `disconnectInternal()`, plays the
"phone drop" tone, and resets the UI to the disconnected state.

## 4. Lifecycle Events (Appear/Disappear)

The `Readers.RFIDReaderEventHandler` interface lets the app react when a reader
physically becomes available or unavailable. Both callbacks are guarded so they
only act on the reader this app owns.

### Appear -> Auto-Reconnect
When a paired reader appears on a transport, `RFIDReaderAppeared` reconnects. If
the *same* reader is re-announced while we still believe we are connected (a stale
session, e.g. after a USB role change), it drops that session first.

```java
@Override
public void RFIDReaderAppeared(ReaderDevice readerDevice) {
    Log.d(TAG, "RFIDReaderAppeared " + readerDevice.getName());
    context.sendToast("Reader appeared: " + readerDevice.getName());
    if (reader != null && reader.isConnected()) {
        context.sendToast("Reader in USB host mode");
        if (readerDevice.getName().equals(reader.getHostName()))
            performDisconnect();     // drop the stale session before reconnecting
    }
    connectReader();
}
```

### Disappear -> Guarded Disconnect
When a reader goes out of range, is powered off, or its link is preempted,
`RFIDReaderDisappeared` tears down — but only for the reader we are connected to.
The `reader != null` check is essential: after a prior teardown `reader` can be
`null`, so dereferencing `reader.getHostName()` unconditionally would throw an NPE.

```java
@Override
public void RFIDReaderDisappeared(ReaderDevice readerDevice) {
    Log.d(TAG, "RFIDReaderDisappeared " + readerDevice.getName());
    context.sendToast("Reader disappeared: " + readerDevice.getName());
    // Only tear down if the reader that vanished is the one we are connected to.
    if (reader != null && readerDevice.getName().equals(reader.getHostName()))
        performDisconnect();
}
```

### USB cable plug-in / unplug (TC701 + RFD40)
Because the RFD40 link runs over `SERVICE_SERIAL`, plugging a USB cable into the
TC701 flips the USB role and **preempts the reader link** → `RFIDReaderDisappeared`
→ guarded `performDisconnect()`. Unplugging restores the link →
`RFIDReaderAppeared` → `connectReader()` reconnects automatically. See
[lifecycle.md](./lifecycle.md) section 7 for the full sequence.

## 5. UI Updates & Feedback

- **Status Messages**: Aggregated and displayed in a modern Material Dialog in `MainActivity`.
- **Audible Feedback**: `ToneGenerator` plays a confirmation beep on connection and a descending two-beep "phone drop" tone on disconnection.
- **Toast wording**: Reader events use a consistent, user-facing style (`Reader appeared: <name>`, `Reader disappeared: <name>`, `Reader in USB host mode`) rather than raw SDK callback names.
