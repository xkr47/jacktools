package space.xkr47.jacktools;

import org.jaudiolibs.jnajack.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

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
    final AtomicReference<ScheduledFuture<?>> graphCheckTask = new AtomicReference<>();

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
                    if (strings != null) {
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
                synchronized (connectedPorts) {
                    System.out.println("Connected " + portName1 + " and " + portName2);
                    connectedPorts.get(portName1).add(portName2);
                    connectedPorts.get(portName2).add(portName1);
                }
            }

            @Override
            public void portsDisconnected(JackClient client, String portName1, String portName2) {
                synchronized (connectedPorts) {
                    System.out.println("Disconnected " + portName1 + " and " + portName2);
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
            ScheduledFuture<?> oldTask = graphCheckTask.getAndSet(null);
            if (oldTask != null) oldTask.cancel(false);
            ScheduledFuture<?> scheduledFuture = pool.schedule(this::checkPorts, 200, MILLISECONDS);
            graphCheckTask.set(scheduledFuture);
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

    private void checkPorts() {
        synchronized (connectedPorts) {
            try {
                System.out.println("Checking ports..");
                Map<String, Set<String>> cp;
                cp = Collections.unmodifiableMap(connectedPorts);
                for (Entry<String, Set<String>> connectedPort : cp.entrySet())         {
                    if (!connectedPort.getValue().isEmpty()) {
                        System.out.printf("%54s <-> %s\n", connectedPort.getKey(), connectedPort.getValue());
                    }
                }

                System.out.println("* Making sure normal outputs are set up correctly");
                for (int ii = 0; ii < 2; ++ii) {
                    final int i = ii;
                    Stream.concat(
                            cp.get("system:playback_" + (i + 1)).stream(),
                            cp.get("system:playback_" + (i + 3)).stream()
                    )
                            .filter((port2) -> !port2.startsWith("C* Eq10X2 - 10-band equalizer:Out "))
                            .collect(toList())
                            .forEach(portToMove -> {
                                try {
                                    disconnectPort(portToMove, "system:playback_" + (i + 1));
                                    connectPort(portToMove, "system:playback_" + (i + 3), true);
                                    connectPort(portToMove, "C* Eq10X2 - 10-band equalizer:In " + stereoPort(i), true);
                                    connectPort(portToMove, "jaaa:in_" + (i + 1), false);
                                } catch (RuntimeException e) {
                                    e.printStackTrace();
                                }
                            });
                }

                System.out.println("* Cleaning equalizer outputs");
                for (int ii = 0; ii < 2; ++ii) {
                    final int i = ii;
                    String src = "C* Eq10X2 - 10-band equalizer:Out " + stereoPort(i);
                    cp.getOrDefault(src, emptySet()).stream()
                            .filter(dst -> !dst.equals("system:playback_" + (i + 1))
                                    && !dst.equals("M:in" + (i + 1)))
                            .collect(toList())
                            .forEach(dst -> disconnectPort(src, dst));
                }

                System.out.println("* Cleaning jackmeter inputs");
                for (int ii = 0; ii < 2; ++ii) {
                    final int i = ii;
                    String dst = "M:in" + (i + 1);
                    cp.getOrDefault(dst, emptySet()).stream()
                            .filter(src -> !src.equals("C* Eq10X2 - 10-band equalizer:Out " + stereoPort(i)))
                            .collect(toList())
                            .forEach(src -> disconnectPort(src, dst));
                }

                System.out.println("* Make sure equalizer outputs are connected properly");
                for (int i = 0; i < 2; ++i) {
                    try {
                        connectPort("C* Eq10X2 - 10-band equalizer:Out " + stereoPort(i), "system:playback_" + (i + 1), true);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                }

                System.out.println("/Ports checked");
            } catch (RuntimeException e) {
                e.printStackTrace();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void connectPort(String port1, String port2, boolean complainOnFailure) {
        boolean is, has;
        synchronized (connectedPorts) {
            is = isConnected(port1, port2);
            has = connectedPorts.containsKey(port1) && connectedPorts.containsKey(port2);
        }
        if (!is) {
            if (has) {
                try {
                    System.out.println("Connecting '" + port1 + "' to '" + port2 + "'");
                    jacki.connect(jack, port1, port2);
                    synchronized (connectedPorts) {
                        connectedPorts.get(port1).add(port2);
                        connectedPorts.get(port2).add(port1);
                    }
                } catch (JackException e) {
                    throw new RuntimeException("Error connecting '" + port1 + "' and '" + port2 + "'", e);
                }
            } else {
                if (complainOnFailure) {
                    System.out.println("NOT Connecting '" + port1 + "' to '" + port2 + "' due to missing port(s)");
                }
            }
        }
    }

    private void disconnectPort(String port1, String port2) {
        boolean is, has;
        synchronized (connectedPorts) {
            is = isConnected(port1, port2);
            has = connectedPorts.containsKey(port1) && connectedPorts.containsKey(port2);
        }
        if (is) {
            if (has) {
                try {
                    System.out.println("Discnnecting '" + port1 + "' from '" + port2 + "'");
                    jacki.disconnect(jack, port1, port2);
                    synchronized (connectedPorts) {
                        connectedPorts.get(port1).remove(port2);
                        connectedPorts.get(port2).remove(port1);
                    }
                } catch (JackException e) {
                    throw new RuntimeException("Error disconnecting '" + port1 + "' and '" + port2 + "'", e);
                }
            } else {
                System.out.println("NOT Disconnecting '" + port1 + "' from '" + port2 + "' due to missing port(s)");
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
