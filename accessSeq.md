# Access Sequence & Stop Guide (TC501)

How to run an RFID **access sequence** (multi-operation read) and how to **stop** it, using
`RFIDHandler`.

> ⚠️ **TC501 limitation:** The TC501 reader supports a **maximum of 4 access sequence
> operations** in a single `OperationSequence`. Adding a 5th operation will fail. Keep the
> total number of `add(...)` calls per sequence to **4 or fewer**.

---

## 1. Adding an operation to the sequence

Each operation is one read (or write/lock/etc.) against a tag memory bank. This helper builds
a **read** operation and adds it to the reader's `OperationSequence`.

```java
boolean addOperationSequenceReadMemoryBank(int iWordOffset, int iWordCount, MEMORY_BANK memoryBank) {
    boolean bResult = false;
    Log.d(TAG, "addOperationSequenceRead Memory Bank = " + memoryBank);

    TagAccess tagAccess = new TagAccess();
    TagAccess.Sequence sequence = tagAccess.new Sequence(tagAccess);
    TagAccess.Sequence.Operation operation = sequence.new Operation();

    operation.setAccessOperationCode(ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ);
    operation.ReadAccessParams.setMemoryBank(memoryBank);
    operation.ReadAccessParams.setAccessPassword(0);
    operation.ReadAccessParams.setCount(iWordCount);   // number of 16-bit words to read
    operation.ReadAccessParams.setOffset(iWordOffset); // starting word offset in the bank

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
```

**Parameters**

| Parameter     | Meaning                                             |
| ------------- | --------------------------------------------------- |
| `iWordOffset` | Starting word offset in the memory bank             |
| `iWordCount`  | Number of 16-bit words to read                      |
| `memoryBank`  | `MEMORY_BANK_EPC`, `MEMORY_BANK_TID`, `MEMORY_BANK_USER`, `MEMORY_BANK_RESERVED` |

---

## 2. Starting the access sequence

The sequence is built and performed on a background thread. The current implementation adds
**2 operations** (TID + EPC), well within the 4-operation TC501 limit.

```java
private class RfidAccessReadSequenceStart extends AsyncTask<Void, Void, Void> {
    @Override
    protected Void doInBackground(Void... params) {
        try {
            // Operation 1 of max 4: read 2 words from TID starting at offset 0
            addOperationSequenceReadMemoryBank(0, 2, MEMORY_BANK.MEMORY_BANK_TID);
            // Operation 2 of max 4: read 6 words from EPC starting at offset 2
            addOperationSequenceReadMemoryBank(2, 6, MEMORY_BANK.MEMORY_BANK_EPC);
            // NOTE: You may add at most 2 more operations here (4 total on TC501).
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
    new RfidAccessReadSequenceStart().execute();
}
```

Call it from the UI (see `MainActivity.onOptionsItemSelected`):

```java
if (id == R.id.access_sequence_start) {
    Toast.makeText(this, "Start Access Sequence", Toast.LENGTH_SHORT).show();
    rfidHandler.accessSequence();
    return true;
}
```

### TC501: 4-operation example

```java
// ✅ Valid on TC501 — exactly 4 operations
addOperationSequenceReadMemoryBank(0, 2, MEMORY_BANK.MEMORY_BANK_TID);      // 1
addOperationSequenceReadMemoryBank(2, 6, MEMORY_BANK.MEMORY_BANK_EPC);      // 2
addOperationSequenceReadMemoryBank(0, 4, MEMORY_BANK.MEMORY_BANK_USER);     // 3
addOperationSequenceReadMemoryBank(0, 2, MEMORY_BANK.MEMORY_BANK_RESERVED); // 4

// ❌ A 5th add(...) will fail on TC501 — do NOT do this
// addOperationSequenceReadMemoryBank(4, 2, MEMORY_BANK.MEMORY_BANK_USER); // 5 -> fails
```

---

## 3. Stopping the sequence / inventory

Stopping an active access sequence uses the same inventory stop action. `stopInventory()`
halts the reader and triggers an `INVENTORY_STOP_EVENT`.

```java
public void stopInventory() {
    try {
        reader.Actions.Inventory.stop();
    } catch (Throwable t) {
        reportError(t);
        t.printStackTrace();
    }
}
```

Call it from the UI (see `MainActivity.onOptionsItemSelected`):

```java
if (id == R.id.access_sequence_stop) {
    Toast.makeText(this, "Stop Access", Toast.LENGTH_SHORT).show();
    rfidHandler.stopInventory();
    return true;
}
```

`stopInventory()` is also invoked automatically on `onPause()` and on trigger release, so the
reader is not left running.

---

## 4. Decoding the access sequence results

Sequence results arrive through the tag read event listener (`eventReadNotify`), the same path
as normal inventory reads. For an **access sequence** read, each `TagData` carries extra
fields that a plain inventory read does not:

| `TagData` accessor      | Meaning                                                            |
| ----------------------- | ----------------------------------------------------------------- |
| `getOpCode()`           | The access operation (`ACCESS_OPERATION_READ`, …). `null` for a plain inventory read. |
| `getOpStatus()`         | Result of that operation (`ACCESS_SUCCESS`, or a failure code).    |
| `getMemoryBank()`       | Which bank the data came from (`MEMORY_BANK_TID`, `MEMORY_BANK_EPC`, …). |
| `getMemoryBankData()`   | The bytes read back, as a **hex string** (each 16-bit word = 4 hex chars). |

Because the sequence in this example reads **TID (op 1)** and **EPC (op 2)**, a single tag
produces **one `TagData` per operation** — so you branch on `getMemoryBank()` to route the
data.

### 4.1 Current implementation in `eventReadNotify`

```java
public void eventReadNotify(RfidReadEvents e) {
    TagData[] myTags = reader.Actions.getReadTags(100);
    if (myTags != null) {
        context.showReadingProgress(false);
        for (int index = 0; index < myTags.length; index++) {
            Log.d(TAG, "Tag ID" + myTags[index].getTagID() + "RSSI value " + myTags[index].getPeakRSSI());
            Log.d(TAG, "RSSI value " + myTags[index].getPeakRSSI());

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
        context.runOnUiThread(() -> context.handleTagdata(myTags));
    }
}
```

Required imports:

```java
import com.zebra.rfid.api3.ACCESS_OPERATION_CODE;
import com.zebra.rfid.api3.ACCESS_OPERATION_STATUS;
import com.zebra.rfid.api3.MEMORY_BANK;
```

### 4.2 Routing TID vs EPC data

The example adds two read operations — TID (`offset 0, 2 words`) and EPC (`offset 2, 6 words`).
Branch on `getMemoryBank()` to handle each bank's hex payload separately:

```java
if (myTags[index].getOpCode() == ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ
        && myTags[index].getOpStatus() == ACCESS_OPERATION_STATUS.ACCESS_SUCCESS) {

    String tagId   = myTags[index].getTagID();
    String hexData = myTags[index].getMemoryBankData(); // e.g. "E2801190" (4 hex chars per word)

    switch (myTags[index].getMemoryBank()) {
        case MEMORY_BANK_TID:
            // 2 words = 8 hex chars, e.g. "E2801190"
            Log.d(TAG, "Tag " + tagId + " TID = " + hexData);
            break;
        case MEMORY_BANK_EPC:
            // 6 words = 24 hex chars, e.g. "3000E2801190A5000062F792"
            Log.d(TAG, "Tag " + tagId + " EPC = " + hexData);
            break;
        default:
            Log.d(TAG, "Tag " + tagId + " " + myTags[index].getMemoryBank() + " = " + hexData);
            break;
    }
}
```

### 4.3 Word count ↔ hex length

`getMemoryBankData()` returns a hex string; each 16-bit word is **4 hex characters**. This
matches the word counts requested in `addOperationSequenceReadMemoryBank(...)`:

| Bank | `iWordCount` (from example) | Expected hex length | Example value              |
| ---- | --------------------------- | ------------------- | -------------------------- |
| TID  | 2 words                     | 8 chars             | `E2801190`                 |
| EPC  | 6 words                     | 24 chars            | `3000E2801190A5000062F792` |

Convert a hex string to bytes if you need the raw values:

```java
private static byte[] hexToBytes(String hex) {
    if (hex == null) return new byte[0];
    int len = hex.length();
    byte[] out = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
        out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
    }
    return out;
}
```

> **Tip:** Always check `getOpStatus() == ACCESS_SUCCESS` before trusting `getMemoryBankData()`.
> On failure (locked bank, wrong password, tag out of field) the data is empty or stale, and
> `getOpStatus()` tells you why.

---

## Quick reference

| Action                     | Method                                                        |
| -------------------------- | ------------------------------------------------------------- |
| Add read operation         | `addOperationSequenceReadMemoryBank(offset, count, bank)`     |
| Start sequence             | `accessSequence()` → `OperationSequence.performSequence()`    |
| Stop sequence / inventory  | `stopInventory()` → `Actions.Inventory.stop()`                |
| Decode results             | `eventReadNotify(...)` → `getOpCode()` / `getOpStatus()` / `getMemoryBank()` / `getMemoryBankData()` |

> **Remember:** On TC501, an `OperationSequence` may contain **at most 4 operations**.
