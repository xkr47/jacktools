package space.xkr47.jacktools;

import org.jaudiolibs.jnajack.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Patchbay {

    static final Patchbay patchbay = new Patchbay();

    static final String VU_METER = "VU meter";

    /**
     * This is a simple JACK client that disconnects ports that should not be connected and connects ports that should..
     * My own patchbay that is..
     */
    public static void main(String[] args) throws Exception {
        patchbay.init().loop();
    }

    final JackClient jack;
    final Jack jacki;
    final Map<String, Set<String>> connectedPorts = new TreeMap<>();
    final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
    final AtomicReference<ScheduledFuture<?>> graphCheckTask = new AtomicReference<>();

    Patchbay() {
        EnumSet<JackOptions> options = EnumSet.of(JackOptions.JackNoStartServer, JackOptions.JackUseExactName);
        EnumSet<JackStatus> status = EnumSet.noneOf(JackStatus.class);
        try {
            jacki = Jack.getInstance();
            jack = jacki.openClient("xkr47.patchbay", options, status);
        } catch (Exception ex) {
            System.out.println("ERROR : Status : " + status);
            throw new RuntimeException("Init failed", ex);
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
                    Set<String> port1 = connectedPorts.get(portName1);
                    Set<String> port2 = connectedPorts.get(portName2);
                    if (port1 != null) {
                        port1.remove(portName2);
                    }
                    if (port2 != null) {
                        port2.remove(portName1);
                    }
                }
            }
        });
        jack.setClientRegistrationCallback(new JackClientRegistrationCallback() {
            @Override
            public void clientRegistered(JackClient invokingClient, String clientName) {
                System.out.println("Client registered: " + clientName);
            }
            @Override
            public void clientUnregistered(JackClient invokingClient, String clientName) {
                System.out.println("Client unregistered: " + clientName);
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

                if (!cp.containsKey("system:playback_1")) {
                    System.out.println("* System ports missing, skipping checks..");
                    return;
                }

                boolean hasEqualizer = cp.containsKey("C* Eq10X2 - 10-band equalizer:In Left");

                if (hasEqualizer) {
                    System.out.println("* Route ports to system 1/2 to 3/4 + equalizer");
                    for (int ii = 0; ii < 2; ++ii) {
                        final int i = ii;
                        //Stream.concat(
                        cp.get("system:playback_" + (i + 1)).stream()//,
                                //cp.get("system:playback_" + (i + 3)).stream()
                                //)
                                .filter((port2) -> !port2.startsWith("C* Eq10X2 - 10-band equalizer:Out "))
                                .collect(toList())
                                .forEach(portToMove -> {
                                    try {
                                        disconnectPort(portToMove, "system:playback_" + (i + 1));
                                        connectPort(portToMove, "system:playback_" + (i + 3), true);
                                        if (hasEqualizer) {
                                            connectPort(portToMove, "C* Eq10X2 - 10-band equalizer:In " + stereoPort(i), true);
                                        }
                                        connectPort(portToMove, "jaaa:in_" + (i + 1), false);
                                        if (!hasEqualizer) {
                                            connectPort(portToMove, "M:in" + mapSpeakerOutput(i + 1), false);
                                        }
                                    } catch (RuntimeException e) {
                                        e.printStackTrace();
                                    }
                                });
                    }
                }

                System.out.println("* Muba links");
                for (int ii = 0; ii < 2; ++ii) {
                    final int i = ii;
                    String src = "PulseAudio JACK Sink:front-" + stereoPort(i).toLowerCase(Locale.ROOT);
                    connectPort(src, "Gain 2x2:In " + (i + 1), true);
                    if (!hasEqualizer) {
                        connectPort(src, VU_METER + ":in_" + (i + 3), true);
                    }
                }


                if (hasEqualizer) {
                    System.out.println("* Cleaning equalizer outputs");
                    for (int ii = 0; ii < 2; ++ii) {
                        final int i = ii;
                        String src = "C* Eq10X2 - 10-band equalizer:Out " + stereoPort(i);
                        cp.getOrDefault(src, emptySet()).stream()
                                .filter(dst -> !dst.equals("system:playback_" + mapSpeakerOutput(i + 1))
                                        && !dst.equals("M:in" + mapSpeakerOutput(i + 1))
                                        && !dst.equals(VU_METER + ":in_" + mapSpeakerOutput(i + 1))
                                )
                                .collect(toList())
                                .forEach(dst -> disconnectPort(src, dst));
                    }
                }

                if (hasEqualizer) {
                    System.out.println("* Cleaning jackmeter inputs");
                    for (int ii = 0; ii < 2; ++ii) {
                        final int i = ii;
                        String dst = "M:in" + mapSpeakerOutput(i + 1);
                        cp.getOrDefault(dst, emptySet()).stream()
                                .filter(src -> !src.startsWith("C* Eq10X2 - 10-band equalizer:Out"))
                                .collect(toList())
                                .forEach(src -> disconnectPort(src, dst));
                    }
                }

                if (cp.containsKey(VU_METER + ":in_1")) {
                    System.out.println("* Syncing VU meter inputs");
                    for (int ii = 0; ii < (hasEqualizer ? 4 : 2); ++ii) {
                        final int i = ii;
                        String vuPort = VU_METER + ":in_" + (i + 1);
                        if (!cp.containsKey(vuPort)) continue;
                        Set<String> expected = cp.get("system:playback_" + (i + 1)).stream()
                                .filter(dst -> !dst.startsWith(VU_METER + ":"))
                                .collect(toSet());
                        //noinspection unchecked
                        Set<String> actual = ofNullable(cp.get(vuPort))
                                .map(x -> (Set<String>)((HashSet<String>)x).clone())
                                .orElseGet(Collections::emptySet);
                        actual.stream()
                                .filter(port -> !expected.contains(port))
                                .forEach(port -> disconnectPort(port, vuPort));
                        expected.stream()
                                .filter(port -> !actual.contains(port))
                                .forEach(port -> connectPort(port, vuPort, true));
                    }
                }

                if (hasEqualizer) {
                    System.out.println("* Make sure equalizer outputs are connected properly");
                    for (int i = 0; i < 2; ++i) {
                        try {
                            connectPort("C* Eq10X2 - 10-band equalizer:Out " + stereoPort(i), "system:playback_" + mapSpeakerOutput(i + 1), true);
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                }

                System.out.println("* Connect mpv & xine via Video gain");
                for (int i = 0; i < 2; ++i) {
                    try {
                        disconnectPort("mpv:out_" + i, "system:playback_" + (i + 1));
                        disconnectPort("xine:out_" + i, "system:playback_" + (i + 1));
                        connectPort("mpv:out_" + i, "Video gain:In " + (i + 1), false);
                        connectPort("xine:out_" + i, "Video gain:In " + (i + 1), false);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                }

                System.out.println("* Piano to system only when Ardour is not running");
                boolean hasArdour = cp.containsKey("ardour:Audio/audio_in 1");
                for (int i = 0; i < 2; ++i) {
                    try {
                        if (hasArdour) {
                            disconnectPort("Piano gain:Out " + (i + 1), "system:playback_" + (i + 1));
                        } else {
                            connectPort("Piano gain:Out " + (i + 1), "system:playback_" + (i + 1), true);
                        }
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                }

                System.out.println("* Connect zynaddsubfx to correct ports unless already connected");
                //connectPort("zynaddsubfx:midi_input", cp.containsKey("system:midi_capture_2") ? "system:midi_capture_1" : "system:midi_capture_1", false);
                for (int i = 0; i < 2; ++i) {
                    try {
                        String srcport = "zynaddsubfx:out_" + (i + 1);
                        if (cp.getOrDefault(srcport, emptySet()).isEmpty()) {
                            connectPort(srcport, "system:playback_" + (i + 1), false);
                        }
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

    private int mapSpeakerOutput(int i) {
        // return i; // both speakers working
        return 1; // right speaker broken
        // return 2; // left spekaer broken
    }

    private void loop() throws InterruptedException {
        pool.submit(() -> 0); // ensure pool has at least one thread running so the app doesn't terminate
    }

    boolean isConnected(String port1, String port2) {
        return connectedPorts.getOrDefault(port1, emptySet()).contains(port2);
    }
}
