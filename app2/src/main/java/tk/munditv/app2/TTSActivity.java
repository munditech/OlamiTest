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

package tk.munditv.app2;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import ai.olami.android.tts.ITtsPlayerListener;
import ai.olami.android.tts.TtsPlayer;

public class TTSActivity extends AppCompatActivity {

    public final static String TAG = "TTSMainActivity";

    private EditText mUserInput;
    private EditText mSpeedEdittext;
    private Button mPlayTTSButton;
    private SeekBar mSpeedBar;

    TtsPlayerListener mTtsPlayerListener = null;
    TtsPlayer mTtsPlayer = null;

    private float mSpeed = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUserInput = (EditText) findViewById(R.id.editText);
        mPlayTTSButton = (Button) findViewById(R.id.button);
        mSpeedBar = (SeekBar) findViewById(R.id.seekBar2);
        mSpeedEdittext = (EditText) findViewById(R.id.speedEdittext);

        mPlayTTSButton.setOnClickListener(new playTTSButtonListener());
        mSpeedBar.setOnSeekBarChangeListener(new speedBarListener());

        // Initial listener
        mTtsPlayerListener = new TtsPlayerListener();
        // Initial TTS player
        mTtsPlayer = new TtsPlayer(TTSActivity.this, mTtsPlayerListener);
    }

    protected class playTTSButtonListener implements View.OnClickListener {
        @Override
        public void onClick (View v) {
            String userInputString;
            userInputString = mUserInput.getText().toString();

            // * Set up TTS playback speed.
            mTtsPlayer.setSpeed(mSpeed);

            // * Set up TTS output volume.
            mTtsPlayer.setVolume(100);

            // * Play TTS by the specified text.
            mTtsPlayer.playText(userInputString, true);
        }
    }

    protected class speedBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mSpeed = (float) progress / 10;
            mSpeedEdittext.setText(mSpeed +"");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
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
