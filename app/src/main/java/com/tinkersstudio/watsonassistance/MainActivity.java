/**
 * Copyright IBM Corporation 2016
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.tinkersstudio.watsonassistance;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.tinkersstudio.watsonassistance.CameraHelper;
import com.tinkersstudio.watsonassistance.GalleryHelper;
import com.tinkersstudio.watsonassistance.MicrophoneInputStream;
import com.tinkersstudio.watsonassistance.StreamPlayer;
import com.ibm.watson.developer_cloud.language_translation.v2.LanguageTranslation;
import com.ibm.watson.developer_cloud.language_translation.v2.model.Language;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.RecognizeCallback;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.DetectedFaces;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.Face;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualRecognitionOptions;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";

    private RadioGroup targetLanguage;
    private EditText input;
    private ImageButton mic;
    private Button translate;
    private ImageButton play;
    private TextView translatedText;
    private ImageView loadedImage;
    private TextView ageMinText;
    private TextView ageMaxText;
    private TextView genderText;

    private SpeechToText speechService;
    private TextToSpeech textService;
    private LanguageTranslation translationService;
    private VisualRecognition visualService;
    private Language selectedTargetLanguage = Language.SPANISH;

    private StreamPlayer player = new StreamPlayer();
    private CameraHelper cameraHelper;
    private GalleryHelper galleryHelper;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraHelper = new CameraHelper(this);
        galleryHelper = new GalleryHelper(this);

        speechService = initSpeechToTextService();
        textService = initTextToSpeechService();
        translationService = initLanguageTranslationService();
        visualService = initVisualRecognitionService();

        //setup screen elements
        targetLanguage = (RadioGroup) findViewById(R.id.target_language);
        input = (EditText) findViewById(R.id.input);
        mic = (ImageButton) findViewById(R.id.mic);
        translate = (Button) findViewById(R.id.translate);
        play = (ImageButton) findViewById(R.id.play);
        translatedText = (TextView) findViewById(R.id.translated_text);
        loadedImage = (ImageView) findViewById(R.id.loaded_image);
        ageMinText = (TextView) findViewById(R.id.visual_age_min);
        ageMaxText = (TextView) findViewById(R.id.visual_age_max);
        genderText = (TextView) findViewById(R.id.visual_gender);

        //setup buttons for language selection
        targetLanguage.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.spanish:
                        selectedTargetLanguage = Language.SPANISH;
                        break;
                    case R.id.french:
                        selectedTargetLanguage = Language.FRENCH;
                        break;
                    case R.id.italian:
                        selectedTargetLanguage = Language.ITALIAN;
                        break;
                }
            }
        });

        //setup listener for keyboard input
        input.addTextChangedListener(new EmptyTextWatcher() {
            @Override public void onEmpty(boolean empty) {
                if (empty) {
                    translate.setEnabled(false);
                } else {
                    translate.setEnabled(true);
                }
            }
        });

        //setup microphone listener
        mic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mic.setEnabled(false);

                new Thread(new Runnable() {
                    @Override public void run() {
                        try {
                            RecognizeOptions options = new RecognizeOptions.Builder()
                                    .continuous(true)
                                    .contentType(MicrophoneInputStream.CONTENT_TYPE)
                                    .model("en-US_BroadbandModel")
                                    .interimResults(true)
                                    .inactivityTimeout(2000)
                                    .build();

                            speechService.recognizeUsingWebSocket(new MicrophoneInputStream(),
                                    options, new MicrophoneRecognizeDelegate());
                        } catch (Exception e) {
                            showError(e);
                        }
                    }
                }).start();
            }
        });

        //setup translate button listener
        translate.setOnClickListener(new View.OnClickListener() {

            @Override public void onClick(View v) {
                new TranslationTask().execute(input.getText().toString());
            }
        });

        //setup translate text listener
        translatedText.addTextChangedListener(new EmptyTextWatcher() {
            @Override public void onEmpty(boolean empty) {
                if (empty) {
                    play.setEnabled(false);
                } else {
                    play.setEnabled(true);
                }
            }
        });

        //setup play button for text to speech
        play.setEnabled(false);
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new SynthesisTask().execute(translatedText.getText().toString());
            }
        });
    }

    private SpeechToText initSpeechToTextService() {
        SpeechToText service = new SpeechToText();
        String username = getString(R.string.speech_text_username);
        String password = getString(R.string.speech_text_password);
        service.setUsernameAndPassword(username, password);
        service.setEndPoint("https://stream.watsonplatform.net/speech-to-text/api");
        return service;
    }

    private TextToSpeech initTextToSpeechService() {
        TextToSpeech service = new TextToSpeech();
        String username = getString(R.string.text_speech_username);
        String password = getString(R.string.text_speech_password);
        service.setUsernameAndPassword(username, password);
        return service;
    }

    private LanguageTranslation initLanguageTranslationService() {
        LanguageTranslation service = new LanguageTranslation();
        String username = getString(R.string.language_translation_username);
        String password = getString(R.string.language_translation_password);
        service.setUsernameAndPassword(username, password);
        return service;
    }

    private VisualRecognition initVisualRecognitionService() {
        return new VisualRecognition(VisualRecognition.VERSION_DATE_2016_05_20,
                getString(R.string.visual_recognition_api_key));
    }

    private class MicrophoneRecognizeDelegate implements RecognizeCallback {
        @Override public void onTranscription(SpeechResults speechResults) {
            String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
            String takeASelfie = "take a selfie ";
            if (text.equals(takeASelfie) && !input.getText().toString().equals(takeASelfie)) {
                cameraHelper.dispatchTakePictureIntent();
            }
            showMicText(text);
        }


        @Override public void onConnected() {

        }

        @Override public void onError(Exception e) {
            showError(e);
            enableMicButton();
        }

        @Override public void onDisconnected() {
            enableMicButton();
        }
    }

    private class TranslationTask extends AsyncTask<String, Void, String> {
        @Override protected String doInBackground(String... params) {
            showTranslation(translationService.translate(params[0], Language.ENGLISH, selectedTargetLanguage).execute().getFirstTranslation());
            return "Did translate";
        }
    }

    private class SynthesisTask extends AsyncTask<String, Void, String> {
        @Override protected String doInBackground(String... params) {
            player.playStream(textService.synthesize(params[0], Voice.EN_LISA).execute());
            return "Did syntesize";
        }
    }

    private class VisualTask extends AsyncTask<Integer, Void, String> {
        @Override protected String doInBackground(Integer... integers) {
            VisualRecognitionOptions options = new VisualRecognitionOptions.Builder()
                    .images(cameraHelper.getFile(integers[0]))
                    .build();

            DetectedFaces faces = visualService.detectFaces(options).execute();

            if (!faces.getImages().get(0).getFaces().isEmpty()) {
                Face face = faces.getImages().get(0).getFaces().get(0);
                Face.Age age = faces.getImages().get(0).getFaces().get(0).getAge();
                showAgeMin(Integer.toString(age.getMin()));
                showAgeMax(Integer.toString(age.getMax()));
                showGender(face.getGender().getGender());
            }

            return "Did visual";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CameraHelper.REQUEST_IMAGE_CAPTURE) {
            loadedImage.setImageBitmap(cameraHelper.getBitmap(resultCode));
            new VisualTask().execute(resultCode);
        }

        if (requestCode == GalleryHelper.PICK_IMAGE_REQUEST) {
            loadedImage.setImageBitmap(galleryHelper.getBitmap(resultCode, data));
        }
    }


    //------------------UI methods--------------------------------------

    private void showTranslation(final String translation) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                translatedText.setText(translation);
            }
        });
    }

    private void showError(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }

    private void showMicText(final String text) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                input.setText(text);
            }
        });
    }

    private void showAgeMin(final String ageMin) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                ageMinText.setText(ageMin);
            }
        });
    }

    private void showAgeMax(final String ageMax) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                ageMaxText.setText(ageMax);
            }
        });
    }

    private void showGender(final String gender) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                genderText.setText(gender);
            }
        });
    }

    private void enableMicButton() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                mic.setEnabled(true);
            }
        });
    }

    //Watch for keyboard input
    private abstract class EmptyTextWatcher implements TextWatcher {
        private boolean isEmpty = true; // assumes text is initially empty

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.length() == 0) {
                isEmpty = true;
                onEmpty(true);
            } else if (isEmpty) {
                isEmpty = false;
                onEmpty(false);
            }
        }

        @Override public void afterTextChanged(Editable s) {}

        public abstract void onEmpty(boolean empty);
    }

}
