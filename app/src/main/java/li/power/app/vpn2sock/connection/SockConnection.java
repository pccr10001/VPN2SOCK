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

    /**
     * Maximum packet size is constrained by the MTU, which is given as a signed short.
     */
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    /**
     * Time to wait in between losing the connection and retrying.
     */
    private static final long RECONNECT_WAIT_MS = TimeUnit.SECONDS.toMillis(3);
    /**
     * Time between keepalives if there is no traffic at the moment.
     * <p>
     * TODO: don't do this; it's much better to let the connection die and then reconnect when
     * necessary instead of keeping the network hardware up for hours on end in between.
     **/
    private static final long KEEPALIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15);
    /**
     * Time to wait without receiving any response before assuming the server is gone.
     */
    private static final long RECEIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);
    /**
     * Time between polling the VPN interface for new traffic, since it's non-blocking.
     * <p>
     * TODO: really don't do this; a blocking read on another thread is much cleaner.
     */
    private static final long IDLE_INTERVAL_MS = TimeUnit.MILLISECONDS.toMillis(100);
    /**
     * Number of periods of length {@IDLE_INTERVAL_MS} to wait before declaring the handshake a
     * complete and abject failure.
     * <p>
     * TODO: use a higher-level protocol; hand-rolling is a fun but pointless exercise.
     */
    private static final int MAX_HANDSHAKE_ATTEMPTS = 50;

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
    public void run(){
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
        Tun2socks.startSocks(packetFlow, mConfig.getHost(),mConfig.getPort());
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
        builder.addRoute("0.0.0.0",0);
        builder.addAllowedApplication(mPackages.iterator().next());
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

    public void disconnect(){
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
