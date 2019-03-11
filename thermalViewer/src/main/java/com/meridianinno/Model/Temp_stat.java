package com.meridianinno.Model;

public class Temp_stat {
    private float min;
    private float max;
    private double stdev;


    public Temp_stat() {
        this.min = 9999;
        this.max = 0;
        this.stdev = 0;
    }

    public Temp_stat(float min, float max, double stdev) {
        this.min = min;
        this.max = max;
        this.stdev = stdev;
    }

    public Temp_stat(Temp_stat src) {
        this.min = src.getMin();
        this.max = src.getMax();
        this.stdev = src.getStdev();
    }

    public float getMax() {
        return max;
    }

    public float getMin() {
        return min;
    }

    public double getStdev() {
        return stdev;
    }

    public void setMax(float max) {
        this.max = max;
    }

    public void setMin(float min) {
        this.min = min;
    }

    public void setStdev(double stdev) {
        this.stdev = stdev;
    }

    public void reset() {
        this.min = 9999;
        this.max = 0;
        this.stdev = 0;
    }

    public static Temp_stat calculateStat(int numArray[])
    {
        Temp_stat result = new Temp_stat();
        float sum = 0.0f;
        double standardDeviation = 0.0;
        int length = numArray.length;

        for(float num : numArray) {
            sum += num;
            if(num > result.getMax()) result.setMax(num);
            if(num < result.getMin()) result.setMin(num);
        }

        double mean = sum/length;

        for(double num: numArray) {
            standardDeviation += Math.pow(num - mean, 2);
        }

        result.setStdev(Math.sqrt(standardDeviation/length));
        return result;
    }
}
