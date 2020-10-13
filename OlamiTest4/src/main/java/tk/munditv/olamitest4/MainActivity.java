package tk.munditv.olamitest4;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.olami.android.tts.ITtsPlayerListener;
import ai.olami.android.tts.TtsPlayer;
import ai.olami.cloudService.APIConfiguration;
import ai.olami.cloudService.APIResponse;
import ai.olami.cloudService.APIResponseData;
import ai.olami.cloudService.TextRecognizer;
import ai.olami.nli.DescObject;
import ai.olami.nli.NLIResult;
import ai.olami.util.GsonFactory;

import static android.content.pm.PackageManager.MATCH_ALL;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";
    public static final Integer RecordAudioRequestCode = 1;
    private SpeechRecognizer speechRecognizer;
    private Gson mJsonDump;
    private TextRecognizer mRecognizer;

    private EditText editText;
    private ImageView micButton;
    private AudioManager audioManager;
    private static Context context;
    private ArrayList<String> data;
    private TextView textInputResponse;

    TtsPlayerListener mTtsPlayerListener = null;
    TtsPlayer mTtsPlayer = null;

    private float mSpeed = 1.0f;
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            checkPermission();
        }

        editText = findViewById(R.id.text);
        micButton = findViewById(R.id.button);

        textInputResponse = (TextView) findViewById(R.id.textInputNLIAPIResponse);
        textInputResponse.setMovementMethod(ScrollingMovementMethod.getInstance());

        context = this;
        Log.d(TAG, "checking Speech Recognizer = " +checkSpeechRecognizer());
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this,
                ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"));
        final Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {

            }

            @Override
            public void onBeginningOfSpeech() {
                editText.setText("");
                editText.setHint("Listening...");
            }

            @Override
            public void onRmsChanged(float v) {

            }

            @Override
            public void onBufferReceived(byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {

            }

            @Override
            public void onError(int i) {

            }

            @Override
            public void onResults(Bundle bundle) {
                micButton.setImageResource(R.drawable.ic_mic_black_off);
                data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                editText.setText(data.get(0));
                processNLIAnalysis(data.get(0));
            }

            @Override
            public void onPartialResults(Bundle bundle) {

            }

            @Override
            public void onEvent(int i, Bundle bundle) {

            }
        });

        micButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_UP){
                    speechRecognizer.stopListening();
                    editText.setHint("");
                }
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN){
                    micButton.setImageResource(R.drawable.ic_mic_black_24dp);
                    speechRecognizer.startListening(speechRecognizerIntent);
                }
                return false;
            }
        });

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // for Olami Initialize
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

        mJsonDump = GsonFactory.getDebugGson(false);

        // * Step 1: Configure your key and localize option.
        APIConfiguration config = new APIConfiguration(
                Config.getAppKey(), Config.getAppSecret(), Config.getLocalizeOption());

        // * Step 2: Create the text recognizer.
        mRecognizer = new TextRecognizer(config);
        mRecognizer.setSdkType("android");

        // * Optional steps: Setup some other configurations.
        mRecognizer.setEndUserIdentifier("Someone");
        mRecognizer.setTimeout(10000);

        // Initial listener
        mTtsPlayerListener = new TtsPlayerListener();
        // Initial TTS player
        mTtsPlayer = new TtsPlayer(MainActivity.this, mTtsPlayerListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speechRecognizer.destroy();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},RecordAudioRequestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RecordAudioRequestCode && grantResults.length > 0 ){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this,"Permission Granted",Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkSpeechRecognizer() {

        boolean isRecognizerServiceValid = false;
        ComponentName currentRecognitionCmp = null;
        // 查找得到的 "可用的" 语音识别服务
        String serviceComponent = Settings.Secure.getString(context.getContentResolver(),
                "voice_recognition_service");
        Log.d(TAG, "voice_recognition service = " + serviceComponent);

        if (TextUtils.isEmpty(serviceComponent)) {
            return false;
        }
        ComponentName component = ComponentName.unflattenFromString(serviceComponent);
        List<ResolveInfo> list = context.getPackageManager().queryIntentServices(
                new Intent(RecognitionService.SERVICE_INTERFACE), MATCH_ALL);

        if (list != null && list.size() != 0) {
            for (ResolveInfo info : list) {
                Log.d(TAG, "\t" + info.loadLabel(context.getPackageManager()) + ": "
                        + info.serviceInfo.packageName + "/" + info.serviceInfo.name);

                // 这里拿系统使用的语音识别服务和内置的语音识别比较，如果相同，OK我们直接直接使用
                // 如果相同就可以直接使用mSpeechRecognizer = 			   SpeechRecognizer.createSpeechRecognizer(context);来创建实例，因为内置的可以使用
                if (info.serviceInfo.packageName.equals(component.getPackageName())) {
                    isRecognizerServiceValid = true;
                    break;
                } else {
                    // 如果服务不同，说明 内置服务 和 系统使用 不是同一个，那么我们需要使用系统使用的
                    // 因为内置的系统不用，我们用了也没有用
                    currentRecognitionCmp = new ComponentName(info.serviceInfo.packageName,
                            info.serviceInfo.name);
                }

            }
        } else {
            // 这里既是查不到可用的语音识别服务，可以歇菜了
            Log.d(TAG, "No recognition services installed");
            return false;
        }
        return true;
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
                            Toast.makeText(MainActivity.this, R.string.InputKeyIsEmpty, Toast.LENGTH_LONG).show();
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

    private void textInputAPIResponseChangeHandler(final String APIResponseDump) {
        new Handler(this.getMainLooper()).post(new Runnable(){
            public void run(){
                textInputResponse.setText(getString(R.string.Response) +" : \n"+ APIResponseDump);
            }
        });
    }

    private void processNLIAnalysis(final String textInput) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // * Send text to request NLI recognition.
                    APIResponse response = mRecognizer.requestNLI(textInput);
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
                        NLIResult[] res = response.getData().getNLIResults();
                        for (NLIResult nliResult : res) {
                            DescObject descObject = nliResult.getDescObject();
                            Log.d(TAG, " NLI Response results : " +descObject.getReplyAnswer());
                            TTSPlayStart(descObject.getReplyAnswer());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static boolean responswait = true;
    private static boolean asrFinal = false;

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

    private void TTSPlayStart(String str) {
        // * Set up TTS playback speed.
        mTtsPlayer.setSpeed(mSpeed);

        // * Set up TTS output volume.
        mTtsPlayer.setVolume(100);

        // * Play TTS by the specified text.
        mTtsPlayer.playText(str, true);
    }
}
