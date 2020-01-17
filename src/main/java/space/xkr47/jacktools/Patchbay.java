package space.xkr47.jacktools;

import org.jaudiolibs.jnajack.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
    final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
    final AtomicReference<Runnable> graphCheckTask = new AtomicReference<>();

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
            public void portRegistered(JackClient client, String addedPort) {
                System.out.println("Port registered: " + addedPort);
                synchronized (connectedPorts) {
                    Set<String> strings = connectedPorts.putIfAbsent(addedPort, new HashSet<>());
                    if (!strings.isEmpty()) {
                        System.err.println("Warning: existing port re-registered: " + addedPort);
                    }
                }
            }

            @Override
            public void portUnregistered(JackClient client, String removedPort) {
                System.out.println("Port unregistered: " + removedPort);
                synchronized (connectedPorts) {
                    Set<String> connections = connectedPorts.remove(removedPort);
                    if (connections == null) {
                        System.err.println("Warning: non-existing port re-removed: " + removedPort);
                    } else if (!connections.isEmpty()) {
                        System.err.println("Warning: existing port '" + removedPort + "' removed with connections to " + connections);
                        for (String connection : connections) {
                            connectedPorts.get(connection).remove(removedPort);
                        }
                    }
                }
            }
        });
        jack.setPortConnectCallback(new JackPortConnectCallback() {
            @Override
            public void portsConnected(JackClient client, String portName1, String portName2) {
                System.out.println("Connected " + portName1 + " and " + portName2);
                synchronized (connectedPorts) {
                    connectedPorts.get(portName1).add(portName2);
                    connectedPorts.get(portName2).add(portName1);
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
            System.out.println("Graph updated");
            Runnable oldTask = graphCheckTask.getAndSet(null);
            if (oldTask != null) pool.remove(oldTask);
            Set<String>[] portsToMove = new Set[2];
            synchronized (connectedPorts) {
                for (Entry<String, Set<String>> connectedPort : connectedPorts.entrySet()) {
                    System.out.printf("%54s <-> %s\n", connectedPort.getKey(), connectedPort.getValue());
                }
                for (int i = 0; i < 2; ++i) {
                    Set<String> ports2 = connectedPorts.get("system:playback_" + (i + 1));
                    portsToMove[i] = ports2.stream()
                            .filter((port2) -> !port2.startsWith("C* Eq10X2 - 10-band equalizer:Out "))
                            .collect(Collectors.toSet());
                }
            }
            if (!portsToMove[0].isEmpty() || !portsToMove[1].isEmpty()) {
                System.out.println("Scheduling check " + portsToMove[0] + " AND " + portsToMove[1]);
                Runnable r = () -> {
                    System.out.println("Checking ports..");
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
                };
                graphCheckTask.set(r);
                pool.schedule(r, 100, MILLISECONDS);
            }
            System.out.println("---");
        });
        String[] ports = jacki.getPorts(jack, null, null, null);
        synchronized (connectedPorts) {
            for (String port : ports) {
                connectedPorts.put(port, new HashSet<>());
            }
            for (String port : ports) {
                String[] allConnections = jacki.getAllConnections(jack, port);
                for (String port2 : allConnections) {
                    connectedPorts.get(port).add(port2);
                }
                //System.out.println("Port " + port + (allConnections.length > 0 ? " connected to " + Arrays.toString(allConnections) : ""));
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
        pool.submit(() -> 0); // ensure pool has at least one thread running so the app doesn't terminate
    }

    boolean isConnected(String port1, String port2) {
        return connectedPorts.getOrDefault(port1, emptySet()).contains(port2);
    }
}
