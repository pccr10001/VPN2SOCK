package li.power.app.vpn2sock.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.VpnManager;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import li.power.app.vpn2sock.MainActivity;
import li.power.app.vpn2sock.connection.SockConnection;
import li.power.app.vpn2sock.model.ServerConfig;

public class S2VService extends VpnService {

    public final String TAG = this.getClass().getName();

    public static final String ACTION_CONNECT = "li.power.app.vpn2sock.action.START";
    public static final String ACTION_DISCONNECT = "li.power.app.vpn2sock.action.STOP";
    public static final String CHANNEL_ID = "";

    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>();
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();
    private AtomicInteger mNextConnectionId = new AtomicInteger(1);

    private PendingIntent mConfigureIntent;

    private static class Connection extends Pair<Thread, ParcelFileDescriptor> {
        public Connection(Thread thread, ParcelFileDescriptor pfd) {
            super(thread, pfd);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ServerConfig config = (ServerConfig) intent.getSerializableExtra("config");
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        startVpn(config);
        return super.onStartCommand(intent, flags, startId);
    }

    private void startVpn(ServerConfig config) {
        Set<String> packages = new HashSet<>();
//        packages.add("com.android.chrome");

        VpnService.prepare(this);
        Log.d(TAG, "Prepared");
        startConnection(new SockConnection(
                this, config, packages));
    }

    private void startConnection(final SockConnection connection) {
        final Thread thread = new Thread(connection);
        setConnectingThread(thread);
        connection.setOnEstablishListener(tunInterface -> {
            mConnectingThread.compareAndSet(thread, null);
            setConnection(new Connection(thread, tunInterface));
        });
        thread.start();
    }

    private void setConnectingThread(final Thread thread) {
        final Thread oldThread = mConnectingThread.getAndSet(thread);
        if (oldThread != null) {
            oldThread.interrupt();
        }
    }

    private void setConnection(final Connection connection) {
        final Connection oldConnection = mConnection.getAndSet(connection);
        if (oldConnection != null) {
            try {
                oldConnection.first.interrupt();
                oldConnection.second.close();
            } catch (IOException e) {
                Log.e(TAG, "Closing VPN interface", e);
            }
        }
    }

    private void disconnect() {
        setConnectingThread(null);
        setConnection(null);
        stopForeground(true);
    }

}
