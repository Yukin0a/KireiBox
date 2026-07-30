package com.yuki.yukihub.tyrano;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class TyranoLocalHttpServer implements Runnable {
    private static final String TAG = "YukiTyrano";
    private final File root;
    private final AsarArchive asar;
    private final byte[] tyranoHook;
    private final ServerSocket serverSocket;
    private volatile boolean running = true;
    private final Thread thread;
    private final ThreadPoolExecutor clients = new ThreadPoolExecutor(
            2, 8, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), runnable -> {
                Thread client = new Thread(runnable, "YukiTyranoHttpClient");
                client.setDaemon(true);
                return client;
            }, new ThreadPoolExecutor.AbortPolicy());

    private static final class ResolvedFile {
        final File file;
        final byte[] data;
        ResolvedFile(File file, byte[] data) {
            this.file = file;
            this.data = data;
        }
    }

    TyranoLocalHttpServer(File root, byte[] tyranoHook) throws Exception {
        this(root, null, tyranoHook);
    }

    TyranoLocalHttpServer(File root, AsarArchive asar, byte[] tyranoHook) throws Exception {
        this.root = root.getCanonicalFile();
        this.asar = asar;
        this.tyranoHook = tyranoHook == null ? new byte[0] : tyranoHook;
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        this.thread = new Thread(this, "YukiTyranoLocalHttpServer");
        this.thread.setDaemon(true);
    }

    void start() { thread.start(); }
    int getPort() { return serverSocket.getLocalPort(); }
    void stop() {
        running = false;
        try { serverSocket.close(); } catch (Throwable ignored) { }
        clients.shutdownNow();
    }

    @Override
    public void run() {
        Log.i(TAG, "local server started port=" + getPort() + " root=" + root);
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                try { clients.execute(() -> handle(socket)); }
                catch (java.util.concurrent.RejectedExecutionException rejected) { close(socket); }
            } catch (Throwable t) {
                if (running) Log.w(TAG, "server accept failed", t);
            }
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(15000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.length() == 0) { close(socket); return; }
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && line.length() > 0) {
                int idx = line.indexOf(':');
                if (idx > 0) headers.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT), line.substring(idx + 1).trim());
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) { sendText(socket, 400, "Bad Request", "bad request"); return; }
            String method = parts[0];
            String uri = parts[1];
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                sendText(socket, 405, "Method Not Allowed", "method not allowed");
                return;
            }
            int q = uri.indexOf('?');
            if (q >= 0) uri = uri.substring(0, q);
            uri = URLDecoder.decode(uri, "UTF-8");
            if (uri.equals("/")) uri = "/index.html";
            while (uri.startsWith("/")) uri = uri.substring(1);
            ResolvedFile resolved = resolveRequestedFile(uri);
            if (resolved == null || (resolved.file == null && resolved.data == null)) {
                sendText(socket, 404, "Not Found", "not found: " + uri);
                return;
            }
            if (resolved.data != null) {
                if (isIndexHtml(uri)) {
                    sendInjectedIndex(socket, resolved.data, "HEAD".equalsIgnoreCase(method));
                } else {
                    sendBytes(socket, resolved.data, uri, "HEAD".equalsIgnoreCase(method));
                }
                return;
            }
            if (isIndexHtml(uri, resolved.file)) {
                sendInjectedIndex(socket, resolved.file, "HEAD".equalsIgnoreCase(method));
                return;
            }
            sendFile(socket, resolved.file, headers.get("range"), "HEAD".equalsIgnoreCase(method));
        } catch (Throwable t) {
            try { sendText(socket, 500, "Internal Server Error", "server error"); } catch (Throwable ignored) { }
            Log.w(TAG, "handle request failed", t);
        } finally {
            close(socket);
        }
    }

    private ResolvedFile resolveRequestedFile(String uri) throws Exception {
        File target = canonicalIfValid(uri);
        if (target != null) return new ResolvedFile(target, null);

        String lower = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m4a")) {
            String alt = replaceSuffix(uri, ".m4a", ".ogg");
            target = canonicalIfValid(alt);
            if (target != null) {
                Log.i(TAG, "resource fallback m4a->ogg " + uri + " -> " + alt);
                return new ResolvedFile(target, null);
            }
        }
        if (lower.endsWith(".rpgmvm")) {
            String alt = replaceSuffix(uri, ".rpgmvm", ".rpgmvo");
            target = canonicalIfValid(alt);
            if (target != null) {
                Log.i(TAG, "resource fallback rpgmvm->rpgmvo " + uri + " -> " + alt);
                return new ResolvedFile(target, null);
            }
        }
        if (asar != null) {
            byte[] data = asar.read(uri);
            if (data != null) return new ResolvedFile(null, data);
            if ("index.html".equalsIgnoreCase(uri) || "index.htm".equalsIgnoreCase(uri)) {
                byte[] indexBytes = asar.read("index.html");
                if (indexBytes == null) indexBytes = asar.read("www/index.html");
                if (indexBytes == null) indexBytes = asar.read("app/index.html");
                if (indexBytes == null) indexBytes = asar.read("resources/app/index.html");
                if (indexBytes != null) return new ResolvedFile(null, indexBytes);
            }
        }
        return new ResolvedFile(resolveCaseInsensitive(uri), null);
    }

    private File canonicalIfValid(String uri) throws Exception {
        if (uri == null || uri.contains("\u0000")) return null;
        File target = new File(root, uri).getCanonicalFile();
        if (!isInsideRoot(target) || !target.isFile()) return null;
        return target;
    }

    private String replaceSuffix(String value, String oldSuffix, String newSuffix) {
        if (value == null) return null;
        return value.substring(0, value.length() - oldSuffix.length()) + newSuffix;
    }

    private File resolveCaseInsensitive(String uri) throws Exception {
        if (uri == null || uri.length() == 0 || uri.contains("..")) return null;
        String[] parts = uri.split("/");
        File current = root;
        for (String part : parts) {
            if (part.length() == 0) continue;
            File exact = new File(current, part);
            if (exact.exists()) {
                current = exact;
                continue;
            }
            File[] children = current.listFiles();
            if (children == null) return null;
            File matched = null;
            for (File child : children) {
                if (child.getName().equalsIgnoreCase(part)) {
                    matched = child;
                    break;
                }
            }
            if (matched == null) return null;
            current = matched;
        }
        File target = current.getCanonicalFile();
        if (!isInsideRoot(target) || !target.isFile()) return null;
        Log.i(TAG, "resource fallback case-insensitive " + uri + " -> " + target.getPath());
        return target;
    }

    private boolean isInsideRoot(File target) {
        if (target == null) return false;
        String rootPath = root.getPath();
        String targetPath = target.getPath();
        return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
    }

    private boolean isIndexHtml(String uri, File target) {
        if (target == null) return isIndexHtml(uri);
        String name = target.getName() == null ? "" : target.getName().toLowerCase(Locale.ROOT);
        String path = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        return ("index.html".equals(name) || "index.htm".equals(name)) && (path.endsWith("index.html") || path.endsWith("index.htm"));
    }

    private boolean isIndexHtml(String uri) {
        if (uri == null) return false;
        String path = uri.toLowerCase(Locale.ROOT);
        return path.endsWith("index.html") || path.endsWith("index.htm");
    }

    private void sendInjectedIndex(Socket socket, File file, boolean headOnly) throws Exception {
        sendInjectedIndex(socket, readTextFile(file), headOnly);
    }

    private void sendInjectedIndex(Socket socket, byte[] htmlBytes, boolean headOnly) throws Exception {
        sendInjectedIndex(socket, new String(htmlBytes == null ? new byte[0] : htmlBytes, StandardCharsets.UTF_8), headOnly);
    }

    private void sendInjectedIndex(Socket socket, String html, boolean headOnly) throws Exception {
        String htmlText = html == null ? "" : html;
        String script = tyranoHook.length > 0 ? new String(tyranoHook, StandardCharsets.UTF_8) : fallbackTyranoHook();
        String injected = "\n<script type='text/javascript'>\n" + script + "\n</script>\n";
        String lower = htmlText.toLowerCase(Locale.ROOT);
        int pos = lower.indexOf("</head>");
        if (pos >= 0) {
            htmlText = htmlText.substring(0, pos) + injected + htmlText.substring(pos);
        } else {
            htmlText = injected + htmlText;
        }
        byte[] data = htmlText.getBytes(StandardCharsets.UTF_8);
        Log.i(TAG, "served injected index bytes=" + data.length + " hook=" + tyranoHook.length);
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-cache\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: " + data.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        if (!headOnly) out.write(data);
        out.flush();
    }

    private String readTextFile(File file) throws Exception {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int read;
        try {
            while ((read = in.read(buf)) >= 0) out.write(buf, 0, read);
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private String fallbackTyranoHook() {
        return "var _tyrano_player={pauseAllAudio:function(){try{var b=TYRANO.kag.tmp.map_bgm;for(var k in b)b[k].pause();var s=TYRANO.kag.tmp.map_se;for(var k2 in s)s[k2].pause();}catch(e){}},resumeAllAudio:function(){try{var b=TYRANO.kag.tmp.map_bgm;if(b[TYRANO.kag.stat.current_bgm])b[TYRANO.kag.stat.current_bgm].play();else if(b[0])b[0].play();}catch(e){}}};"
                + "window.tyrano_save=window.tyrano_save||{};if(window.$){$.setStorage=function(key,val,type){if('appJsInterface' in window){appJsInterface.setStorage(key,escape(JSON.stringify(val)));}else{window.tyrano_save[key]=encodeURIComponent(JSON.stringify(val));location.href='tyranoplayer-save://?key='+key+'&data='+encodeURIComponent(JSON.stringify(val));}};$.getStorage=function(key,type){if('appJsInterface' in window){try{var s=appJsInterface.getStorage(key);return s==''?null:unescape(s);}catch(e){return null;}}else{if(!window.tyrano_save[key]||window.tyrano_save[key]==''){return null;}return decodeURIComponent(window.tyrano_save[key]);}};$.openWebFromApp=function(url){if('appJsInterface' in window){appJsInterface.openUrl(url);}else{location.href='tyranoplayer-web://?url='+url;}};}";
    }

    private void sendFile(Socket socket, File file, String rangeHeader, boolean headOnly) throws Exception {
        if (file == null) {
            sendText(socket, 404, "Not Found", "file missing");
            return;
        }
        long fileLen = file.length();
        long start = 0;
        long end = fileLen - 1;
        boolean partial = false;
        if (rangeHeader != null && rangeHeader.toLowerCase(Locale.ROOT).startsWith("bytes=")) {
            String range = rangeHeader.substring(6).trim();
            int dash = range.indexOf('-');
            if (dash >= 0) {
                String a = range.substring(0, dash).trim();
                String b = range.substring(dash + 1).trim();
                if (a.length() > 0) start = Long.parseLong(a);
                if (b.length() > 0) end = Long.parseLong(b);
                if (end >= fileLen) end = fileLen - 1;
                if (start < 0) start = 0;
                if (start <= end) partial = true;
            }
        }
        long len = Math.max(0, end - start + 1);
        String status = partial ? "206 Partial Content" : "200 OK";
        OutputStream raw = new BufferedOutputStream(socket.getOutputStream());
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(status).append("\r\n");
        h.append("Accept-Ranges: bytes\r\n");
        h.append("Content-Type: ").append(mime(file.getName())).append("\r\n");
        h.append("Cache-Control: no-cache\r\n");
        h.append("Access-Control-Allow-Origin: *\r\n");
        h.append("Content-Length: ").append(len).append("\r\n");
        if (partial) h.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(fileLen).append("\r\n");
        h.append("Connection: close\r\n\r\n");
        raw.write(h.toString().getBytes(StandardCharsets.UTF_8));
        if (!headOnly) {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
            try {
                long skipped = 0;
                while (skipped < start) {
                    long s = in.skip(start - skipped);
                    if (s <= 0) break;
                    skipped += s;
                }
                byte[] buf = new byte[64 * 1024];
                long left = len;
                while (left > 0) {
                    int read = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (read < 0) break;
                    raw.write(buf, 0, read);
                    left -= read;
                }
            } finally {
                try { in.close(); } catch (Throwable ignored) { }
            }
        }
        raw.flush();
    }

    private void sendBytes(Socket socket, byte[] data, String uri, boolean headOnly) throws Exception {
        if (data == null) {
            sendText(socket, 404, "Not Found", "data missing");
            return;
        }
        byte[] body = data;
        OutputStream raw = new BufferedOutputStream(socket.getOutputStream());
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 200 OK\r\n");
        h.append("Content-Type: ").append(mime(uri)).append("\r\n");
        h.append("Cache-Control: no-cache\r\n");
        h.append("Access-Control-Allow-Origin: *\r\n");
        h.append("Content-Length: ").append(body.length).append("\r\n");
        h.append("Connection: close\r\n\r\n");
        raw.write(h.toString().getBytes(StandardCharsets.UTF_8));
        if (!headOnly) raw.write(body);
        raw.flush();
    }

    private void sendText(Socket socket, int code, String reason, String body) throws Exception {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        out.write(("HTTP/1.1 " + code + " " + reason + "\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-cache\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: " + data.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.flush();
    }

    private static void close(Socket socket) { try { socket.close(); } catch (Throwable ignored) { } }

    private static String mime(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".ogg")) return "audio/ogg";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".aac")) return "audio/aac";
        if (n.endsWith(".flac")) return "audio/flac";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".m4v")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".ttf")) return "font/ttf";
        if (n.endsWith(".otf")) return "font/otf";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".xml")) return "application/xml; charset=utf-8";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}

