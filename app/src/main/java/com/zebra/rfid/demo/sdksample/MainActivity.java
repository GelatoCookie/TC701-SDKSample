package com.zebra.rfid.demo.sdksample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.button.MaterialButton;
import com.zebra.rfid.api3.ENUM_TAGQUIET_MASK;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.MEMORY_BANK;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.STATE_AWARE_ACTION;
import com.zebra.rfid.api3.TARGET;
import com.zebra.rfid.api3.TagData;
import com.zebra.scannercontrol.SDKHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Sample app to connect to the reader,to do inventory and basic barcode scan
 * We can also set antenna settings and singulation control
 * */

public class MainActivity extends AppCompatActivity implements RFIDHandler.ResponseHandlerInterface {

    public TextView statusTextViewRFID = null;
    private ProgressBar progressBar;
    private View readingOverlay;
    public TextView scanResult;
    private MaterialButton btnInventoryStart;
    private ColorStateList defaultStartButtonTint;
    private MaterialButton btnConnect;
    private MaterialButton btnDisconnect;
    private ColorStateList defaultConnectTint;
    private ColorStateList defaultDisconnectTint;
    private RecyclerView tagRecyclerView;
    private TagAdapter tagAdapter;
    private TextView listHeaderPrimary;
    private TextView listHeaderSecondary;
    public EditText tagIdEditText, passwordEditText;
    public TARGET target;
    public STATE_AWARE_ACTION stateAwareAction;
    public byte byteval;

    RFIDHandler rfidHandler;
    final static String TAG = "RFID_SAMPLE";
    public static SDKHandler sdkHandler;
    private boolean rfidPermissionReady = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean bluetoothScan = result.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false);
                Boolean bluetoothConnect = result.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false);
                if (Boolean.TRUE.equals(bluetoothConnect)) {
                    rfidPermissionReady = true;
                    rfidHandler.onForeground(this);
                } else {
                    Toast.makeText(this, R.string.permission_not_granted, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Log.d(TAG, "App Start Up on " + Build.MODEL);

        // RFID Handler
        statusTextViewRFID = (TextView) findViewById(R.id.textViewStatusrfid);
        progressBar = findViewById(R.id.progressBar);
        readingOverlay = findViewById(R.id.readingOverlay);
        scanResult = (TextView) findViewById(R.id.scanResult);
        btnInventoryStart = findViewById(R.id.btnInventoryStart);
        defaultStartButtonTint = btnInventoryStart.getBackgroundTintList();
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        defaultConnectTint = btnConnect.getBackgroundTintList();
        defaultDisconnectTint = btnDisconnect.getBackgroundTintList();
        setConnectionButtons(false);
        tagAdapter = new TagAdapter();
        tagRecyclerView = findViewById(R.id.tagRecyclerView);
        tagRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tagRecyclerView.setAdapter(tagAdapter);
        listHeaderPrimary = findViewById(R.id.listHeaderPrimary);
        listHeaderSecondary = findViewById(R.id.listHeaderSecondary);
        tagIdEditText= findViewById(R.id.tagId);
        passwordEditText  = findViewById(R.id.password);

        rfidHandler = new RFIDHandler();
        //rfidHandler.onCreate(this);
        rfidHandler.impinjTag = tagIdEditText.getText().toString();
        rfidHandler.password = passwordEditText.getText().toString();
        tagIdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                rfidHandler.impinjTag = s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                rfidHandler.password = s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        //Scanner Initializations
        //Handling Runtime BT permissions for Android 12 and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT});
            } else {
                rfidPermissionReady = true;
            }
        } else {
            rfidPermissionReady = true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.access_sequence_start) {
            Toast.makeText(this,"Start Access Sequence",Toast.LENGTH_SHORT).show();
            tagAdapter.clear();
            setAccessSequenceHeader(true);
            rfidHandler.accessSequence();
            return true;
        }
        if (id == R.id.access_sequence_stop) {
            Toast.makeText(this,"Stop Access",Toast.LENGTH_SHORT).show();
            rfidHandler.stopInventory();
            setAccessSequenceHeader(false);
            return true;
        }
        if (id == R.id.get_oem_info) {
            String serial = rfidHandler.getSerialNumber();
            String message = (serial == null || serial.trim().isEmpty())
                    ? getString(R.string.oem_serial_unavailable)
                    : getString(R.string.oem_serial_label, serial);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.oem_serial_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        }

        if (id == R.id.enableImpinjVisiblity) {
            String result = rfidHandler.enableImpinjVisibility();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.enableImpinjProtect) {
            String result = rfidHandler.enableImpinjProtection();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.disableImpinjVisiblity) {
            String result = rfidHandler.disableImpinjVisibilty();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.disableImpinjProtect) {
            String result = rfidHandler.disableImpinjProtection();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.tagFocus) {
            showTagFocusDialog();
            return true;
        }
        if (id == R.id.tagQuiet) {
            String result = showCustomTagQuietDialog();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.singulation) {
            String result = showCustomSingulation();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.prefilter) {
            showPrefilterDialog();
            return true;
        }


        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        if (rfidPermissionReady) {
            rfidHandler.onForeground(this);
        }
    }

    @Override
    protected void onPause() {
        if(rfidHandler.isbIsReading())
            rfidHandler.stopInventory();
        super.onPause();
        Log.d(TAG, "onPause");

        //rfidHandler.onPause();
    }

    @Override
    protected void onStop() {
        rfidHandler.onBackground();
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        Log.d(TAG, "onPostResume");
        clearStatusMessages();
        updateTitleWithSdkVersion(rfidHandler.getVersionInfo());
    }

    public void updateTitleWithSdkVersion(String version) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(Build.MODEL + " (SDK: " + version + ")");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        rfidHandler.onDestroy();
    }

    private final StringBuilder statusBuffer = new StringBuilder();
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private Runnable showStatusRunnable;

    public void UpdateUI_statusTextViewRFID(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "statusTextViewRFID " + status);

                if (status != null && !status.isEmpty()) {
                    statusTextViewRFID.setText(status);
                    if (statusBuffer.indexOf(status) == -1) {
                        statusBuffer.append(status).append("\n");
                    }
                    // Hide progress bar when we get a status update
                    showProgress(false);

                    if (showStatusRunnable != null) {
                        statusHandler.removeCallbacks(showStatusRunnable);
                    }
                    showStatusRunnable = MainActivity.this::showStatusDialog;
                    statusHandler.postDelayed(showStatusRunnable, 500);
                }
            }
        });
    }

    public void clearStatusMessages() {
        statusBuffer.setLength(0);
    }

    // Sets the RFID status text directly (e.g. transient "Connecting..."/"Reconnecting..."),
    // without buffering it or triggering the status dialog.
    public void setRfidStatus(final String status) {
        runOnUiThread(() -> {
            if (statusTextViewRFID != null) {
                statusTextViewRFID.setText(status);
            }
        });
    }

    public void showStatusDialog() {
        runOnUiThread(() -> {
            if (statusBuffer.length() > 0) {
                AlertDialog dialog = new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(R.string.dialog_title_rfid_status)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setMessage(statusBuffer.toString().trim())
                        .setPositiveButton(android.R.string.ok, (d, which) -> clearStatusMessages())
                        .setOnDismissListener(d -> clearStatusMessages())
                        .show();

                // Auto-dismiss after 2 seconds
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        if (dialog.isShowing() && !isFinishing()) {
                            dialog.dismiss();
                        }
                    } catch (Exception ignored) {}
                }, 2000);
            }
        });
    }

    public void showProgress(boolean show) {
        runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    public void showReadingProgress(boolean show) {
        runOnUiThread(() -> {
            if (readingOverlay != null) {
                readingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    // Disables and greys out the START button while reading; restores it when stopped.
    public void setStartButtonReading(final boolean reading) {
        runOnUiThread(() -> applyButtonState(btnInventoryStart, defaultStartButtonTint, !reading));
    }

    // Enables the active button and greys out the inactive one based on connection state.
    public void setConnectionButtons(final boolean connected) {
        runOnUiThread(() -> {
            applyButtonState(btnConnect, defaultConnectTint, !connected);
            applyButtonState(btnDisconnect, defaultDisconnectTint, connected);
        });
    }

    private void applyButtonState(MaterialButton button, ColorStateList defaultTint, boolean enabled) {
        if (button == null) {
            return;
        }
        button.setEnabled(enabled);
        button.setBackgroundTintList(enabled ? defaultTint
                : ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.darker_gray)));
    }

    public void StartInventory(View view)
    {
        tagAdapter.clear();
        setAccessSequenceHeader(false);
        rfidHandler.performInventory();
        //   rfidHandler.MultiTag();
    }

    public void connectReader(View view) {
        clearStatusMessages();
        rfidHandler.performConnect();
    }

    public void disconnectReader(View view) {
        clearStatusMessages();
        rfidHandler.performDisconnect();
    }
    public void scanCode(View view){
        rfidHandler.scanCode();

    }



    public void testFunction(View view){
        //rfidHandler.testFunction();
    }

    public void StopInventory(View view){
        rfidHandler.stopInventory();
    }

    @Override
    public void handleTagdata(TagData[] tagData) {
        runOnUiThread(() -> {
            for (TagData tag : tagData) {
                tagAdapter.addTag(tag.getTagID(), String.valueOf(tag.getPeakRSSI()));
            }
            tagRecyclerView.scrollToPosition(tagAdapter.getItemCount() - 1);
        });
    }

    private void setAccessSequenceHeader(boolean accessMode) {
        if (listHeaderPrimary != null)
            listHeaderPrimary.setText(accessMode ? R.string.label_access_seq : R.string.label_tag_id);
        if (listHeaderSecondary != null)
            listHeaderSecondary.setText(accessMode ? R.string.label_access_status : R.string.label_rssi);
    }

    @Override
    public void handleAccessSequenceData(TagData[] tagData) {
        runOnUiThread(() -> {
            for (TagData tag : tagData) {
                if (tag.getOpCode() == null)
                    continue;
                String bank = tag.getMemoryBank() != null
                        ? tag.getMemoryBank().toString().replace("MEMORY_BANK_", "") : "";
                String data = tag.getMemoryBankData();
                String primary = bank + " " + (data == null || data.isEmpty() ? "-" : data);
                String status = tag.getOpStatus() != null
                        ? tag.getOpStatus().toString().replace("ACCESS_", "") : "";
                tagAdapter.addAccessResult(primary, status);
            }
            tagRecyclerView.scrollToPosition(tagAdapter.getItemCount() - 1);
        });
    }

    @Override
    public void handleTriggerPress(boolean pressed) {
        if (pressed) {
            runOnUiThread(() -> tagAdapter.clear());
            rfidHandler.performInventory();
        } else
            rfidHandler.stopInventory();
    }

    @Override
    public void barcodeData(String val) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scanResult.setText("Scan Result : "+val);
            }
        });

    }

    @Override
    public void sendToast(String val) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Snackbar.make(findViewById(android.R.id.content), val, Snackbar.LENGTH_LONG).show();
            }
        });
    }


    private String showCustomTagQuietDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tag_quiet, null);

        Spinner spinner1 = dialogView.findViewById(R.id.Target);
        Spinner spinner2 = dialogView.findViewById(R.id.State);

        Spinner mask1 = dialogView.findViewById(R.id.mask1);
        Spinner mask2 = dialogView.findViewById(R.id.mask2);
        Spinner mask3 = dialogView.findViewById(R.id.mask3);

        ArrayAdapter<String> maskAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.tagmask_enum));
        mask1.setAdapter(maskAdapter);
        mask2.setAdapter(maskAdapter);
        mask3.setAdapter(maskAdapter);

        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.pre_filter_target_options)
        );

        spinner1.setAdapter(targetAdapter);

        ArrayAdapter<String> state_aware_action_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.pre_filter_action_array)
        );
        spinner2.setAdapter(state_aware_action_adapter);

        new AlertDialog.Builder(this)
                .setTitle("Input Values")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {


                    String spinner1Value = spinner1.getSelectedItem().toString();
                    String spinner2Value = spinner2.getSelectedItem().toString();

                    target = TARGET.getTarget(spinner1.getSelectedItemPosition());
                    stateAwareAction = getStateAwareActionFromString(spinner2Value);

                    List<ENUM_TAGQUIET_MASK> maskList = new ArrayList<>();
                    try {
                        ENUM_TAGQUIET_MASK m1 = ENUM_TAGQUIET_MASK.fromString(mask1.getSelectedItem().toString());
                        if (m1 != null) maskList.add(m1);
                    } catch (IllegalArgumentException ignored) {}

                    try {
                        ENUM_TAGQUIET_MASK m2 = ENUM_TAGQUIET_MASK.fromString(mask2.getSelectedItem().toString());
                        if (m2 != null) maskList.add(m2);
                    } catch (IllegalArgumentException ignored) {}

                    try {
                        ENUM_TAGQUIET_MASK m3 = ENUM_TAGQUIET_MASK.fromString(mask3.getSelectedItem().toString());
                        if (m3 != null) maskList.add(m3);
                    } catch (IllegalArgumentException ignored) {}

                    ENUM_TAGQUIET_MASK[] masks = maskList.toArray(new ENUM_TAGQUIET_MASK[0]);
                    String result = rfidHandler.tagQuiet(masks, target, stateAwareAction);
                    Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        return "";
    }

    private STATE_AWARE_ACTION getStateAwareActionFromString(String strAction) {
        STATE_AWARE_ACTION action = null;
        if (strAction.equalsIgnoreCase("INV A NOT INV B OR ASRT SL NOT DSRT SL"))
            action = STATE_AWARE_ACTION.STATE_AWARE_ACTION_INV_A_NOT_INV_B;
        if (strAction.equalsIgnoreCase("INV A OR ASRT SL"))
            action = STATE_AWARE_ACTION.STATE_AWARE_ACTION_INV_A;
        if (strAction.equalsIgnoreCase("NOT INV B OR NOT DSRT SL"))
            action = STATE_AWARE_ACTION.STATE_AWARE_ACTION_NOT_INV_B;
        if (strAction.equalsIgnoreCase("INV A2BB2A NOT INV A OR NEG SL NOT ASRT SL"))
            action = STATE_AWARE_ACTION.STATE_AWARE_ACTION_INV_A2BB2A_NOT_INV_A;
        if (strAction.equalsIgnoreCase("INV B NOT INV A OR DSRT SL NOT ASRT SL"))
            action = STATE_AWARE_ACTION.STATE_AWARE_ACTION_INV_B_NOT_INV_A;
        if (strAction.equalsIgnoreCase("INV B OR DSRT SL"))
            action = STATE_AWARE_ACTION.STATE_AWARE_ACTION_INV_B;
        if (strAction.equalsIgnoreCase("NOT INV A OR NOT ASRT SL"))
            action = STATE_AWARE_ACTION.STATE_AWARE_ACTION_NOT_INV_A;
        if (strAction.equalsIgnoreCase("NOT INV A2BB2A OR NOT NEG SL"))
            action = STATE_AWARE_ACTION.STATE_AWARE_ACTION_NOT_INV_A2BB2A;
        return action;
    }

    private String showCustomSingulation(){
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_set_session, null);
        Spinner inv_state_spinner = dialogView.findViewById(R.id.InventoryState);
        Spinner sessionSpinner = dialogView.findViewById(R.id.session);

        ArrayAdapter<String> inventoryStateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.inventory_state_array)
        );

        inv_state_spinner.setAdapter(inventoryStateAdapter);

        ArrayAdapter<String> state_aware_action_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.session_array)
        );
        sessionSpinner.setAdapter(state_aware_action_adapter);

        new AlertDialog.Builder(this)
                .setTitle("Input Values")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {


                    String spinner1Value = inv_state_spinner.getSelectedItem().toString();
                    String spinner2Value = sessionSpinner.getSelectedItem().toString();

                    SESSION session = SESSION.GetSession(sessionSpinner.getSelectedItemPosition());
                    INVENTORY_STATE inventoryState = INVENTORY_STATE.GetInventoryState(inv_state_spinner.getSelectedItemPosition());

                    String result = rfidHandler.singulation(session, inventoryState);
                    Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        return "";
    }

    private void showPrefilterDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_prefilter, null);
        Spinner targetSpinner = dialogView.findViewById(R.id.prefilter_target_spinner);
        Spinner actionSpinner = dialogView.findViewById(R.id.prefilter_action_spinner);
        Spinner membankSpinner = dialogView.findViewById(R.id.prefilter_membank_spinner);
        EditText pointerEditText = dialogView.findViewById(R.id.pointer_edittext);
        EditText maskEditText = dialogView.findViewById(R.id.mask_edittext);
        EditText lengthEditText = dialogView.findViewById(R.id.length_edittext);
        Button saveButton = dialogView.findViewById(R.id.save_button);

        // Set up adapters for the spinners
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.pre_filter_target_options));
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetSpinner.setAdapter(targetAdapter);

        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.pre_filter_action_array));
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(actionAdapter);

        ArrayAdapter<String> membankAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.memory_bank_array));
        membankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        membankSpinner.setAdapter(membankAdapter);

        // Build the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle("Configure Settings");
        final AlertDialog dialog = builder.create();

        saveButton.setOnClickListener(v -> {
            // Handle save button click
            String pointerValue = pointerEditText.getText().toString();

            MEMORY_BANK mb = MEMORY_BANK.GetMemoryBankValue(membankSpinner.getSelectedItem().toString());
            TARGET target1 = TARGET.getTarget(targetSpinner.getSelectedItemPosition());
            STATE_AWARE_ACTION stateAwareAction1 = getStateAwareActionFromString(actionSpinner.getSelectedItem().toString());

            String mask = maskEditText.getText().toString();
            int pointer = pointerValue.isEmpty() ? 0 : Integer.parseInt(pointerValue);
            String lengthStr = lengthEditText.getText().toString();
            int length = lengthStr.isEmpty() ? 0 : Integer.parseInt(lengthStr);

            rfidHandler.setPrefilter(mb, stateAwareAction1, target1, mask, pointer, length);
            dialog.dismiss();
        });

        // Display the dialog
        dialog.show();
    }

    private void showTagFocusDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tagfocus, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.radioGroup_tagfocus);
        RadioButton radioYes = dialogView.findViewById(R.id.radio_tagfocus_yes);
        RadioButton radioNo = dialogView.findViewById(R.id.radio_tagfocus_no);

        // Optionally, set default selection (e.g., Yes)
        radioYes.setChecked(true);

        new AlertDialog.Builder(this)
            .setTitle("Set TagFocus")
            .setView(dialogView)
            .setPositiveButton("OK", (dialog, which) -> {
                boolean isTagFocus = radioGroup.getCheckedRadioButtonId() == R.id.radio_tagfocus_yes;
                String result = rfidHandler.tagFocus(isTagFocus);
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
