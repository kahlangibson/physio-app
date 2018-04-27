/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.app;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.app.help.HelpOption;
import com.mbientlab.metawear.app.help.HelpOptionAdapter;
import com.mbientlab.metawear.data.EulerAngles;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.SensorFusionBosch;
import com.mbientlab.metawear.module.SensorFusionBosch.AccRange;
import com.mbientlab.metawear.module.SensorFusionBosch.GyroRange;
import com.mbientlab.metawear.module.SensorFusionBosch.Mode;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.content.Intent;
//import android.support.v7.app.AppCompatActivity;
//import android.os.Bundle;
//import android.widget.TextView;
import com.mbientlab.metawear.app.InitialPeriodicMotionDetector;

import org.w3c.dom.Text;

/**
 * Created by kahlan on 01/24/2018.
 */

public class PatientFragment extends PatientFragmentBase {

    private int[] colors = {Color.rgb(255,255,255),Color.rgb(255,85,85)};
    private String[] labels = {"Detected Reps", "Movement Data"};
    private static final float SAMPLING_PERIOD = 1 / 200f;
    private final Handler chartHandler= new Handler();
    private long prevUpdate1 = -1;

    private boolean rep = false;

    private boolean done = false;

    //this are new definitions added by Janelle
    //private int index = 0; //used to index the circular arrays
    private int capacity = 1024; //this is the maximum number of entries we will have in the circular arrays
    int Fs = 100; //the sampling frequency
    private final ArrayList<Entry> x0 = new ArrayList<>(capacity), x1 = new ArrayList<>(capacity), x2 = new ArrayList<>(capacity), x3 = new ArrayList<>(capacity);
    private SensorFusionBosch sensorFusion;

    Filtration filtration = new Filtration();
//    InitialPeriodicMotionDetector initDetector = new InitialPeriodicMotionDetector(Fs, capacity);
    //ADD CLASS HERE

    public float pitch, roll, yaw;

    public PatientFragment() {
        super(R.string.navigation_fragment_patient, R.layout.fragment_patientdata, -1f, 1f);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMaxValue(400f);
        leftAxis.setAxisMinValue(-400f);

        refreshChart(false);
        for (int i=0;i<capacity;i++){
            pitch_data.add(0.0);
            roll_data.add(0.0);
            yaw_data.add(0.0);
        }
    }

    @Override
    protected void setup() {
        sensorFusion.configure()
                .mode(Mode.NDOF)
                .accRange(AccRange.AR_16G)
                .gyroRange(GyroRange.GR_2000DPS)
                .commit();

        sensorFusion.eulerAngles().addRouteAsync(source -> source.stream((data, env) -> {

            final EulerAngles angles = data.value(EulerAngles.class);

            //store each angle as the first entry in the array and flip data
            pitch = angles.pitch(); //this should return the current pitch as a float
            roll = angles.roll();
            yaw = angles.yaw();

            dataUpdate();

        })).continueWith(task -> {
            streamRoute = task.getResult();
            sensorFusion.eulerAngles().start();
            sensorFusion.start();

            return null;
        });
    }

    @Override
    protected void clean() {
        if (sensorFusion != null) {
            sensorFusion.stop();
        }
    }

    @Override
    protected void resetData(boolean clearData) {
        if (clearData) {
            sampleCount = 0;
            chartXValues.clear();
            x0.clear();
            x1.clear();
            x2.clear();
            x3.clear();
        }

        ArrayList<LineDataSet> spinAxisData = new ArrayList<>();

        spinAxisData.add(new LineDataSet(x0, "Reps"));
        spinAxisData.get(0).setCircleColor(Color.rgb(255,255,255));
        spinAxisData.get(0).setColor(Color.rgb(68,71,90));
        spinAxisData.get(0).setDrawCircles(true);

        spinAxisData.add(new LineDataSet(x1, "Movement"));
        spinAxisData.get(1).setColor(Color.rgb(255,85,85));
        spinAxisData.get(1).setDrawCircles(false);

//        spinAxisData.add(new LineDataSet(x2, "Periodic"));
//        spinAxisData.get(1).setColor(Color.rgb(80, 250, 123));
//        spinAxisData.get(1).setDrawCircles(false);


        LineData data = new LineData(chartXValues);
        for (LineDataSet set : spinAxisData) {
            data.addDataSet(set);
        }
        data.setDrawValues(false);
        chart.setData(data);
        chart.getLegend().setCustom(colors, labels);
    }

    @Override
    protected void boardReady() throws UnsupportedModuleException {
        sensorFusion = mwBoard.getModuleOrThrow(SensorFusionBosch.class);
        ledModule = mwBoard.getModuleOrThrow(Led.class);
    }

    @Override
    protected void fillHelpOptionAdapter(HelpOptionAdapter adapter) {
        adapter.add(new HelpOption(R.string.config_name_sensor_fusion_data, R.string.config_desc_sensor_fusion_data));
    }

    protected void dataUpdate() {
        LineData chartData = chart.getData();
        long current = System.currentTimeMillis();
        float data;
        double p = pitch;
        double r = roll;
        double y = yaw;
        pitch_data.add(0, p);
        roll_data.add(0,r);
        yaw_data.add(0,y);
        if (pitch_data.size() > capacity){
            pitch_data.remove(capacity);
        }
        if (roll_data.size() > capacity) {
            roll_data.remove(capacity);
        }
        if (yaw_data.size() > capacity) {
            yaw_data.remove(capacity);
        }

        pitch_data = filtration.Filter(pitch_data, capacity, "pitch");
        roll_data = filtration.Filter(roll_data, capacity, "roll");
        yaw_data = filtration.Filter(yaw_data, capacity, "yaw");

        isPeriodic = motion.isPeriodic(pitch_data, roll_data, yaw_data);
        freqtext = (float) motion.getfreq();
        motionError = motion.isMotionError();
        toofast = motion.isToofast();
        double oldRepCount = numReps;
        int resetCal = motion.getResetCalib();
        boolean calibDone = motion.calibrationComplete;
        numReps = motion.getRepCount();
        if(numReps >= repsInSet){
            done = true;
        }
        else
            done = false;
       // if(motion.getfreq() != 0)
        //System.out.println("Cali Complete? " + motion.percentThreshold() + " Cali Complete " + motion.getfreq());
        int chosenAngle = motion.getChosenAngle();
        if((oldRepCount != numReps) || resetCal == 1)
            rep = true;

        //System.out.println("Old Rep Count: "+ oldRepCount + " New Rep Count " + numReps + " Rep " + rep + " Reset Calib " + resetCal);

        percentMotion = motion.percentThreshold();

        current = System.currentTimeMillis();
        if (prevUpdate1 == -1 || (current - prevUpdate1) >= 200 || rep) {
            prevUpdate1 = current;

            // THIS IS A SWITCH TO SEE WHICH AXIS WE'RE TRACKING
            if(chosenAngle == 1) {
                data = roll_data.get(0).floatValue();
            }
            else if(chosenAngle == 2) {
                data = yaw_data.get(0).floatValue();
            }
            else {
                data = pitch_data.get(0).floatValue();
            }

            // THIS IS WHAT WAS WORKING IN THE LAST DEMO, do not touch
            chartData.addXValue(String.format(Locale.US, "%.2f", sampleCount * SAMPLING_PERIOD));
            // 0 if periodic, 1 elsewise
            chartData.getDataSetByIndex(1).resetColors();
            if (isPeriodic) {
                chartData.getDataSetByIndex(1).addColor(Color.rgb(80, 250, 123));
                colors[1] = Color.rgb(80, 250, 123);
                chart.getLegend().setCustom(colors, labels);
            }
            else {
                chartData.getDataSetByIndex(1).addColor(Color.rgb(255,85,85));
                colors[1] = Color.rgb(255,85,85);
                chart.getLegend().setCustom(colors, labels);
            }
            chartData.addEntry(new Entry(data, sampleCount), 1);
            ledModule.stop(true);
            if (rep) {
                chartData.addEntry(new Entry(data, sampleCount), 0);
                if(calibDone)
                    ledModule.editPattern(Led.Color.GREEN);
                if(numReps == repsInSet)
                    ledModule.editPattern(Led.Color.BLUE);
                rep = false;
            }
            if(motionError)
                ledModule.editPattern(Led.Color.RED);
            chart.getData().notifyDataChanged();
            chart.notifyDataSetChanged();

            moveViewToLast();
            sampleCount++;
        }
    }
    private void moveViewToLast() {
        chart.setVisibleXRangeMinimum(100);
        chart.setVisibleXRangeMaximum(100);
        chart.moveViewToX(Math.max(0f, chartXValues.size() - 1));
    }
    private void configureChannel(Led.PatternEditor editor) {
        final short PULSE_WIDTH= 1000;
        editor.highIntensity((byte) 31).lowIntensity((byte) 31)
                .highTime((short) (PULSE_WIDTH >> 1)).pulseDuration(PULSE_WIDTH)
                .repeatCount((byte) -1).commit();
    }

}
