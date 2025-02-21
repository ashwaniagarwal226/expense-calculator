package com.expense.calculator;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

public class SimpleHTTPServer extends NanoHTTPD {

    private static final String VIDEO_DIRECTORY = "/Users/ashwiniagarwal/Pictures/";

    public SimpleHTTPServer() throws IOException {
        super(8080);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("Server started at http://localhost:8080/");
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String filename = params.get("filename");

        if (filename != null) {
            File videoFile = new File(VIDEO_DIRECTORY + filename);
            if (videoFile.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(videoFile);
                    return newChunkedResponse(Response.Status.OK, "video/mp4", fis);
                } catch (IOException e) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error serving file");
                }
            } else {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
            }
        }

        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing filename parameter");
    }

    public static void main(String[] args) {
        try {
            new SimpleHTTPServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
