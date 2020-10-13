/*
	Copyright 2017, VIA Technologies, Inc. & OLAMI Team.

	http://olami.ai

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package tk.munditv.olamitest3;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

import ai.olami.android.IRecorderSpeechRecognizerListener;
import ai.olami.android.RecorderSpeechRecognizer;
import ai.olami.android.tts.ITtsPlayerListener;
import ai.olami.android.tts.TtsPlayer;
import ai.olami.cloudService.APIConfiguration;
import ai.olami.cloudService.APIResponse;
import ai.olami.cloudService.SpeechResult;
import ai.olami.cloudService.TextRecognizer;
import ai.olami.util.GsonFactory;

public class SpeechInputActivity extends AppCompatActivity {

    private final static String TAG = "SpeechInputActivity";

    private static final int REQUEST_EXTERNAL_PERMISSION = 1;
    private static final int REQUEST_MICROPHONE = 3;

    private RecorderSpeechRecognizer mRecognizer = null;
    private TextRecognizer mtextRecognizer;

    private final int VOLUME_BAR_MAX_VALUE = 40;
    private final int VOLUME_BAR_MAX_ITEM = 20;
    private final int VOLUME_BAR_ITEM_VALUE = VOLUME_BAR_MAX_VALUE / VOLUME_BAR_MAX_ITEM;

    private Button recordButton;
    private Button cancelButton;

    private TextView voiceVolumeText;
    private TextView voiceVolumeBar;
    private TextView STTText;
    private TextView APIResponseText;
    private TextView recognizeStatusText;
    private TextView recordStatusText;

    private RecorderSpeechRecognizer.RecordState mRecordState;
    private RecorderSpeechRecognizer.RecognizeState mRecognizeState;

    private Switch mAutoStopSwitch;

    TtsPlayerListener mTtsPlayerListener = null;
    TtsPlayer mTtsPlayer = null;
    private Gson mJsonDump;

    private float mSpeed = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_input);
        mJsonDump = GsonFactory.getDebugGson(false);

        Intent intent = getIntent();
        Config.setLocalizeOption(intent.getIntExtra("LOCALIZE_OPTION", Config.getLocalizeOption()));

        recordButton = (Button) findViewById(R.id.recordButton);
        cancelButton = (Button) findViewById(R.id.cancelButton);
        voiceVolumeText = (TextView) findViewById(R.id.voiceVolume);
        voiceVolumeBar = (TextView) findViewById(R.id.voiceVolumeBar);
        STTText = (TextView) findViewById(R.id.STTText);
        APIResponseText = (TextView) findViewById(R.id.APIResponse);
        APIResponseText.setMovementMethod(ScrollingMovementMethod.getInstance());
        recognizeStatusText = (TextView) findViewById(R.id.recognizeStatus);
        recordStatusText = (TextView) findViewById(R.id.recordStatus);

        recordButton.setOnClickListener(new recordButtonListener());
        cancelButton.setOnClickListener(new cancelButtonListener());

        mAutoStopSwitch = (Switch) findViewById(R.id.autoStopSwitch);
        mAutoStopSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mRecognizer != null) {
                    mRecognizer.enableAutoStopRecording(isChecked);
                }
            }
        });

        // load the default App-Key & App-Secret.
        String appKey = Config.getAppKey();
        String appSecret = Config.getAppSecret();
        if ((appKey.isEmpty() || appKey.startsWith("*"))
                || (appSecret.isEmpty() || appSecret.startsWith("*"))) {
            // If the developer doesn't change keys, pop up and the developer to input their keys.
            onCreateConfigurationDialog().show();
        }

        // Set default localization setting by the setting of system language.
        String systemLanguage = getSystemLanguage();
        if (systemLanguage.equals("zh-TW")) {
            Config.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_TRADITIONAL_CHINESE);
        } else {
            Config.setLocalizeOption(APIConfiguration.LOCALIZE_OPTION_SIMPLIFIED_CHINESE);
        }

        // Initial listener
        mTtsPlayerListener = new TtsPlayerListener();
        // Initial TTS player
        mTtsPlayer = new TtsPlayer(SpeechInputActivity.this, mTtsPlayerListener);

    }

    public Dialog onCreateConfigurationDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.Input);
        final LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.configuration_setting, null);

        final EditText appKeyInput = (EditText)  view.findViewById(R.id.appKeyInput);
        final EditText appSecretInput = (EditText) view.findViewById(R.id.appSecretInput);

        builder.setView(view)
                .setPositiveButton(R.string.Submit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String userAppKeyInput = appKeyInput.getText().toString();
                        String userAppSecret = appSecretInput.getText().toString();

                        if (userAppKeyInput.isEmpty() || userAppSecret.isEmpty()) {
                            Toast.makeText(SpeechInputActivity.this, R.string.InputKeyIsEmpty, Toast.LENGTH_LONG).show();
                            onCreateConfigurationDialog().show();
                        } else {
                            // The developer has already inputted keys.
                            Config.setAppKey(userAppKeyInput);
                            Config.setAppSecret(userAppSecret);
                        }
                    }
                })
                .setNegativeButton(R.string.Register, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent intent= new Intent(Intent.ACTION_VIEW, Uri.parse("https://olami.ai"));
                        startActivity(intent);
                        onCreateConfigurationDialog().show();
                    }
                });
        return builder.create();
    }

    protected String getSystemLanguage() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = LocaleList.getDefault().get(0);
        } else {
            locale = Locale.getDefault();
        }

        String language = locale.getLanguage() + "-" + locale.getCountry();
        return language;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if the user agrees to access the microphone
        boolean hasMicrophonePermission = checkApplicationPermissions(
                Manifest.permission.RECORD_AUDIO,
                REQUEST_MICROPHONE);

        if (hasMicrophonePermission) {
            // * Step 1: Configure your key and localize option.
            APIConfiguration config = new APIConfiguration(
                    Config.getAppKey(), Config.getAppSecret(), Config.getLocalizeOption());

            // * Step 2: Create the microphone recording speech recognizer.
            //           ----------------------------------------------------------
            //           You should implement the IRecorderSpeechRecognizerListener
            //           to get all callbacks and assign the instance of your
            //           listener class into this recognizer.
            mRecognizer = RecorderSpeechRecognizer.create(new SpeechRecognizerListener(), config);

            // * Optional step: Setup the recognize result type of your request.
            //                  The default setting is RECOGNIZE_RESULT_TYPE_STT for Speech-To-Text.
            mRecognizer.setRecognizeResultType(RecorderSpeechRecognizer.RECOGNIZE_RESULT_TYPE_ALL);

            // * Other optional steps: Setup some other configurations.
            //                         You can use default settings without bellow steps.
            mRecognizer.setEndUserIdentifier("Someone");
            mRecognizer.setApiRequestTimeout(3000);

            // * Advanced setting example.
            //   These are also optional steps, so you can skip these
            //   (or any one of these) to use default setting(s).
            // ------------------------------------------------------------------
            // * You can set the length of end time of the VAD in milliseconds
            //   to stop voice recording automatically.
            mRecognizer.setLengthOfVADEnd(2000);
            // * You can set the frequency in milliseconds of the recognition
            //   result query, then the recognizer client will query the result
            //   once every milliseconds you set.
            mRecognizer.setResultQueryFrequency(100);
            // * You can set audio length in milliseconds to upload, then
            //   the recognizer client will upload parts of audio once every
            //   milliseconds you set.
            mRecognizer.setSpeechUploadLength(300);
            // * Due to the different microphone sensitivity of each different device,
            //   you can set level of silence volume of the VAD
            //   to stop voice recording automatically.
            //   The recommended value is 5 to 10.
            mRecognizer.setSilenceLevelOfVADTail(5);
            // ------------------------------------------------------------------

            // Initialize volume bar of the input audio.
            voiceVolumeChangeHandler(0);

            if (mRecognizer.isAutoStopRecordingEnabled()) {
                autoStopSwitchChangeHandler(true);
            } else {
                autoStopSwitchChangeHandler(false);
            }

            // * Step 2: Create the text recognizer.
            mtextRecognizer = new TextRecognizer(config);
            mtextRecognizer.setSdkType("android");

            // * Optional steps: Setup some other configurations.
            mtextRecognizer.setEndUserIdentifier("Someone");
            mtextRecognizer.setTimeout(10000);

        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // * Release the recognizer when program stops or exits.
        if (mRecognizer != null) {
            mRecognizer.release();
        }
    }

    protected class recordButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            // Get current voice recording state.
            mRecordState = mRecognizer.getRecordState();

            // Check to see if we should start recording or stop manually.
            if (mRecordState == RecorderSpeechRecognizer.RecordState.STOPPED) {
                try {

                    // * Request to start voice recording and recognition.
                    mRecognizer.start();
                    //
                    // You can also send text with NLIConfig to append "nli_config" JSON object.
                    //
                    // For Example, try to replace 'start()' with the following sample code:
                    // ===================================================================
                    // NLIConfig nliConfig = new NLIConfig();
                    // nliConfig.setSlotName("myslot");
                    // mRecognizer.start(nliConfig);
                    // ===================================================================
                    //

                } catch (InterruptedException e) {

                    e.printStackTrace();

                }
                recordButton.setEnabled(false);

            } else if (mRecordState == RecorderSpeechRecognizer.RecordState.RECORDING) {

                // * Request to stop voice recording when manually stop,
                //   and then wait for the final recognition result.
                mRecognizer.stop();

            }
        }
    }

    private class cancelButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {

            // * Issue to cancel all process including voice recording
            //   and speech recognition.
            mRecognizer.cancel();

        }
    }

    /**
     * This is a callback listener example.
     *
     * You should implement the IRecorderSpeechRecognizerListener
     * to get all callbacks and assign the instance of your listener class
     * into the recognizer instance of RecorderSpeechRecognizer.
     */
    private class SpeechRecognizerListener implements IRecorderSpeechRecognizerListener {

        // * Implement override method to get callback when the voice recording
        //   process state changes.
        @Override
        public void onRecordStateChange(RecorderSpeechRecognizer.RecordState state) {
            String StatusStr = getString(R.string.RecordState) + " : ";
            mRecordState = state;

            if (state == RecorderSpeechRecognizer.RecordState.STOPPED) {

                // * The recording process is stopped.
                // * This is also the beginning or end of the life cycle.

                StatusStr += getString(R.string.RecordState_STOPPED);
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(true, getString(R.string.recordButton_start));
                cancelButtonChangeHandler(View.INVISIBLE, "");
            } else if (state == RecorderSpeechRecognizer.RecordState.INITIALIZING) {

                // * The recording process is initializing.
                // * This is normally starts after the STOPPED state.

                StatusStr += getString(R.string.RecordState_INITIALIZING) + "...";
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(false, StatusStr);
                cancelButtonChangeHandler(View.INVISIBLE, "");
                APIResponseChangeHandler("");
                STTChangeHandler("");
                APIResponseChangeHandler("");

            } else if (state == RecorderSpeechRecognizer.RecordState.INITIALIZED) {

                // * The recording process is initialized.
                // * This is normally starts after the INITIALIZING state.

                StatusStr += getString(R.string.RecordState_INITIALIZED);
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(false, StatusStr);
                cancelButtonChangeHandler(View.INVISIBLE, "");

            } else if (state == RecorderSpeechRecognizer.RecordState.RECORDING) {

                // * The recording process is starting.
                // * This is normally starts after the INITIALIZED state.

                StatusStr += getString(R.string.RecordState_RECORDING) + "...";
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(true, getString(R.string.recordButton_stop));
                cancelButtonChangeHandler(View.VISIBLE, "X");
                responswait = false;
            } else if (state == RecorderSpeechRecognizer.RecordState.STOPPING) {

                // * The recording process is stopping.
                // * This is normally starts after the RECORDING state
                // * and the next state should be STOPPED.
                // * --------------------------------------------------------
                //   This DOES NOT mean that the speech recognition process
                //   is also being stopped.
                // * --------------------------------------------------------

                StatusStr += getString(R.string.RecordState_STOPPING) + "...";
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(false, StatusStr);
                cancelButtonChangeHandler(View.VISIBLE, "X");
                voiceVolumeChangeHandler(0);

                if (false) {
                    if (!asrFinal && !responswait) {
                        String anserString = getString(R.string.pleaseWait);
                        TTSPlayStart(anserString);
                        responswait = true;
                    }
                }


            } else if (state == RecorderSpeechRecognizer.RecordState.ERROR) {

                // * There was an error in the recording process.

                StatusStr += getString(R.string.RecordState_ERROR);
                Log.i(TAG, StatusStr);
                recordStateHandler(StatusStr);
                recordButtonChangeHandler(false, StatusStr);
                cancelButtonChangeHandler(View.VISIBLE, "X");
                voiceVolumeChangeHandler(0);
                errorStateHandler(StatusStr);

            }
        }

        // * Implement override method to get callback when the recognize
        //   process state changes.
        @Override
        public void onRecognizeStateChange(RecorderSpeechRecognizer.RecognizeState state) {
            String StatusStr = getString(R.string.RecognizeState) +" : ";
            mRecognizeState = state;

            if (state == RecorderSpeechRecognizer.RecognizeState.STOPPED) {

                // * The recognize process is stopped.
                // * This is also the beginning or end of the life cycle.

                StatusStr += getString(R.string.RecognizeState_STOPPED);
                Log.i(TAG, StatusStr);
                recognizeStateHandler(StatusStr);

            } else if (state == RecorderSpeechRecognizer.RecognizeState.PROCESSING) {

                // * The recognize process is start running.
                // * This is normally starts after the STOPPED state.

                StatusStr += getString(R.string.RecognizeState_PROCESSING) +"...";
                Log.i(TAG, StatusStr);
                recognizeStateHandler(StatusStr);

            } else if (state == RecorderSpeechRecognizer.RecognizeState.COMPLETED) {

                // * The recognize process is start running.
                // * This is normally starts after the PROCESSING state.
                // * and the next state should be STOPPED.
                // * --------------------------------------------------------
                //   It means that a complete voice recognition has been done.
                //   So it usually triggers
                //   onRecognizeResultChange(APIResponse response) callback,
                //   then you will get complete results including the content
                //   of speech-to-text, NLI or IDS data in that callback.
                // * --------------------------------------------------------

                StatusStr += getString(R.string.RecognizeState_COMPLETED);
                Log.i(TAG, StatusStr);
                recognizeStateHandler(StatusStr);

            } else if (state == RecorderSpeechRecognizer.RecognizeState.ERROR) {

                // * There was an error in the recognize process.

                StatusStr += getString(R.string.RecognizeState_ERROR);
                Log.i(TAG, StatusStr);
                recognizeStateHandler(StatusStr);
                errorStateHandler(StatusStr);

            }
        }

        // * Implement override method to get callback when the results
        //   of speech recognition changes.
        @Override
        public void onRecognizeResultChange(APIResponse response) {

            // * Get recognition results.
            //   In this example, we only handle the speech-to-text result.
            SpeechResult sttResult = response.getData().getSpeechResult();

            if (sttResult.complete()) {

                // 'complete() == true' means returned text is final result.

                // --------------------------------------------------
                // * It also means you can get NLI/IDS results if included.
                //   So you can handle or process NLI/IDS results here ...
                //
                //   For example:
                //   NLIResult[] nliResults = response.getData().getNLIResults();
                //
                // * See also :
                //   - OLAMI Java Client SDK & Examples
                //   - ai.olami.nli.NLIResult.
                // --------------------------------------------------

                STTChangeHandler(sttResult.getResult());
                APIResponseChangeHandler(response.toString());

            } else {

                // Recognition has not yet been completed.
                // The text you get here is not a final result.

                if (sttResult.getStatus() == SpeechResult.STATUS_RECOGNIZE_OK) {
                    STTChangeHandler(sttResult.getResult());
                    APIResponseChangeHandler(response.toString());
                }

            }
        }

        // * Implement override method to get callback when the volume of
        //   voice input changes.
        @Override
        public void onRecordVolumeChange(int volumeValue) {

            // Do something here when you get the changed volume.

            voiceVolumeChangeHandler(volumeValue);
        }

        // * Implement override method to get callback when server error occurs.
        @Override
        public void onServerError(APIResponse response) {
            Log.e(TAG, "Server error code: "+ response.getErrorCode()
                    +", Error message: " + response.getErrorMessage());
            errorStateHandler("onServerError Code: "+ response.getErrorCode());
        }

        // * Implement override method to get callback when error occurs.
        @Override
        public void onError(RecorderSpeechRecognizer.Error error) {
            Log.e(TAG, "Error code:"+ error.name());
            errorStateHandler("RecorderSpeechRecognizer.Error: "+ error.name());
        }

        // * Implement override method to get callback when exception occurs.
        @Override
        public void onException(Exception e) {
            e.printStackTrace();
        }
    }

    private void recordButtonChangeHandler(final boolean isEnabled, final String buttonString) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                recordButton.setEnabled(isEnabled);
                recordButton.setText(buttonString);
            }
        });
    }

    private void cancelButtonChangeHandler(final int isVisibility, final String buttonString) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                cancelButton.setVisibility(isVisibility);
                cancelButton.setText(buttonString);
            }
        });
    }

    private void voiceVolumeChangeHandler(final int volume) {
        final int volumeBarItemCount = volume / VOLUME_BAR_ITEM_VALUE;

        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                voiceVolumeText.setText(getString(R.string.Volume) +" : "+ volume);
                // Voice volume bar value change
                String voiceVolumeBarStr = "▌";
                for (int i = 1; i < volumeBarItemCount && i <= VOLUME_BAR_MAX_ITEM;
                     i++) {
                    voiceVolumeBarStr += "▌";
                }
                voiceVolumeBar.setText(voiceVolumeBarStr);

                // Voice volume bar color change
                if (volumeBarItemCount >= 0 && volumeBarItemCount <= 7) {
                    voiceVolumeBar.setTextColor(Color.GREEN);
                } else if (volumeBarItemCount >= 7 && volumeBarItemCount <= 14) {
                    voiceVolumeBar.setTextColor(Color.BLUE);
                } else {
                    voiceVolumeBar.setTextColor(Color.RED);
                }
            }
        });
    }

    private void STTChangeHandler(final String STTStr) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                STTText.setText(STTStr);
            }
        });
    }

    private void textInputAPIResponseChangeHandler(final String APIResponseDump) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                //textInputResponse.setText(getString(R.string.Response) +" : \n"+ APIResponseDump);
                ParseAPIResponse(APIResponseDump);
            }
        });
    }

    private void APIResponseChangeHandler(final String APIResponseStr) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                APIResponseText.setText(getString(R.string.Response) +" :\n"+ APIResponseStr);
                ParseAPIResponse(APIResponseStr);
            }
        });
    }

    private void recognizeStateHandler(final String recognizeStatusStr) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                recognizeStatusText.setText(recognizeStatusStr);
            }
        });
    }

    private void recordStateHandler(final String recordStatusStr) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                recordStatusText.setText(recordStatusStr);
            }
        });
    }

    private void errorStateHandler(final String errorString) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                Toast.makeText(getApplicationContext(),
                        errorString,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void autoStopSwitchChangeHandler(final boolean isChecked) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                mAutoStopSwitch.setChecked(isChecked);
            }
        });
    }

    private boolean checkApplicationPermissions(String permissionStr, int requestCode) {
        // Check to see if we have permission to access something,
        // such like the microphone.
        int permission = ActivityCompat.checkSelfPermission(
                SpeechInputActivity.this,
                permissionStr);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We can not access it, request authorization from the user.
            ActivityCompat.requestPermissions(
                    SpeechInputActivity.this,
                    new String[] {permissionStr},
                    requestCode
            );
            return false;
        } else {
            return true;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_MICROPHONE:
                for (int i = 0; i < permissions.length; i++) {
                    String permission = permissions[i];
                    int grantResult = grantResults[i];
                    if (permission.equals(Manifest.permission.RECORD_AUDIO)) {
                        if(grantResult == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(
                                    this,
                                    getString(R.string.GetMicrophonePermission),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this,
                                    getString(R.string.GetMicrophonePermissionDenied),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                break;
            case REQUEST_EXTERNAL_PERMISSION:
                for (int i = 0; i < permissions.length; i++) {
                    String permission = permissions[i];
                    int grantResult = grantResults[i];
                    if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if(grantResult == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(
                                    this,
                                    getString(R.string.GetWriteStoragePermission),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this,
                                    getString(R.string.GetWriteStoragePermissionDenied),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                break;
        }
    }

    private static boolean responswait = true;
    private static boolean asrFinal = false;

    private void ParseAPIResponse(String APIResponseStr) {
        try {
            JSONObject object = new JSONObject(APIResponseStr);
            String dataString = object.getString("data");
            String statusString = object.getString("status");
            JSONObject dataObject = new JSONObject(dataString);
            String asrString = dataObject.getString("asr");

            // parsing asr json string to object
            JSONObject asrObject = new JSONObject(asrString);
            final String asrResult = asrObject.getString("result");
            String asrRequestId = asrObject.getString("requestId");
            String asrSpechStatus = asrObject.getString("speech_status");
            asrFinal = asrObject.getBoolean("final");
            String asrSegResult = asrObject.getString("segResult");
            int asrStatus = asrObject.getInt("status");
            if (asrFinal) {

                String segString = dataObject.getString("seg");
                String nliString = dataObject.getString("nli");

                JSONArray nliArray = new JSONArray(nliString);
                for (int i = 0; i < nliArray.length(); i++) {
                    JSONObject nliObject = nliArray.getJSONObject(i);

                    //get NLI Desc String
                    String nliDescString = nliObject.getString("desc_obj");
                    JSONObject nliDescObject = new JSONObject(nliDescString);
                    String nliDescResultString = nliDescObject.getString("result");

                    int nliDescStatus = nliDescObject.getInt("status");
                    if (nliDescStatus == 0) {
                        String nliTypeString = nliObject.getString("type");
                        if (nliTypeString.equals("weather")) {
                            String nliDescUrlString = nliDescObject.getString("url");
                        }
                    } else if (nliDescStatus == 3001) {
                        nliDescResultString = "網路忙線中";
                    }
                    TTSPlayStart(nliDescResultString);
                    responswait = false;
                }
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // * Send text to request NLI recognition.
                            APIResponse response = mtextRecognizer.requestNLI(asrResult);
                            //
                            // You can also send text with NLIConfig to append "nli_config" JSON object.
                            //
                            // For Example,
                            // try to replace 'requestNLI(inputText)' with the following sample code:
                            // ===================================================================
                            // NLIConfig nliConfig = new NLIConfig();
                            // nliConfig.setSlotName("myslot");
                            // APIResponse response = mRecognizer.requestNLI(textInputEdit.getText().toString(), nliConfig);
                            // ===================================================================
                            //

                            // Check request status.
                            if (response.ok() && response.hasData()) {
                                // * Dump NLI results by JSON format.
                                textInputAPIResponseChangeHandler(mJsonDump.toJson(response));
                                // --------------------------------------------------
                                // * You can also handle or process NLI/IDS results ...
                                //
                                //   For example:
                                //   NLIResult[] nliResults = response.getData().getNLIResults();
                                //
                                // * See also :
                                //   - OLAMI Java Client SDK & Examples
                                //   - ai.olami.nli.NLIResult.
                                // --------------------------------------------------
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void TTSPlayStart(String resultStr) {

        // * Set up TTS playback speed.
        mTtsPlayer.setSpeed(mSpeed);

        // * Set up TTS output volume.
        mTtsPlayer.setVolume(100);

        // * Play TTS by the specified text.
        mTtsPlayer.playText(resultStr, true);
    }

    /**
     * This is a callback listener example.
     *
     * You should implement the ITtsPlayerListener
     * to get all callbacks and assign the instance of your listener class
     * into the TTS player instance of TtsPlayer.
     */
    public class TtsPlayerListener implements ITtsPlayerListener {
        @Override
        public void onPlayEnd() {
            Log.i(TAG, "--------onPlayEnd()---------");
        }

        @Override
        public void onStop() {
            Log.i(TAG, "--------onStop()---------");
        }

        @Override
        public void onPlayingTTS(String TTSString) {
            Log.i(TAG, "--------onPlayingTTS()---------"+ TTSString);
        }
    }
}
