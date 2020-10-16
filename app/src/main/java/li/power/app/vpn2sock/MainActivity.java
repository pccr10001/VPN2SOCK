package li.power.app.vpn2sock;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import li.power.app.vpn2sock.databinding.ActivityMainBinding;
import li.power.app.vpn2sock.model.ServerConfig;
import li.power.app.vpn2sock.services.S2VService;

public class MainActivity extends AppCompatActivity {

    ServerConfig config = new ServerConfig();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(view -> connect(null));
    }

    public void connect(View view) {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }
    @Override
    protected void onActivityResult(int request, int result, Intent data) {

        if (result == RESULT_OK) {
            startService(getServiceIntent());
        }
        super.onActivityResult(request, result, data);
    }
    private Intent getServiceIntent() {
        return new Intent(this, S2VService.class).putExtra("config", config);
    }
}