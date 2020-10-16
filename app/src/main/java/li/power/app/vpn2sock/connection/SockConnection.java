package li.power.app.vpn2sock.connection;

import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import li.power.app.vpn2sock.model.ServerConfig;
import li.power.app.vpn2sock.services.S2VService;
import tun2socks.PacketFlow;
import tun2socks.Tun2socks;

public class SockConnection implements Runnable {

    public final String TAG = this.getClass().getName();

    private final S2VService mService;
    private final ServerConfig mConfig;
    private OnEstablishListener mOnEstablishListener;
    private final Set<String> mPackages;
    private boolean stopped = false;

    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    private static final long IDLE_INTERVAL_MS = TimeUnit.MILLISECONDS.toMillis(100);

    public interface OnEstablishListener {
        void onEstablish(ParcelFileDescriptor tunInterface);
    }

    class MyPacketFlow implements PacketFlow {

        private FileOutputStream fos;

        @Override
        public void writePacket(byte[] packet) {
            try {
                fos.write(packet);
            } catch (IOException e) {
                Log.e(TAG, "Write to VPN client failed");
                e.printStackTrace();
            }
        }

        public MyPacketFlow(FileOutputStream fos) {
            this.fos = fos;
        }
    }

    @Override
    public void run() {
        try {
            connect();
        } catch (IOException | PackageManager.NameNotFoundException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void connect() throws IOException, InterruptedException, IllegalArgumentException, PackageManager.NameNotFoundException {
        ParcelFileDescriptor iface = configure(mConfig);
        Log.d(TAG, "VPN Created");
        FileInputStream input = new FileInputStream(iface.getFileDescriptor());
        FileOutputStream output = new FileOutputStream(iface.getFileDescriptor());
        PacketFlow packetFlow = new MyPacketFlow(output);

        ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_SIZE);
        Tun2socks.startSocks(packetFlow, mConfig.getHost(), mConfig.getPort());
        Log.d(TAG, "Socks5 Created");
        while (!stopped) {
            boolean idle = true;
            int length = input.read(packet.array());
            if (length > 0) {
                packet.limit(length);
                Tun2socks.inputPacket(packet.array());
                packet.clear();
                idle = false;
            }
            if (idle) {
                Thread.sleep(IDLE_INTERVAL_MS);
            }
        }
    }

    private ParcelFileDescriptor configure(ServerConfig config) throws IllegalArgumentException, PackageManager.NameNotFoundException {
        VpnService.Builder builder = mService.new Builder();
        builder.setMtu(1500);
        builder.addAddress("10.87.0.2", 24);
        builder.addRoute("0.0.0.0", 0);
        for (String p : mPackages) {
            builder.addAllowedApplication(p);
        }
        final ParcelFileDescriptor vpnInterface;
        builder.setSession(config.getHost());
        synchronized (mService) {
            vpnInterface = builder.establish();
            if (mOnEstablishListener != null) {
                mOnEstablishListener.onEstablish(vpnInterface);
            }
        }
        Log.i(TAG, "New interface: " + vpnInterface);
        return vpnInterface;
    }

    public void setOnEstablishListener(OnEstablishListener listener) {
        mOnEstablishListener = listener;
    }

    public void disconnect() {
        stopped = true;
    }

    public SockConnection(final S2VService service,
                          final ServerConfig config,
                          final Set<String> packages) {
        mService = service;
        mConfig = config;
        mPackages = packages;
    }
}
