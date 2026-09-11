package com.pablo.kwadfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DnsVpnService extends VpnService {

    public static final String ACTION_STOP = "com.pablo.kwadfilter.STOP";
    public static final String ACTION_RELOAD = "com.pablo.kwadfilter.RELOAD";
    public static final String PREFS = "cfg";
    public static volatile boolean RUNNING = false;

    private static final String VPN_ADDR = "10.111.222.2";
    private static final String VPN_DNS = "10.111.222.3";
    private static final String UPSTREAM = "1.1.1.1";

    private static final String[] ALLOW = { "events.mz.unity3d.com" };

    private static final String[] SOFT = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "admob.com", "adservice.google.com",
            "unityads.unity3d.com", "applovin.com", "applvn.com"
    };
    private static final String[] MED = {
            "ironsrc.com", "ironsource.mobi", "supersonicads.com",
            "vungle.com", "chartboost.com", "inmobi.com", "mopub.com",
            "mintegral.com", "mtgglobals.com", "pangle.io", "pangleglobal.com",
            "adcolony.com", "tapjoy.com", "fyber.com", "an.facebook.com",
            "moatads.com", "smaato.com", "startappservice.com", "startapp.com",
            "unrulymedia.com", "criteo.com", "pubmatic.com", "rubiconproject.com",
            "adsrvr.org", "casalemedia.com", "openx.net", "3lift.com",
            "adnxs.com", "teads.tv", "bidmachine.io", "adtrace.io"
    };
    private static final String[] AGGR = {
            "app-measurement.com", "google-analytics.com", "googletagmanager.com",
            "analytics.google.com", "appsflyer.com", "adjust.com", "adjust.io",
            "kochava.com", "branch.io", "amplitude.com", "segment.com", "segment.io",
            "mixpanel.com", "flurry.com", "onesignal.com", "braze.com",
            "clevertap.com", "singular.net", "tenjin.com", "bugsnag.com"
    };

    private ParcelFileDescriptor vpn;
    private Thread worker;
    private FileOutputStream out;
    private volatile String[] active = SOFT;
    private volatile InetAddress[] upstreams = new InetAddress[0];
    private boolean isForeground = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopVpn();
            if (isForeground) { stopForeground(true); isForeground = false; }
            stopSelf();
            return START_NOT_STICKY;
        }
        applyConfig();
        if (!RUNNING) startVpn();
        return START_STICKY;
    }

    private void applyConfig() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int level = p.getInt("level", 1);
        boolean notif = p.getBoolean("notif", true);
        active = buildList(level);
        if (notif && !isForeground) {
            startForeground(1, buildNotification(level));
            isForeground = true;
        } else if (!notif && isForeground) {
            stopForeground(true);
            isForeground = false;
        } else if (notif && isForeground) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(1, buildNotification(level));
        }
    }

    private String[] buildList(int level) {
        ArrayList<String> l = new ArrayList<>(Arrays.asList(SOFT));
        if (level >= 1) l.addAll(Arrays.asList(MED));
        if (level >= 2) l.addAll(Arrays.asList(AGGR));
        return l.toArray(new String[0]);
    }

    private Notification buildNotification(int level) {
        String ch = "adshield";
        String[] names = { getString(R.string.level_soft), getString(R.string.level_recommended), getString(R.string.level_aggressive) };
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(ch, "AdShield",
                    NotificationManager.IMPORTANCE_LOW));
        }
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                piFlags);
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, ch)
                : new Notification.Builder(this);
        b.setContentTitle(getString(R.string.notif_title));
        b.setContentText(getString(R.string.notif_text, names[Math.max(0, Math.min(2, level))]));
        b.setSmallIcon(android.R.drawable.ic_lock_idle_lock);
        b.setContentIntent(pi);
        b.setOngoing(true);
        return b.build();
    }

    private synchronized void startVpn() {
        if (RUNNING) return;
        try {
            Builder b = new Builder();
            b.setSession("AdShield");
            b.addAddress(VPN_ADDR, 32);
            b.addDnsServer(VPN_DNS);
            b.addRoute(VPN_DNS, 32);
            b.setMtu(1500);
            vpn = b.establish();
            if (vpn == null) { stopSelf(); return; }
            out = new FileOutputStream(vpn.getFileDescriptor());
            computeUpstreams();
            RUNNING = true;
            worker = new Thread(this::loop, "adshield-loop");
            worker.start();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private synchronized void stopVpn() {
        RUNNING = false;
        try { if (worker != null) worker.interrupt(); } catch (Exception ignore) {}
        try { if (vpn != null) vpn.close(); } catch (Exception ignore) {}
        vpn = null;
        worker = null;
    }

    @Override public void onDestroy() { stopVpn(); super.onDestroy(); }
    @Override public void onRevoke() { stopVpn(); stopSelf(); super.onRevoke(); }

    // Single-threaded. Waits with poll() until a packet is ready (no busy-spin), then reads.
    private void loop() {
        FileDescriptor fd = vpn.getFileDescriptor();
        FileInputStream in = new FileInputStream(fd);
        StructPollfd pfd = new StructPollfd();
        pfd.fd = fd;
        pfd.events = (short) OsConstants.POLLIN;
        StructPollfd[] fds = new StructPollfd[]{ pfd };
        byte[] buf = new byte[32767];
        while (RUNNING) {
            try {
                pfd.revents = 0;
                Os.poll(fds, -1);             // blocks (0% CPU) until the tun has data
            } catch (Exception e) {
                if (!RUNNING) break;
                continue;
            }
            if ((pfd.revents & OsConstants.POLLIN) == 0) continue;
            int len;
            try {
                len = in.read(buf);
            } catch (IOException e) {
                break;
            }
            if (len < 0) break;               // EOF: tun gone -> stop
            if (len < 28) continue;
            if ((buf[0] & 0xF0) >> 4 != 4) continue;   // IPv4
            if ((buf[9] & 0xFF) != 17) continue;       // UDP
            int ihl = (buf[0] & 0x0F) * 4;
            int dstPort = ((buf[ihl + 2] & 0xFF) << 8) | (buf[ihl + 3] & 0xFF);
            if (dstPort != 53) continue;               // DNS
            handleDns(buf, len);
        }
        // loop ended: make sure state is consistent
        if (RUNNING) {
            RUNNING = false;
            try { if (vpn != null) vpn.close(); } catch (Exception ignore) {}
            vpn = null;
            stopSelf();
        }
    }

    private void handleDns(byte[] pkt, int len) {
        try {
            int ihl = (pkt[0] & 0x0F) * 4;
            int udpLen = ((pkt[ihl + 4] & 0xFF) << 8) | (pkt[ihl + 5] & 0xFF);
            int dnsOff = ihl + 8;
            int dnsLen = udpLen - 8;
            if (dnsLen <= 12 || dnsOff + dnsLen > len) return;
            String host = parseQName(pkt, dnsOff, dnsLen);
            byte[] dns;
            if (shouldBlock(host)) {
                dns = buildNxdomain(pkt, dnsOff, dnsLen);
            } else {
                dns = forward(pkt, dnsOff, dnsLen);
                if (dns == null) return;
            }
            writeOut(buildIpUdp(pkt, ihl, dns));
        } catch (Exception ignore) {}
    }

    private String parseQName(byte[] p, int dnsOff, int dnsLen) {
        int i = dnsOff + 12;
        int end = dnsOff + dnsLen;
        StringBuilder sb = new StringBuilder();
        while (i < end) {
            int l = p[i] & 0xFF;
            if (l == 0) break;
            if ((l & 0xC0) != 0) break;
            i++;
            if (i + l > end) break;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(p, i, l));
            i += l;
        }
        return sb.toString().toLowerCase();
    }

    private boolean shouldBlock(String host) {
        if (host == null || host.isEmpty()) return false;
        for (String a : ALLOW) if (host.equals(a) || host.endsWith("." + a)) return false;
        String[] list = active;
        for (String b : list) if (host.equals(b) || host.endsWith("." + b)) return true;
        return false;
    }

    private byte[] buildNxdomain(byte[] p, int dnsOff, int dnsLen) {
        int i = dnsOff + 12;
        int end = dnsOff + dnsLen;
        while (i < end) {
            int l = p[i] & 0xFF;
            if (l == 0) { i++; break; }
            i += l + 1;
        }
        i += 4;
        int qLen = i - dnsOff;
        if (qLen < 12 || qLen > dnsLen) qLen = dnsLen;
        byte[] r = new byte[qLen];
        System.arraycopy(p, dnsOff, r, 0, qLen);
        r[2] = (byte) (0x80 | (p[dnsOff + 2] & 0x01));
        r[3] = (byte) 0x83;
        r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0;
        return r;
    }

    // Upstream DNS: the underlying network's own resolvers first, then 1.1.1.1 / 8.8.8.8.
    private void computeUpstreams() {
        ArrayList<InetAddress> r = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc == null) continue;
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;   // skip ours
                    if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                    LinkProperties lp = cm.getLinkProperties(n);
                    if (lp != null) for (InetAddress d : lp.getDnsServers()) if (!r.contains(d)) r.add(d);
                }
            }
        } catch (Exception ignore) {}
        try { r.add(InetAddress.getByName("1.1.1.1")); } catch (Exception ignore) {}
        try { r.add(InetAddress.getByName("8.8.8.8")); } catch (Exception ignore) {}
        upstreams = r.toArray(new InetAddress[0]);
    }

    private byte[] forward(byte[] p, int dnsOff, int dnsLen) {
        byte[] q = Arrays.copyOfRange(p, dnsOff, dnsOff + dnsLen);
        InetAddress[] ups = upstreams;
        if (ups.length == 0) { computeUpstreams(); ups = upstreams; }
        int tried = 0;
        for (InetAddress up : ups) {
            if (tried++ >= 3) break;
            DatagramSocket s = null;
            try {
                s = new DatagramSocket();
                protect(s);
                s.setSoTimeout(1500);
                s.send(new DatagramPacket(q, q.length, up, 53));
                byte[] rbuf = new byte[4096];
                DatagramPacket rp = new DatagramPacket(rbuf, rbuf.length);
                s.receive(rp);
                return Arrays.copyOf(rbuf, rp.getLength());
            } catch (Exception e) {
                // try next upstream
            } finally {
                if (s != null) s.close();
            }
        }
        computeUpstreams();   // refresh for next time (network may have changed)
        return null;
    }

    private byte[] buildIpUdp(byte[] orig, int ihl, byte[] dns) {
        int total = 20 + 8 + dns.length;
        byte[] o = new byte[total];
        o[0] = 0x45;
        o[2] = (byte) ((total >> 8) & 0xFF);
        o[3] = (byte) (total & 0xFF);
        o[8] = 64;
        o[9] = 17;
        System.arraycopy(orig, 16, o, 12, 4);
        System.arraycopy(orig, 12, o, 16, 4);
        int cks = checksum(o, 0, 20);
        o[10] = (byte) ((cks >> 8) & 0xFF);
        o[11] = (byte) (cks & 0xFF);
        int u = 20;
        o[u] = 0; o[u + 1] = 53;
        o[u + 2] = orig[ihl];
        o[u + 3] = orig[ihl + 1];
        int ulen = 8 + dns.length;
        o[u + 4] = (byte) ((ulen >> 8) & 0xFF);
        o[u + 5] = (byte) (ulen & 0xFF);
        o[u + 6] = 0; o[u + 7] = 0;
        System.arraycopy(dns, 0, o, u + 8, dns.length);
        return o;
    }

    private int checksum(byte[] b, int off, int len) {
        int sum = 0;
        for (int i = 0; i < len; i += 2) {
            int word = ((b[off + i] & 0xFF) << 8) | (i + 1 < len ? (b[off + i + 1] & 0xFF) : 0);
            sum += word;
            sum = (sum & 0xFFFF) + (sum >>> 16);
        }
        return (~sum) & 0xFFFF;
    }

    private synchronized void writeOut(byte[] data) {
        try { if (out != null) { out.write(data); out.flush(); } } catch (IOException ignore) {}
    }
}
