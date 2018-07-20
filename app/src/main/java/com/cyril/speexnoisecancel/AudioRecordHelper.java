package com.cyril.speexnoisecancel;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * @author : jinlongfeng
 * @version : V
 * @description:
 * @time : 2018/7/20-08:39
 */
public class AudioRecordHelper {

    private static final String TAG = "AudioRecordHelper";

    //RawAudioName裸音频数据文件
    private static final String RawAudioName = "/sdcard/ss/record_speex.raw";
    //WaveAudioName可播放的音频文件
    private static final String WaveAudioName = "/sdcard/ss/record_speex.wav";
    // 设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    private static int sampleRateInHz = 44100;
    // 设置音频的录制的声道CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
    private static int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    // 音频数据格式:PCM 16位每个样本。保证设备支持。PCM 8位每个样本。不一定能得到设备支持。
    private static int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    boolean isRecording = false;
    private int audioSource = MediaRecorder.AudioSource.MIC;
    // 缓冲区字节大小
    private int bufferSizeInBytes = 0;
    private AudioRecord audioRecord;

    public AudioRecordHelper(Context context) {
        mContext = context;
    }

    private Context mContext;

    private void startRecord() {
        if (audioRecord == null) {
            creatAudioRecord();
        }

        audioRecord.startRecording();
        // 让录制状态为true
        isRecording = true;
        // 开启音频文件写入线程
        new Thread(new AudioRecordThread()).start();
    }


    public void startRecordTask(){
        startRecord();
    }

    public  void stopRecordTask(){
        stopRecord();
    }

    private void stopRecord() {
        isRecording = false;//停止文件写入
    }

    private void creatAudioRecord() {
        // 获得缓冲区字节大小
        bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz,
                channelConfig, audioFormat);
        // 创建AudioRecord对象
        audioRecord = new AudioRecord(audioSource, sampleRateInHz,
                channelConfig, audioFormat, bufferSizeInBytes);
    }

    private void writeDateTOFile() {
        // new一个byte数组用来存一些字节数据，大小为缓冲区大小
        byte[] audiodata = new byte[bufferSizeInBytes];
        FileOutputStream fos = null;
        int readsize = 0;
        try {
            File file = new File(RawAudioName);
            if (file.exists()) {
                file.delete();
            }
            fos = new FileOutputStream(file);// 建立一个可存取字节的文件
        } catch (Exception e) {
            e.printStackTrace();
        }
        while (isRecording == true) {
            readsize = audioRecord.read(audiodata, 0, bufferSizeInBytes);

            if (AudioRecord.ERROR_INVALID_OPERATION != readsize) {

                try {
                    fos.write(audiodata);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            fos.close();// 关闭写入流
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWav(final String rawFileName, final String wavFileName) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                int longSampleRate = 44100;
                int channels = 1;// TODO:声道数一定要通过检测得到！！！！！
                long byteRate = 16 * 44100 * channels / 8;

                FileChannel fcin = null;
                FileChannel fcout = null;
                ByteBuffer buffer = null;

                long totalPCMLength = 0;
                long totalDataLength = totalPCMLength + 36;
                int bufferSize;

                bufferSize = AudioRecord.getMinBufferSize(longSampleRate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                buffer = ByteBuffer.allocateDirect(bufferSize * 2).order(ByteOrder.nativeOrder());

                long startTime = System.currentTimeMillis();
                try {
                    fcin = new RandomAccessFile(rawFileName, "r").getChannel();
                    fcout = new RandomAccessFile(wavFileName, "rws").getChannel();

                    // 写入头信息
                    totalPCMLength = fcin.size();
                    totalDataLength = totalPCMLength + 36;
                    byte[] header = generateWaveFileHeader(totalPCMLength, totalDataLength, longSampleRate, channels,
                            byteRate);
                    buffer.clear();
                    buffer.put(header);
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        fcout.write(buffer);
                    }

                    buffer.clear();
                    // 写入数据部分
                    while (fcin.read(buffer) != -1) {
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            fcout.write(buffer);
                        }
                        buffer.clear();
                    }

                    try {
                        fcout.force(true);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }

                    if (fcout != null) {
                        try {
                            fcout.close();// 关闭写入流
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        fcout = null;
                    }

                    if (fcin != null) {
                        try {
                            fcin.close();// 关闭写入流
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        fcin = null;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                long stopTime = System.currentTimeMillis();

                if (buffer != null) {
                    buffer.clear();
                    buffer = null;
                }
            }
        }).start();
    }

    private byte[] generateWaveFileHeader(long totalPCMLength, long totalDataLength, long longSampleRate, int channels,
                                          long byteRate) {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLength & 0xff);// 从下个地址开始到文件尾的总字节数
        header[5] = (byte) ((totalDataLength >> 8) & 0xff);
        header[6] = (byte) ((totalDataLength >> 16) & 0xff);
        header[7] = (byte) ((totalDataLength >> 24) & 0xff);
        header[8] = 'W';// WAV文件标志（WAVE）
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1 // 2 bytes:格式种类（值为1时，表示数据为线性PCM编码）
        header[21] = 0;
        header[22] = (byte) channels;// 2 bytes:通道数，单声道为1，双声道为2
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);// 采样频率
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);// 波形数据传输速率（每秒平均字节数）
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8); // block align //DATA数据块长度，字节。　
        header[33] = 0;
        header[34] = 16; // bits per sample //PCM位宽 TODO:根据配置
        header[35] = 0;
        header[36] = 'd';// 数据标志符（data）
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalPCMLength & 0xff);// DATA总数据长度字节
        header[41] = (byte) ((totalPCMLength >> 8) & 0xff);
        header[42] = (byte) ((totalPCMLength >> 16) & 0xff);
        header[43] = (byte) ((totalPCMLength >> 24) & 0xff);
        return header;
    }

    private void close() {
        if (audioRecord != null) {
            System.out.println("stopRecord");
            audioRecord.stop();
            audioRecord.release();//释放资源
            audioRecord = null;
        }
    }

    class AudioRecordThread implements Runnable {
        @Override
        public void run() {
            writeDateTOFile();//往文件中写入裸数据
            close();
            WriteWav(RawAudioName, WaveAudioName);//给裸数据加上头文件


           // Toast.makeText(mContext, "录制完成", Toast.LENGTH_SHORT).show();

            Log.d(TAG,"录制完成");

        }
    }
}
