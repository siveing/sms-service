package com.biktech.smsgateway.feature.message;

import com.biktech.smsgateway.common.ErrorCode;
import com.biktech.smsgateway.common.GatewayException;

import java.util.regex.Pattern;

/** Numbers in one format, and hidden in lists and logs. */
public final class PhoneNumbers {

    private static final Pattern INTERNATIONAL = Pattern.compile("\\+[1-9]\\d{7,14}");

    private PhoneNumbers() {
    }

    /** 012 345 678, 85512345678 and 0085512345678 all become +85512345678. */
    public static String international(String raw) {
        String number = raw.replaceAll("[\\s\\-().]", "");
        if (number.startsWith("00")) {
            number = "+" + number.substring(2);
        } else if (number.startsWith("0")) {
            number = "+855" + number.substring(1);
        } else if (!number.startsWith("+")) {
            number = "+" + number;
        }
        if (!INTERNATIONAL.matcher(number).matches()) {
            throw new GatewayException(ErrorCode.NUMBER_INVALID);
        }
        return number;
    }

    /** +85512345678 becomes +855•••678. */
    public static String masked(String number) {
        return number.length() <= 7 ? "•••" : number.substring(0, 4) + "•••" + number.substring(number.length() - 3);
    }

    /** The digits to look for in stored numbers: 012345678 looks for 12345678. */
    public static String searchDigits(String typed) {
        String digits = typed.replaceAll("\\D", "");
        return digits.startsWith("0") ? digits.substring(1) : digits;
    }
}
