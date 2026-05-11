package com.capstoneecho.echo_back.pronunciation.tts.support;

import java.util.Locale;

public interface TtsClient {

    byte[] synthesize(String text, Locale locale);
}
