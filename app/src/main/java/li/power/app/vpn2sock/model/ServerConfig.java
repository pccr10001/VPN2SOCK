package li.power.app.vpn2sock.model;

import java.io.Serializable;

import lombok.Data;

@Data
public class ServerConfig implements Serializable {

    private String host;
    private int port;

}
