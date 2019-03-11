package com.meridianinno.utility;

import static java.lang.Double.NaN;

public class ConversionHelper {

    private String mC, mF, mK; // reduce string look-ups by using member variables

    public ConversionHelper(String strC, String strF, String strK) {
        mC = strC;
        mF = strF;
        mK = strK;
    }

    public double convertTemperatureByUnitString(double temp, String oldUnit, String newUnit) {
        // Return same temperature if the units are the same
        if (oldUnit.equals(newUnit))
            return temp;

        // Convert otherwise
        if (oldUnit.equals(mC)) {
            if (newUnit.equals(mF)) {
                return TemperatureConverter.C_to_F(temp);
            } else if (newUnit.equals(mK)) {
                return TemperatureConverter.C_to_K(temp);
            }
        }
        else if (oldUnit.equals(mF)) {
            if (newUnit.equals(mC)) {
                return TemperatureConverter.F_to_C(temp);
            } else if (newUnit.equals(mK)) {
                return TemperatureConverter.F_to_K(temp);
            }
        }
        else if (oldUnit.equals(mK)) {
            if (newUnit.equals(mC)) {
                return TemperatureConverter.K_to_C(temp);
            } else if (newUnit.equals(mF)) {
                return TemperatureConverter.K_to_F(temp);
            }
        }
        // returns NaN if the input unit strings are not recognized
        return NaN;
    }
}
