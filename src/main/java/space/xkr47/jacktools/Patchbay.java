package space.xkr47.jacktools;

import org.jaudiolibs.jnajack.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;

public class Patchbay {

    /**
     * This is a simple JACK client that disconnects ports that should not be connected and connects ports that should..
     * My own patchbay that is..
     */
    public static void main(String[] args) throws Exception {
        new Patchbay().init().loop();
    }

    final JackClient jack;
    final Jack jacki;
    final Map<String, Set<String>> connectedPorts = new TreeMap<>();
    final BlockingQueue<Runnable> jobs = new ArrayBlockingQueue<>(100);

    Patchbay() throws JackException {
        EnumSet<JackOptions> options = EnumSet.of(JackOptions.JackNoStartServer);
        EnumSet<JackStatus> status = EnumSet.noneOf(JackStatus.class);
        jacki = Jack.getInstance();
        try {
            jack = jacki.openClient("lol", options, status);
        } catch (JackException ex) {
            System.out.println("ERROR : Status : " + status);
            throw ex;
        }
    }

    private Patchbay init() throws JackException {
        jack.setPortRegistrationCallback(new JackPortRegistrationCallback() {
            @Override
            public void portRegistered(JackClient client, String portFullName) {
                System.out.println("Port registered: " + portFullName);
            }

            @Override
            public void portUnregistered(JackClient client, String portFullName) {
                System.out.println("Port unregistered: " + portFullName);
                synchroniz
            }
        });
        jack.setPortConnectCallback(new JackPortConnectCallback() {
            @Override
            public void portsConnected(JackClient client, String portName1, String portName2) {
                System.out.println("Connected " + portName1 + " and " + portName2);
                synchronized (connectedPorts) {
                    connectedPorts.compute(portName1, addConnectedPort(portName2));
                    connectedPorts.compute(portName2, addConnectedPort(portName1));
                }
            }

            @Override
            public void portsDisconnected(JackClient client, String portName1, String portName2) {
                System.out.println("Disconnected " + portName1 + " and " + portName2);
                synchronized (connectedPorts) {
                    connectedPorts.get(portName1).remove(portName2);
                    connectedPorts.get(portName2).remove(portName1);
                }
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
            System.out.println("Checking graph");
            Set<String>[] portsToMove = new Set[2];
            synchronized (connectedPorts) {
                for (Entry<String, Set<String>> connectedPort : connectedPorts.entrySet()) {
                    System.out.printf("%40s <-> %s\n", connectedPort.getKey(), connectedPort.getValue());
                }
                for (int i = 0; i < 2; ++i) {
                    Set<String> ports2 = connectedPorts.get("system:playback_" + (i + 1));
                    portsToMove[i] = ports2.stream()
                            .filter((port2) -> !port2.startsWith("C* Eq10X2 - 10-band equalizer:Out "))
                            .collect(Collectors.toSet());
                }
            }
            if (!portsToMove[0].isEmpty() || !portsToMove[1].isEmpty()) {
                jobs.add(() -> {
                    for (int i = 0; i < 2; ++i) {
                        for (String portToMove : portsToMove[i]) {
                            try {
                                disconnectPort(portToMove, "system:playback_" + (i + 1));
                                connectPort(portToMove, "system:playback_" + (i + 3));
                                connectPort(portToMove, "C* Eq10X2 - 10-band equalizer:In " + stereoPort(i));
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
            System.out.println("---");
        });
        jack.setSampleRateCallback((client, sampleRate) -> {
            System.out.println("Sample rate changed to " + sampleRate);
        });
        jack.setXrunCallback(client -> {
            System.out.println("Xrun");
        });
        String[] ports = jacki.getPorts(jack, null, null, null);
        synchronized (connectedPorts) {
            for (String port : ports) {
                String[] allConnections = jacki.getAllConnections(jack, port);
                for (String port2 : allConnections) {
                    connectedPorts.compute(port, addConnectedPort(port2));
                }
                System.out.println("Port " + port + (allConnections.length > 0 ? " connected to " + Arrays.toString(allConnections) : ""));
            }
        }
        jack.activate();

        return this;
    }

    private void connectPort(String port1, String port2) {
        boolean is;
        synchronized (connectedPorts) {
            is = isConnected(port1, port2);
        }
        if (!is) {
            try {
                jacki.connect(jack, port1, port2);
            } catch (JackException e) {
                throw new RuntimeException("Error connecting '" + port1 + "' and '" + port2 + "'");
            }
        }
    }

    private void disconnectPort(String port1, String port2) {
        boolean is;
        synchronized (connectedPorts) {
            is = isConnected(port1, port2);
        }
        if (is) {
            try {
                jacki.disconnect(jack, port1, port2);
            } catch (JackException e) {
                throw new RuntimeException("Error disconnecting '" + port1 + "' and '" + port2 + "'");
            }
        }
    }

    private static String stereoPort(int i) {
        return i == 0 ? "Left": "Right";
    }

    private void loop() throws InterruptedException {
        //noinspection InfiniteLoopStatement
        while (true) {
            /*
            Map<String, String> hm = new LinkedHashMap<>();
            hm.put("n", String.valueOf(jack.getName()));
            hm.put("t", String.valueOf(jacki.getTime()));
            hm.put("ft", String.valueOf(jack.getFrameTime()));
            hm.put("lft", String.valueOf(jack.getLastFrameTime()));
            hm.put("ctf", String.valueOf(jack.getCurrentTransportFrame()));
            hm.put("sr", String.valueOf(jack.getSampleRate()));
            hm.put("bs", String.valueOf(jack.getBufferSize()));
            System.out.println(hm);
            */
            Runnable take = jobs.take();
            System.out.println("Running job");
            try {
                take.run();
            } catch (RuntimeException e) {
                System.err.println("Job failed: ");
                e.printStackTrace();
            }
            System.out.println("Job complete");
        }
    }

    static BiFunction<String, Set<String>, Set<String>> addConnectedPort(String newPort2) {
        return (port1, ports2) -> {
            if (ports2 == null) ports2 = new HashSet<>();
            ports2.add(newPort2);
            return ports2;
        };
    }

    boolean isConnected(String port1, String port2) {
        return connectedPorts.getOrDefault(port1, emptySet()).contains(port2);
    }

}
