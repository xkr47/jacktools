package space.xkr47.jacktools;

import org.jaudiolibs.jnajack.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class DebugDump {
    public static void main(String[] args) throws Exception {
        EnumSet<JackOptions> options = EnumSet.of(JackOptions.JackNoStartServer);
        EnumSet<JackStatus> status = EnumSet.noneOf(JackStatus.class);
        JackClient jack;
        final Jack jacki = Jack.getInstance();
        try {
            jack = jacki.openClient("lol", options, status);
        } catch (JackException ex) {
            System.out.println("ERROR : Status : " + status);
            throw ex;
        }
        System.out.println(jack);

        jack.setPortRegistrationCallback(new JackPortRegistrationCallback() {
            @Override
            public void portRegistered(JackClient client, String portFullName) {
                System.out.println("Port registered: " + portFullName);
            }

            @Override
            public void portUnregistered(JackClient client, String portFullName) {
                System.out.println("Port unregistered: " + portFullName);
            }
        });
        jack.setPortConnectCallback(new JackPortConnectCallback() {
            @Override
            public void portsConnected(JackClient client, String portName1, String portName2) {
                System.out.println("Connected " + portName1 + " and " + portName2);
            }

            @Override
            public void portsDisconnected(JackClient client, String portName1, String portName2) {
                System.out.println("Disconnected " + portName1 + " and " + portName2);
            }
        });

        jack.setBuffersizeCallback((client, bufferSize) -> {
            System.out.println("Buffer size changed to " + bufferSize);
        });

        jack.setClientRegistrationCallback(new JackClientRegistrationCallback() {
            @Override
            public void clientRegistered(JackClient invokingClient, String clientName) {
                System.out.println("Client regixtered: " + clientName);
            }

            @Override
            public void clientUnregistered(JackClient invokingClient, String clientName) {
                System.out.println("Client unregixtered: " + clientName);
            }
        });
        jack.setGraphOrderCallback(invokingClient -> {
            System.out.println("Graph order changed");
        });
        jack.setSampleRateCallback((client, sampleRate) -> {
            System.out.println("Sample rate changed to " + sampleRate);
        });
        jack.setXrunCallback(client -> {
            System.out.println("Xrun");
        });
        String[] ports = jacki.getPorts(jack, null, null, null);
        for (String port : ports) {
            String[] allConnections = jacki.getAllConnections(jack, port);
            System.out.println("Port " + port + (allConnections.length > 0 ? " connected to " + Arrays.toString(allConnections) : ""));
        }

        jack.activate();

        while (true) {
            Thread.sleep(1000L);
            Map<String, String> hm = new LinkedHashMap<>();
            hm.put("n", String.valueOf(jack.getName()));
            hm.put("t", String.valueOf(jacki.getTime()));
            hm.put("ft", String.valueOf(jack.getFrameTime()));
            hm.put("lft", String.valueOf(jack.getLastFrameTime()));
            hm.put("ctf", String.valueOf(jack.getCurrentTransportFrame()));
            hm.put("sr", String.valueOf(jack.getSampleRate()));
            hm.put("bs", String.valueOf(jack.getBufferSize()));
            System.out.println(hm);
        }
    }
}
