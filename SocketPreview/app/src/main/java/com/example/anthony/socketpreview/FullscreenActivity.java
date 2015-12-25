package com.example.anthony.socketpreview;

import com.example.anthony.socketpreview.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.List;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class FullscreenActivity extends Activity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;

    /*TODO Manually defined members start here*/
    /**
     * store a reference of the action bar here
     */
    private ActionBar action_bar = null;

    /**
     * store a reference of the main worker instance
     */
    private PreviewSocketSender mPreviewSocketSender = null;

    /**
     * store a reference of the surfaceview that we give to PreviewSocketSender
     */
    private SurfaceView mPreviewView = null;

    /**
     * Selected width and height to change to
     */
    int selected_width = 0;
    int selected_height = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // grab the action bar here, so that we can hide it later
        action_bar = getActionBar();
        setContentView(R.layout.activity_fullscreen);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        final View contentView = findViewById(R.id.fullscreen_content);

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                        // make action bar hide and show along with other UI widgets
                        if(visible) action_bar.show();
                        else    action_bar.hide();
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        //findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

        // TODO Here we create our main worker instance
        mPreviewView = (SurfaceView) contentView;
        mPreviewSocketSender = new PreviewSocketSender(mPreviewView){
            // Define how to tell user we are connected
            @Override
            public void notifyConnected() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                "Connected, start sending", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Define how to tell user we are disconnected
            @Override
            public void notifyDisconnected() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                "Disconnected", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void notifyChanges(String msg) {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        };

    }

    @Override
    protected void onPause(){
        super.onPause();
        if(mPreviewSocketSender != null)
            mPreviewSocketSender.pause();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(mPreviewSocketSender != null)
            mPreviewSocketSender.resume();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
        //return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            // TODO toggle connection
            case R.id.action_bar_send:
                //connection
                EditText mIpText = (EditText) findViewById(R.id.ip_edittext);
                EditText mPortText = (EditText) findViewById(R.id.port_editText);
                String ip = mIpText.getText().toString();
                int port = Integer.parseInt(mPortText.getText().toString());
                mPreviewSocketSender.setDestIp(ip);
                mPreviewSocketSender.setPort(port);
                if(mPreviewSocketSender != null) {
                    if(!mPreviewSocketSender.isSending()) {
                        if (!mPreviewSocketSender.connect()) {
                            //connection failed, tell user
                        }else{
                        }
                    } else {
                        mPreviewSocketSender.disconnect();
                    }
                }
                return true;
            // TODO toggle visibility of the preview on phone screen
            case R.id.action_video:
                //preview visible or not
                switch (mPreviewView.getVisibility()){
                    case View.VISIBLE:
                        mPreviewView.setVisibility(View.INVISIBLE);
                        break;
                    default:
                        mPreviewView.setVisibility(View.VISIBLE);
                        break;
                }
                return true;
            case R.id.action_bar_setting:
                //show the dialogue of setting frame size
                ArrayAdapter frameSizeAdapter = new ArrayAdapter<String>(this, R.layout.list_item, mPreviewSocketSender.getSupporttedSizes());

                AlertDialog.Builder frameSizeSettingDialogBuilder = new AlertDialog.Builder(this);
                //frameSizeSettingDialogBuilder.setMessage(R.string.frame_size_setting);
                frameSizeSettingDialogBuilder.setPositiveButton(R.string.ok_str, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(selected_width != 0 && selected_height != 0){
                            mPreviewSocketSender.setPreviewSize(selected_width, selected_height);
                        }
                    }
                });
                frameSizeSettingDialogBuilder.setNegativeButton(R.string.cancel_str, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selected_width = 0;
                        selected_height = 0;
                    }
                });
                frameSizeSettingDialogBuilder.setSingleChoiceItems(frameSizeAdapter, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //TODO cache the selected size
                        String size_str = mPreviewSocketSender.getSupporttedSizes()[which];
                        selected_width = Integer.parseInt(size_str.substring(0, size_str.indexOf("*")));
                        selected_height = Integer.parseInt(size_str.substring(size_str.indexOf("*")+1));
                    }
                });
                AlertDialog frameSizeSettingDialog = frameSizeSettingDialogBuilder.create();
                frameSizeSettingDialog.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
