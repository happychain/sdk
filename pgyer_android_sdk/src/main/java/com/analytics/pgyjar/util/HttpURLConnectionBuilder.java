package com.analytics.pgyjar.util;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by liuqiang 2020-10-23 .
 */
public class HttpURLConnectionBuilder {

    public static final String DEFAULT_CHARSET = "UTF-8";
    private final String mUrlString;
    private String mRequestMethod;
    private String mRequestBody;
    private PgyMultipartEntity mMultipartEntity;
    private int mTimeout = 1500;

    private final Map<String, String> mHeaders;

    public HttpURLConnectionBuilder(String urlString) {
        mUrlString = urlString;
        mHeaders = new HashMap<String, String>();
    }

    public HttpURLConnectionBuilder setRequestMethod(String requestMethod) {
        mRequestMethod = requestMethod;
        return this;
    }

    public HttpURLConnectionBuilder setRequestBody(String requestBody) {
        mRequestBody = requestBody;
        return this;
    }

    public HttpURLConnectionBuilder writeFormFields(Map<String, String> fields) {
        try {
            String formString = getFormString(fields, DEFAULT_CHARSET);
            setHeader("Content-Type", "application/x-www-form-urlencoded");
            setRequestBody(formString);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public HttpURLConnectionBuilder writeMultipartData(Map<String, String> fields, Context context, File voiceFile, List<Uri> attachmentUris) {
        try {
            mMultipartEntity = new PgyMultipartEntity();
            mMultipartEntity.writeFirstBoundaryIfNeeds();

            for (String key : fields.keySet()) {
                mMultipartEntity.addPart(key, fields.get(key));
            }

            /** Write files */
            if (voiceFile != null) {
                InputStream inputVoice = new FileInputStream(
                        voiceFile.getAbsolutePath());
                mMultipartEntity.addPart("voice", voiceFile.getName(), inputVoice,
                        "audio/wav", false);
            }

            if (attachmentUris != null) {
                for (int i = 0; i < attachmentUris.size(); i++) {
                    Uri attachmentUri = attachmentUris.get(i);
                    boolean lastFile = (i == attachmentUris.size() - 1);
                    InputStream input = context.getContentResolver().openInputStream(attachmentUri);
                    String filename = attachmentUri.getLastPathSegment();
                    mMultipartEntity.addPart("image[]",
                            filename,
                            input, lastFile);
                }
            }

            mMultipartEntity.writeLastBoundaryIfNeeds();

            setHeader("Content-Type", "multipart/form-data; boundary=" + mMultipartEntity.getBoundary());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

    public HttpURLConnectionBuilder setTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout has to be positive.");
        }
        mTimeout = timeout;
        return this;
    }

    public HttpURLConnectionBuilder setHeader(String name, String value) {
        mHeaders.put(name, value);
        return this;
    }

    public HttpURLConnectionBuilder setBasicAuthorization(String username, String password) {
        String authString = "Basic " + Base64.encodeToString(
                (username + ":" + password).getBytes(), android.util.Base64.NO_WRAP);

        setHeader("Authorization", authString);
        return this;
    }

    public HttpURLConnection build() {

        HttpURLConnection connection;
        try {
            URL url = new URL(mUrlString);
            connection = (HttpURLConnection) url.openConnection();

            connection.setConnectTimeout(mTimeout);
            connection.setReadTimeout(mTimeout);

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD) {
                connection.setRequestProperty("Connection", "close");
            }

            if (!TextUtils.isEmpty(mRequestMethod)) {
                connection.setRequestMethod(mRequestMethod);
                if (!TextUtils.isEmpty(mRequestBody) || mRequestMethod.equalsIgnoreCase("POST") || mRequestMethod.equalsIgnoreCase("PUT")) {
                    connection.setDoOutput(true);
                }
            }

            for (String name : mHeaders.keySet()) {
                connection.setRequestProperty(name, mHeaders.get(name));
            }

            if (!TextUtils.isEmpty(mRequestBody)) {
                OutputStream outputStream = connection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, DEFAULT_CHARSET));
                writer.write(mRequestBody);
                writer.flush();
                writer.close();
            }

            if (mMultipartEntity != null) {
                connection.setRequestProperty("Content-Length", String.valueOf(mMultipartEntity.getContentLength()));
                BufferedOutputStream outputStream = new BufferedOutputStream(connection.getOutputStream());
                outputStream.write(mMultipartEntity.getOutputStream().toByteArray());
                outputStream.flush();
                outputStream.close();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);

        } catch (Exception e) {
            throw new RuntimeException(e);

        }
        return connection;
    }

    private static String getFormString(Map<String, String> params, String charset) throws UnsupportedEncodingException {
        List<String> protoList = new ArrayList<String>();
        for (String key : params.keySet()) {
            String value = params.get(key);
            key = URLEncoder.encode(key, charset);
            value = URLEncoder.encode(value, charset);
            protoList.add(key + "=" + value);
        }
        return TextUtils.join("&", protoList);
    }
}