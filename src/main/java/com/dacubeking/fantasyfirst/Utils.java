package com.dacubeking.fantasyfirst;

public final class Utils {


    public static String getSlackIdFromMention(String mention) {
        return mention.substring(2, mention.indexOf('|'));
    }
}
