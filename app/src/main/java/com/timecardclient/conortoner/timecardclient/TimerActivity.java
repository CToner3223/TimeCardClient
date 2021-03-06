package com.timecardclient.conortoner.timecardclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.MediaRouteButton;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class TimerActivity extends AppCompatActivity {

    private static final String LOG_TAG = "TimerActivity";
    private static final String OUTPUT_RESULTS_FILENAME = "results.csv";
    private static final String OUTPUT_SYNC_STATUS_FILENAME = "sync.csv";
    private boolean timerStarted = false;
    private Handler timerHandler = new Handler();
    private long startTime;
    private TextView timer;
    private FloatingActionButton saveFab;
    private TimerActivity thisActivity;
    private long stopTime;
    private EditText carNumber;
    private NumberPicker penaltyPicker;
    private Switch wrongTest;
    private ArrayDeque<String> retryQue = new ArrayDeque<>();
    private Handler reQueHandler = new Handler();
    private String host = "http://192.168.224.236:8080";
    private String marshalName = null;
    private String curentLayout = null;
    private Button startStopButton = null;
    private Button resetButton = null;


    private Runnable reQue = new Runnable() {
        @Override
        public void run() {
            if (retryQue.size() > 0) {
                retryFailedSaves();
            }
            reQueHandler.postDelayed(this, 20000);
        }
    };


    private void retryFailedSaves() {
        Log.e(LOG_TAG, "retrying network calls, que size: " + retryQue.size());
        new HttpRequestTask(retryQue.pop()).execute();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timer);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        thisActivity = this;

        timer = (TextView) findViewById(R.id.timer);
        saveFab = (FloatingActionButton) findViewById(R.id.saveFab);
        saveFab.hide();
        carNumber = (EditText) findViewById(R.id.carNumber);
        penaltyPicker = (NumberPicker) findViewById(R.id.penaltyPicker);
        wrongTest = (Switch) findViewById(R.id.wTSwitch);
        startStopButton = (Button) findViewById(R.id.button);
        resetButton = (Button) findViewById(R.id.resetButton);

        String[] nums = new String[21];
        for (int i = 0; i < nums.length; i++)
            nums[i] = Integer.toString(i);

        penaltyPicker.setMinValue(0);
        penaltyPicker.setMaxValue(20);
        penaltyPicker.setWrapSelectorWheel(false);
        penaltyPicker.setDisplayedValues(nums);
        penaltyPicker.setValue(0);
        List<String> results = readFromFile(getFileForResultsStorage(OUTPUT_RESULTS_FILENAME));
        List<String> synchedResults = readFromFile(getFileForResultsStorage(OUTPUT_SYNC_STATUS_FILENAME));
        for (String result : results) {
            if (!synchedResults.contains(result)) {
                Log.d(LOG_TAG, "Adding item to retry queue: " + result);
                retryQue.push(result);
            }
        }
        reQueHandler.postDelayed(reQue, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_timer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                openSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    private void openSettings() {
        Intent i = new Intent(getApplicationContext(), SettingsActivity.class);

        i.putExtra("hostLocation", host);
        i.putExtra("marshalName", marshalName);
        i.putExtra("layout", curentLayout);

        startActivityForResult(i, 1);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case (1): {
                if (resultCode == Activity.RESULT_OK) {
                    Bundle extras = data.getExtras();
                    host = data.getStringExtra("hostLocation");
                    marshalName = data.getStringExtra("marshalName");
                    curentLayout = data.getStringExtra("layout");
                }
                break;
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putLong(getString(R.string.startTimeLable), startTime);
        savedInstanceState.putLong(getString(R.string.stopTimeLable), stopTime);
        savedInstanceState.putBoolean(getString(R.string.timerStateLable), timerStarted);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        startTime = savedInstanceState.getLong(getString(R.string.startTimeLable));
        stopTime = savedInstanceState.getLong(getString(R.string.stopTimeLable));
        timerStarted = savedInstanceState.getBoolean(getString(R.string.timerStateLable));
        if (timerStarted) {
            timerHandler.postDelayed(startTimer, 0);
        }
    }

    public void onStartStop(View view) {
        if (!timerStarted) {
            if (allFieldsPopulated()) {
                timerStarted = true;
                startTime = System.currentTimeMillis();
                timerHandler.removeCallbacks(startTimer);
                timerHandler.postDelayed(startTimer, 0);
                saveFab.hide();
                resetButton.setEnabled(false);
            }
        } else {
            stopTime = System.currentTimeMillis();
            timerStarted = false;
            timerHandler.removeCallbacks(startTimer);
            startStopButton.setEnabled(false);
            saveFab.show();
            resetButton.setEnabled(true);
            startStopButton.setEnabled(false);
        }
    }

    private boolean allFieldsPopulated() {
        try {
            validateStringField("Marshal Name in Settings", marshalName);
            validateStringField("Layout in Settings", curentLayout);
            validateNumberField("Layout in Settings", curentLayout);
            validateStringField("Car Number", carNumber.getText().toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateNumberField(String name, String value) throws Exception {
        Log.d(LOG_TAG, String.format("Trying to parse %s as a number", value));
        if(!Pattern.matches("[0-9]+",value)){
            new AlertDialog.Builder(this)
                    .setTitle("Ooops!")
                    .setMessage(String.format("You haven't entered a number for %s!", name))
                    .setIcon(android.R.drawable.ic_dialog_alert).show();
            throw new Exception(String.format("Field: %s invalid.", name));
        }
    }

    private void validateStringField(String name, String value) throws Exception {
        boolean result = (value != null && !"".equals(value.trim()));
        if (!result) {
            new AlertDialog.Builder(this)
                    .setTitle("Ooops!")
                    .setMessage(String.format("You haven't entered a valid %s!", name))
                    .setIcon(android.R.drawable.ic_dialog_alert).show();
            throw new Exception(String.format("Field: %s invalid.", name));
        }
    }


    public void onReset(View view) {
        stopTime = System.currentTimeMillis();
        timerStarted = false;
        timerHandler.removeCallbacks(startTimer);
        startStopButton.setEnabled(true);
        updateTimer(0);
        saveFab.hide();
    }

    Runnable startTimer = new Runnable() {
        @Override
        public void run() {
            long timerTime = System.currentTimeMillis() - startTime;
            updateTimer(timerTime);
            timerHandler.postDelayed(this, 10);
        }
    };

    private void updateTimer(long timerTime) {
        long seconds = timerTime / 1000;
        long milliS = (timerTime % 1000) / 10;
        String timerString;
        if (milliS < 10) {
            timerString = seconds + ":0" + milliS;
        } else {
            timerString = seconds + ":" + milliS;
        }
        timer.setText(timerString);
    }

    public void onSave(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Saving TimeCard")
                .setMessage("Do you really want to save this timecard?")
                .setIcon(android.R.drawable.ic_menu_save)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                        saveFab.hide();
                        Toast.makeText(TimerActivity.this, "Saving....", Toast.LENGTH_SHORT).show();
                        JSONObject JsonPayload = createPayload();
                        writeToFile(JsonPayload.toString(), OUTPUT_RESULTS_FILENAME);
                        new HttpRequestTask(JsonPayload.toString()).execute();
                    }
                })
                .setNegativeButton(android.R.string.no, null).show();
    }

    private JSONObject createPayload() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("layout", curentLayout);
            payload.put("startTime", startTime);
            payload.put("endTime", stopTime);
            payload.put("wrongTest", wrongTest.isChecked());
            payload.put("penalty", penaltyPicker.getValue());
            payload.put("carNumber", carNumber.getText());
            payload.put("marshalName", marshalName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return payload;
    }


    private class HttpRequestTask extends AsyncTask<String, String, String> {

        private Exception exception;
        private Activity currentActivity;
        private String payload;

        public HttpRequestTask(String inPayload) {
            payload = inPayload;
        }

        protected String doInBackground(String... urls) {
            try {
                return "" + testHttpPost();
            } catch (Exception e) {
                this.exception = e;
                Log.e(LOG_TAG, "network exception", e);
                return null;
            }
        }

        private int testHttpPost() throws IOException {
            URL url = new URL(host + "/result");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            OutputStream out = null;
            int responseCode = 0;
            String responseMessage;
            try {
                urlConnection.setDoOutput(true);
                urlConnection.setChunkedStreamingMode(0);

                out = new BufferedOutputStream(urlConnection.getOutputStream());

                out.write(payload.getBytes("UTF-8"));
                Log.e(LOG_TAG, "payload :" + payload);
                out.flush();

                responseMessage = urlConnection.getResponseMessage();
                responseCode = urlConnection.getResponseCode();

                Log.e(LOG_TAG, responseCode + ":" + responseMessage);

            } finally {
                urlConnection.disconnect();
            }
            return responseCode;
        }

        protected void onPostExecute(String result) {
            thisActivity.saveCallback(result, payload);
        }
    }

    private void saveCallback(String result, String payload) {
        Log.d(LOG_TAG, "Result from save callback: " + result);
        if (result == null || result.isEmpty() || result.charAt(0) != '2') {
            addToRetryQue(payload);
        } else {
            writeToFile(payload, OUTPUT_SYNC_STATUS_FILENAME);
            Toast.makeText(TimerActivity.this, "Saved!", Toast.LENGTH_SHORT).show();
        }
    }

    private void addToRetryQue(String payload) {
        retryQue.push(payload);
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    private File getFileForResultsStorage(String filenameSuffix) {
        // Get the directory for the user's public pictures directory.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_");
        File extStore = Environment.getExternalStorageDirectory();
        File file = new File(String.format("%s/Download/%s%s", extStore.getAbsolutePath(), sdf.format(new Date()), filenameSuffix));
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Log.d(LOG_TAG, String.format("Problem creating result file: %s", file.getName()), e);
            }
        }
        return file;
    }

    private void writeToFile(String result, String filenameSuffix) {
        if (isExternalStorageWritable()) {
            File file = getFileForResultsStorage(filenameSuffix);
            FileWriter fileWriter = null;
            try {
                Log.d(LOG_TAG, "Trying to write to file: " + file.getAbsolutePath());
                fileWriter = new FileWriter(file, true);
                fileWriter.write(result);
            } catch (IOException e) {
                Log.e(LOG_TAG, String.format("Something went wrong writing to the file: %s", file.getName()), e);
            } finally {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Something went wrong writing file", e);
                }
            }
        } else {
            Log.e(LOG_TAG, "External storage isn't writable...:-(");
        }
    }

    private List<String> readFromFile(File file) {
        List<String> result = new ArrayList<>();
        BufferedReader bufferedReader = null;
        if (isExternalStorageReadable()) {
            try {
                FileReader fileReader = new FileReader(file);
                bufferedReader = new BufferedReader(fileReader);
                for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
                    Log.d(LOG_TAG, "Read line from file: " + line);
                    result.add(line);
                }
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, "Problem reading results from file: " + file.getAbsolutePath(), e);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Problem reading results from file: " + file.getAbsolutePath(), e);
            } finally {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Problem closing file", e);
                }
            }
        }
        return result;
    }
}
