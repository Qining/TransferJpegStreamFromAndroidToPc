package com.example.anthony.socketpreview;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.hardware.Camera;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;

/**
 * Created by anthony on 04/03/2015.
 */
public abstract class PreviewSocketSender implements SurfaceHolder.Callback {
    private static final String TAG = "PreviewSocketSender";
    // The camera to use
    private Camera mCamera = null;
    // The camera index
    private int mCameraIndex = 0;
    // No Default IP
    private String mDestIp = null;
    // Default port: 9889
    private int mPort = 9889;
    // Default timeout threshold for connection
    private int mConnectionTimeout = 3000;
    private Socket mSocket = null;
    private OutputStream mSocketOutputStream = null;
    // Single thread executor to synchronize image sending task
    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    // Default FPS
    private int mFps = 10;
    private SurfaceHolder mSurfaceHolder = null;
    // Default size for both preview and surface view
    private int mWidth = 0;
    private int mHeight = 0;
    // FPS range supported by the camera, refreshed in contructor
    private int mMinFps = 5000;
    private int mMaxFps = 30000;
    // Default JPEG quality
    private int mQuality = 50;
    // Flag to tell if we should synchronize preview and sending task (may not working)
    private boolean mSyncSendingFlag = true;
    // State indicator
    private boolean mIsSending = false;
    // State indicator
    private boolean mIsPreviewing = false;
    // Lock for processing preview data
    private ReentrantLock mPreviewLock = new ReentrantLock();
    // store the context of the UI thread (should be removed later)

    /**
     * Create PreviewSocketSender instance
     * @param surface_view: The surfaceview that hold the preview of the camera
     */
    public PreviewSocketSender(SurfaceView surface_view){
        mSurfaceHolder = surface_view.getHolder();
        mSurfaceHolder.addCallback(this);
        int num_of_cameras = Camera.getNumberOfCameras();
        if(num_of_cameras < 1){
            //print a toast to user to notify that no camera is found
        }else {
            mCameraIndex = num_of_cameras - 1;
            mCamera = Camera.open(mCameraIndex);
            mMinFps = mCamera.getParameters().getSupportedPreviewFpsRange().get(0)[0];
            mMaxFps = mCamera.getParameters().getSupportedPreviewFpsRange().get(0)[1];
        }
    }

    /**
     * Find the best size with provided width and height
     * @param width
     * @param height
     */
    private void setBestSize(int width, int height){
        List<Camera.Size> supportedSizes = mCamera.getParameters().getSupportedPreviewSizes();
        Camera.Size bestSize = supportedSizes.remove(0);
        for (Camera.Size size : supportedSizes) {
            if (size.width <= width && size.height <=height &&
                    (size.width * size.height) > (bestSize.width * bestSize.height)) {
                bestSize = size;
            }
        }
        mWidth = bestSize.width;
        mHeight = bestSize.height;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    /**
     * We need to configure our camera, preview and buffer whenever surface changed.
     * This will be called after onResume of the Activity.
     * @param holder
     * @param format
     * @param width
     * @param height
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(mWidth==0 || mHeight==0){
            setBestSize(width, height);
        }
        if(initializeCameraPreview()) {
            mCamera.startPreview();
            mIsPreviewing = true;
        }
        if(mIsSending){
            PixelFormat pixelFormat = new PixelFormat();
            PixelFormat.getPixelFormatInfo(ImageFormat.NV21, pixelFormat);
            int bufSize = mWidth * mHeight * pixelFormat.bitsPerPixel / 8;
            mCamera.stopPreview();
            mIsPreviewing = false;
            byte[] buffer = null;
            buffer = new byte[ bufSize ];
            mCamera.addCallbackBuffer(buffer);
            mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
            mCamera.startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    /**
     * Call back for camera preview, called whenever a frame is ready
     */
    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback(){
        private final String TAG = "mPreviewCallback";

        /**
         * For each incoming frame, prepare the data to transfer.
         * Use SocketSendingTask, a AsyncTask, to transfer image packet
         * @param data: data from camera
         * @param camera
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if(!mIsSending || mSocket==null) return;
            mPreviewLock.lock();
            YuvImage im = new YuvImage(data, ImageFormat.NV21, mWidth,
                    mHeight, null);
            Rect r = new Rect(0, 0, mWidth, mHeight);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            im.compressToJpeg(r, mQuality, stream);
            Log.d(TAG, "size of JPEG image:" + im.getWidth() + " * " + im.getHeight());

            // Add two Integer before JPEG data to tell the width and height (Not Necessary at all!)
            byte[] picSizeHeader = new byte[]{(byte) ((mWidth>>24)&0xff), (byte) ((mWidth>>16)&0xff),
                    (byte) ((mWidth>>8)&0xff), (byte) ((mWidth>>0)&0xff),
                    (byte) ((mHeight>>24)&0xff), (byte) ((mHeight>>16)&0xff),
                    (byte) ((mHeight>>8)&0xff), (byte) ((mHeight>>0)&0xff)};
            // Add an Integer at the first place to tell the size of following data in this packet
            // Note the size of picSizeHeader is included
            int packetSize = stream.size() + picSizeHeader.length;
            Log.i(TAG, "packetSize="+packetSize);
            byte[] packetSizeHeader = new byte[]{(byte) ((packetSize>>24)&0xff), (byte) ((packetSize>>16)&0xff),
                    (byte) ((packetSize>>8)&0xff), (byte) ((packetSize>>0)&0xff)};
            // Complete the packet to transfer
            byte[] jpeg_data = concat(packetSizeHeader, picSizeHeader, stream.toByteArray());

            SocketSendingTask t = new SocketSendingTask();
            t.executeOnExecutor(mExecutor, jpeg_data);
            // Shall we wait for the success of socket sending?
            if(mSyncSendingFlag){
                try {
                    t.get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            // Add back buffer
            camera.addCallbackBuffer(data);
            mPreviewLock.unlock();
        }
    };

    /**
     * AsyncTask to send packet through socket.
     * The socket and output stream should be already setup in connect()
     */
    private class SocketSendingTask extends AsyncTask<byte[], Void, String>{
        private final String TAG = "SocketSendingTask";
        private OutputStream out;
        @Override
        protected String doInBackground(byte[]... params) {
            if(!mIsSending || mSocket==null || !mSocket.isConnected() || mSocketOutputStream == null)
                return null;

            try {
                mSocketOutputStream.write(params[0]);
            } catch (IOException e) {
                Log.e(TAG, "IOException in out.write(): doInBackground(), disconnect() will be called");
                disconnect();
                e.printStackTrace();
            } catch (RuntimeException e){
                Log.e(TAG, "RuntimeException in out.write(): doInBackground(), disconnect() will be called");
                disconnect();
                e.printStackTrace();
            }

            try {
                mSocketOutputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "IOException in out.flush(): doInBackground(), disconnect() will be called");
                disconnect();
                e.printStackTrace();
            } catch (RuntimeException e){
                Log.e(TAG, "RuntimeException in out.flush(): doInBackground(), disconnect() will be called");
                disconnect();
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Stop preview and release camera, but do no disconnect
     */
    public void pause(){
        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mIsPreviewing = false;
        }
    }

    /**
     * Resume preview (here we just get the camera,
     * but callback: surfaceChanged() will start the preview for us
     */
    public void resume(){
        if(mCamera == null)
            mCamera = Camera.open(mCameraIndex);
    }

    public void togglePreview(){
        if(mIsPreviewing){
            pause();
        }else{
            resume();
        }
    }

    public String[] getSupporttedSizes(){
        List<Camera.Size> raw_sizes =  mCamera.getParameters().getSupportedPreviewSizes();
        String[] ret = new String[raw_sizes.size()-1];
        int i = 0;
        raw_sizes.remove(0);
        for(Camera.Size size: raw_sizes){
            String str = Integer.toString(size.width) + "*" + Integer.toString(size.height);
            ret[i] = str;
            i += 1;
        }
        Log.i(TAG, "Number of supported sizes:"+ret.length);
        return ret;
    }

    public void setPreviewSize(int width, int height){
        if(width != mWidth || height != mHeight) {
            if(mIsPreviewing){
                mCamera.stopPreview();
            }
            mWidth = width;
            mHeight = height;
            initializeCameraPreview();
            if(mIsPreviewing){
                mCamera.startPreview();
            }
            notifyChanges("preview width set to " + mWidth + " height set to " + mHeight);
        }
    }

    public int[] getPreviewSize(){
        int[] size = new int[2];
        size[0] = mWidth;
        size[1] = mHeight;
        return size;
    }

    public int[] getFpsRange(){
        int[] fps_range = new int[2];
        fps_range[0] = mMinFps;
        fps_range[1] = mMaxFps;
        return fps_range;
    }

    public int setFps(int fps){
        mFps = fps > mMaxFps ? mMaxFps : (fps < mMinFps ? mMinFps : fps);

        return mFps;
    }

    public boolean getSyncSendingFlag(){
        return mSyncSendingFlag;
    }

    public void setSyncSendingFlag(boolean flag){
        mSyncSendingFlag = flag;
    }

    public void setDestIp(String ip){
        mDestIp = ip;
    }

    public String getDestIp(){
        return this.mDestIp;
    }

    public void setPort(int port){
        this.mPort = port;
    }

    public int getPort(){
        return this.mPort;
    }

    public void setQuality(int q){
        mQuality = q;
    }

    public int getQuality(){
        return mQuality;
    }

    public void changeCamera(){
        mCameraIndex = (mCameraIndex+1) % Camera.getNumberOfCameras();
        if(mCamera!=null){
            mCamera.stopPreview();
            mCamera.release();
            mIsPreviewing = false;
        }
        initializeCameraPreview();
        mCamera.startPreview();
        mIsPreviewing = true;
    }

    /**
     * Setup camera parameters, should be call whenever camera changes or may change
     * @return succeeded or not
     */
    private boolean initializeCameraPreview(){
        Log.d(TAG, "initializeCameraPreview() called with mWidth="+mWidth+" mHeight="+mHeight);
        getCamera();

        Camera.Parameters p = mCamera.getParameters();
        p.setPreviewSize(mWidth, mHeight);
        mSurfaceHolder.setFixedSize(p.getPreviewSize().width, p.getPreviewSize().height);
        Log.d(TAG, "initializeCameraPreview() mCamera width=" + p.getPreviewSize().width + " height=" + p.getPreviewSize().height);
        p.setPreviewFpsRange(mFps, (mFps + 1) > mMaxFps ? mMaxFps : (mFps + 1));
        Log.d(TAG, "initializeCameraPreview() mFps=" + mFps);
        mCamera.setParameters(p);

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (IOException e) {
            Log.e(TAG, "Error in initializeCameraPreivew: mCamera.setPreviewDisplay()");
            e.printStackTrace();
        }
        return true;
    }

    /**
     * AsyncTask to setup socket connection, should be used in connect()
     */
    private class SocketConnectionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            mSocket = new Socket();
            try {
                mSocket.connect(new InetSocketAddress(mDestIp, mPort), mConnectionTimeout);
            } catch (SocketTimeoutException e){
                Log.e(TAG, "connect() timeout!");
                mSocket = null;
                // show a toast to user to notify the failure of connection
                notifyChanges("Connection Timout");
            } catch (IOException e) {
                Log.e(TAG, "failed in connect()!");
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Connect to a socket server at certain address and port
     * @return succeeded or not
     */
    public boolean connect(){
        if(mSocket != null) {
            Log.d(TAG, "Already connected, disconnect first: connect()");
            disconnect();
        }
        SocketConnectionTask connection = new SocketConnectionTask();
        connection.executeOnExecutor(mExecutor);
        try {
            connection.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        if(mSocket == null || !mSocket.isConnected()){
            //print something to tell user this failure.
            mSocket = null;
            notifyChanges("Connection Failed");
            return false;
        }
        try {
            mSocketOutputStream = mSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        PixelFormat pixelFormat = new PixelFormat();
        PixelFormat.getPixelFormatInfo(ImageFormat.NV21, pixelFormat);
        int bufSize = mWidth * mHeight * pixelFormat.bitsPerPixel / 8;
        mCamera.stopPreview();
        mIsPreviewing = false;
        byte[] buffer = null;
        buffer = new byte[ bufSize ];
        mCamera.addCallbackBuffer(buffer);
        mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
        mCamera.startPreview();
        mIsSending = true;
        mIsPreviewing = true;
        notifyConnected();
        return true;
    }

    /**
     * Disconnect with server
     * @return succeeded or not
     */
    public boolean disconnect(){
        if(mSocketOutputStream != null){
            try {
                mSocketOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocketOutputStream = null;
        }
        if(mSocket == null){
            Log.d(TAG, "Already disconnected: disconnect()");
            return true;
        }
        try {
            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "failed in disconnect()");
            e.printStackTrace();
        }
        mSocket = null;
        mIsSending = false;
        notifyDisconnected();
        return true;
    }

    /**
     * Check if we are sending images, if the connection has be setup
     * @return mIsSending
     */
    public boolean isSending(){
        return mIsSending;
    }

    private void getCamera(){
        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mIsPreviewing = false;
        }
        mCamera = Camera.open(mCameraIndex);
    }

    private byte[] concat(byte[]... args) {
        int fulllength = 0;
        for (byte[] arrItem : args) {
            fulllength += arrItem.length;
        }

        byte[] retArray = new byte[fulllength];
        int start = 0;
        for (byte[] arrItem : args) {
            System.arraycopy(arrItem, 0, retArray, start, arrItem.length);
            start += arrItem.length;
        }
        return retArray;
    }


    public abstract void notifyConnected();

    public abstract void notifyDisconnected();

    public abstract void notifyChanges(String msg);
}
