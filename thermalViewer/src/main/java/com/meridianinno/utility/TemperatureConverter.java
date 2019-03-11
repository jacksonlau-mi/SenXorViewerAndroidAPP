package com.meridianinno.utility;

public class TemperatureConverter {

    // Source: https://www.rapidtables.com/convert/temperature
    public static double C_to_F(double tempC) { return tempC * 9.0/5.0 + 32; }

    public static double C_to_K(double tempC) {
        return tempC + 273.15;
    }

    public static double F_to_C(double tempF) {
        return (tempF - 32) * 5.0/9.0;
    }

    public static double F_to_K(double tempF) {
        return (tempF + 459.67) * 5.0/9.0;
    }

    public static double K_to_C(double tempK) {
        return tempK - 273.15;
    }

    public static double K_to_F(double tempK) {
        return tempK * 9.0/5.0 - 459.67;
    }
}
