package tw.com.sungfeng.thingslantern;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.MediaPlayer;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends Activity {
    // Use this string for the first part of the practical (load local media
    // from the raw directory).
    // private static final String VIDEO_SAMPLE = "tacoma_narrows";

    // Use this string for part 2 (load media from the internet).
    private static final String VIDEO_SAMPLE =
            "https://developers.google.com/training/images/tacoma_narrows.mp4";
    // Use this string for part 2 (load media from the internet).
    private static final String VIDEO_SAMPLE_01 = "video01.mp4";
    private static final String VIDEO_SAMPLE_02 = "video02.mp4";
    private static final String TAG = MainActivity.class.getSimpleName();
    private Boolean mCoinVideoPlaying = Boolean.FALSE;
    private Boolean mRegVideoPlaying = Boolean.FALSE;
    private Gpio mButtonGpio;
    private Gpio mButtonSubGpio;

    private VideoView mVideoView;
    private TextView mBufferingTextView;

    // Current playback position (in milliseconds).
    private int mCurrentPosition = 0;
    private UsbManager mUsbManager;
    private AudioManager mAudioManager;
    // Tag for the instance state bundle.
    private static final String PLAYBACK_TIME = "play_time";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVideoView = findViewById(R.id.videoView);
        mBufferingTextView = findViewById(R.id.buffering_textview);

        if (savedInstanceState != null) {
            mCurrentPosition = savedInstanceState.getInt(PLAYBACK_TIME);
        }

        // Set up the media controller widget and attach it to the video view.
        MediaController controller = new MediaController(this);
        controller.setMediaPlayer(mVideoView);
        mVideoView.setMediaController(controller);
        mVideoView.setMediaController(null);
        gpioControl();
        gpioSubControl();

        mUsbManager = getSystemService(UsbManager.class);

        // Detach events are sent as a system-wide broadcast
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        handleIntent(getIntent());
        try {
            listUsb();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    printStatus(getString(R.string.status_removed));
                    printDeviceDescription(device);
                }
            }
        }
    };

    private void printDeviceDescription(UsbDevice device) {
        String result = UsbHelper.readDevice(device) + "\n\n";
        printResult(result);
    }

    private void handleIntent(Intent intent) {
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
            printStatus(getString(R.string.status_added));
            printDeviceDetails(device);
        } else {
            // List all devices connected to USB host on startup
            printStatus(getString(R.string.status_list));
            printDeviceList();
        }
    }

    private void printDeviceDetails(UsbDevice device) {
        UsbDeviceConnection connection = mUsbManager.openDevice(device);

        String deviceString = "";
        try {
            //Parse the raw device descriptor
            deviceString = DeviceDescriptor.fromDeviceConnection(connection)
                    .toString();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid device descriptor", e);
        }

        String configString = "";
        try {
            //Parse the raw configuration descriptor
            configString = ConfigurationDescriptor.fromDeviceConnection(connection)
                    .toString();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid config descriptor", e);
        } catch (ParseException e) {
            Log.w(TAG, "Unable to parse config descriptor", e);
        }

        printResult(deviceString + "\n\n" + configString);
        connection.close();
    }

    /**
     * Print the list of currently visible USB devices.
     */
    private void printDeviceList() {
        HashMap<String, UsbDevice> connectedDevices = mUsbManager.getDeviceList();

        if (connectedDevices.isEmpty()) {
            printResult("No Devices Currently Connected");
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append("Connected Device Count: ");
            builder.append(connectedDevices.size());
            builder.append("\n\n");
            for (UsbDevice device : connectedDevices.values()) {
                //Use the last device detected (if multiple) to open
                builder.append(UsbHelper.readDevice(device));
                builder.append("\n\n");
            }
            printResult(builder.toString());
        }
    }

    private void printStatus(String status) {
        Log.i(TAG, status);
    }

    private void printResult(String result) {
        Log.i(TAG, result);
    }

    private void listUsb() throws IOException {
        Log.i(TAG, "list usb devices...");

        /*
        Runtime.getRuntime().exec("mkdir /mnt/usb\n");

        Runtime.getRuntime().exec("mount -t vfat -o rw /dev/block/sdb1 /mnt/usb\n");

        Runtime.getRuntime().exec("ls /mnt/usb");
        /*/
    }

    private void setVolume(){
        mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        int media_current_volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int media_max_volume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        Log.i(TAG, "b:max volume="+media_max_volume+"current volume="+media_current_volume);
        // Set media volume level
        mAudioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC, // Stream type
                media_max_volume, // Index
                AudioManager.FLAG_SHOW_UI // Flags
        );
        media_current_volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.i(TAG, "a:max volume="+media_max_volume+"current volume="+media_current_volume);
    }

    private void enbalePlayStateCheck(){

        Timer timer = new Timer();
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "play state checking...");
                        if(mCoinVideoPlaying == Boolean.FALSE && mRegVideoPlaying == Boolean.FALSE) {
                            initializePlayer(Boolean.FALSE);
                        }
                    }
                });
            }
        }, 0, 1000);
    }

    private void gpioControl(){
        try {
            String pinName = BoardDefault.getGPIOForButton();
            mButtonGpio = PeripheralManager.getInstance().openGpio(pinName);
            mButtonGpio.setDirection(Gpio.DIRECTION_IN);
            mButtonGpio.setEdgeTriggerType(Gpio.EDGE_FALLING);
            mButtonGpio.registerGpioCallback(new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio) {
                    Log.i(TAG, "GPIO changed, button pressed");
                    if( mCoinVideoPlaying == Boolean.FALSE){
                        Log.i(TAG, "Play coin video");
                        playCoinVideo();
                        mCoinVideoPlaying = Boolean.TRUE;
                    }
                    // Return true to continue listening to events
                    return true;
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    private void gpioSubControl(){
        try {
            String pinName = "BCM20";//BoardDefault.getGPIOForButton();
            mButtonSubGpio = PeripheralManager.getInstance().openGpio(pinName);
            mButtonSubGpio.setDirection(Gpio.DIRECTION_IN);
            mButtonSubGpio.setEdgeTriggerType(Gpio.EDGE_FALLING);
            mButtonSubGpio.registerGpioCallback(new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio) {
                    Log.i(TAG, "Sub GPIO changed, button pressed");
                    if( mCoinVideoPlaying == Boolean.FALSE){
                        Log.i(TAG, "Play coin video");
                        playCoinVideo();
                        mCoinVideoPlaying = Boolean.TRUE;
                    }
                    // Return true to continue listening to events
                    return true;
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Load the media each time onStart() is called.
        initializePlayer(Boolean.FALSE);
        enbalePlayStateCheck();
        setVolume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Media playback takes a lot of resources, so everything should be
        // stopped and released at this time.
        releasePlayer();
    }
    private void releasePlayer() {
        mVideoView.stopPlayback();
        mRegVideoPlaying = Boolean.FALSE;
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mButtonGpio != null) {
            // Close the Gpio pin
            Log.i(TAG, "Closing Button GPIO pin");
            try {
                mButtonGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            } finally {
                mButtonGpio = null;
            }
        }
        if (mButtonSubGpio != null) {
            // Close the Gpio pin
            Log.i(TAG, "Closing Button Sub GPIO pin");
            try {
                mButtonSubGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            } finally {
                mButtonSubGpio = null;
            }
        }
    }

    private void playCoinVideo(){
        initializePlayer(Boolean.TRUE);
    }
    private void initializePlayer(final Boolean isCoinVideo) {
        // Show the "Buffering..." message while the video loads.
        mBufferingTextView.setVisibility(VideoView.VISIBLE);
        mRegVideoPlaying = Boolean.TRUE;
        // Buffer and decode the video sample.
        Uri videoUri = getMedia(VIDEO_SAMPLE_01);
        if(isCoinVideo == Boolean.TRUE){
            videoUri = getMedia(VIDEO_SAMPLE_02);
        }
        Log.i(TAG, "video Uri = "+videoUri);
        mVideoView.setVideoURI(videoUri);

        // Listener for onPrepared() event (runs after the media is prepared).
        mVideoView.setOnPreparedListener(
                new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {

                        // Hide buffering message.
                        mBufferingTextView.setVisibility(VideoView.INVISIBLE);

                        // Restore saved position, if available.
                        if (mCurrentPosition > 0) {
                            mVideoView.seekTo(mCurrentPosition);
                        } else {
                            // Skipping to 1 shows the first frame of the video.
                            mVideoView.seekTo(1);
                        }

                        // Start playing!
                        mVideoView.start();
                    }
                });

        // Listener for onCompletion() event (runs after media has finished
        // playing).
        mVideoView.setOnCompletionListener(
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        //Toast.makeText(MainActivity.this,                                R.string.toast_message,                                Toast.LENGTH_SHORT).show();

                        // Return the video position to the start.
                        mVideoView.seekTo(0);
                        //initializePlayer(Boolean.FALSE);
                        Log.i(TAG, "regular video is playing , again.");
                        mCoinVideoPlaying = Boolean.FALSE;
                        mRegVideoPlaying = Boolean.FALSE;
                    }
                });
    }
    // Get a Uri for the media sample regardless of whether that sample is
    // embedded in the app resources or available on the internet.
    private Uri getMedia(String mediaName) {
        String fileName = null;
        if(mediaName == VIDEO_SAMPLE_01){
            fileName = "android.resource://" + getPackageName() + "/" + R.raw.video01;
        } else{
            fileName = "android.resource://" + getPackageName() + "/" + R.raw.video02;
        }
        return Uri.parse(fileName);
        /*
        if (URLUtil.isValidUrl(mediaName)) {
            // Media name is an external URL.
            return Uri.parse(mediaName);
        } else {
            // Media name is a raw resource embedded in the app.
            return Uri.parse(fileName);
        }
        */
    }
}
