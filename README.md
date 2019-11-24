# jacktools
Jack tools written in Java, using https://github.com/jaudiolibs/jnajack

# Tools

## DebugDump

([source](https://github.com/xkr47/jacktools/blob/master/src/main/java/space/xkr47/jacktools/DebugDump.java))

This is a simple JACK client that just dumps more or less any info it can get its hands on, including updates.

Example output:

```
Sample rate changed to 48000
Port system:capture_1
Port system:capture_2
Port system:capture_3
Port system:capture_4
Port system:capture_5
Port system:capture_6
Port system:capture_7
Port system:capture_8
Port system:capture_9
Port system:capture_10
Port system:capture_11
Port system:capture_12
Port system:capture_13
Port system:capture_14
Port system:capture_15
Port system:capture_16
Port system:capture_17
Port system:capture_18
Port system:playback_1 connected to [C* Eq10X2 - 10-band equalizer:Out Left]
Port system:playback_2 connected to [C* Eq10X2 - 10-band equalizer:Out Right]
Port system:playback_3 connected to [PulseAudio JACK Sink:front-left, XMMS2:out_1]
Port system:playback_4 connected to [PulseAudio JACK Sink:front-right, XMMS2:out_2]
Port system:playback_5
Port system:playback_6
Port system:playback_7
Port system:playback_8
Port system:midi_capture_1
Port system:midi_playback_1
Port a2j:Midi Through [14] (capture): Midi Through Port-0
Port a2j:Midi Through [14] (playback): Midi Through Port-0
Port a2j:Midi Through [14] (capture): Midi Through Port-1
Port a2j:Midi Through [14] (playback): Midi Through Port-1
Port a2j:Midi Through [14] (capture): Midi Through Port-2
Port a2j:Midi Through [14] (playback): Midi Through Port-2
Port a2j:Midi Through [14] (capture): Midi Through Port-3
Port a2j:Midi Through [14] (playback): Midi Through Port-3
Port a2j:Midi Through [14] (capture): Midi Through Port-4
Port a2j:Midi Through [14] (playback): Midi Through Port-4
Port a2j:Midi Through [14] (capture): Midi Through Port-5
Port a2j:Midi Through [14] (playback): Midi Through Port-5
Port a2j:Midi Through [14] (capture): Midi Through Port-6
Port a2j:Midi Through [14] (playback): Midi Through Port-6
Port a2j:Midi Through [14] (capture): Midi Through Port-7
Port a2j:Midi Through [14] (playback): Midi Through Port-7
Port a2j:Midi Through [14] (capture): Midi Through Port-8
Port a2j:Midi Through [14] (playback): Midi Through Port-8
Port a2j:Midi Through [14] (capture): Midi Through Port-9
Port a2j:Midi Through [14] (playback): Midi Through Port-9
Port a2j:Midi Through [14] (capture): Midi Through Port-10
Port a2j:Midi Through [14] (playback): Midi Through Port-10
Port a2j:Midi Through [14] (capture): Midi Through Port-11
Port a2j:Midi Through [14] (playback): Midi Through Port-11
Port a2j:Midi Through [14] (capture): Midi Through Port-12
Port a2j:Midi Through [14] (playback): Midi Through Port-12
Port a2j:Midi Through [14] (capture): Midi Through Port-13
Port a2j:Midi Through [14] (playback): Midi Through Port-13
Port a2j:Midi Through [14] (capture): Midi Through Port-14
Port a2j:Midi Through [14] (playback): Midi Through Port-14
Port a2j:Midi Through [14] (capture): Midi Through Port-15
Port a2j:Midi Through [14] (playback): Midi Through Port-15
Port PulseAudio JACK Source:front-left
Port PulseAudio JACK Source:front-right
Port PulseAudio JACK Sink:front-left connected to [C* Eq10X2 - 10-band equalizer:In Left, system:playback_3]
Port PulseAudio JACK Sink:front-right connected to [C* Eq10X2 - 10-band equalizer:In Right, system:playback_4]
Port C* Eq10X2 - 10-band equalizer:In Left connected to [PulseAudio JACK Sink:front-left, XMMS2:out_1]
Port C* Eq10X2 - 10-band equalizer:In Right connected to [PulseAudio JACK Sink:front-right, XMMS2:out_2]
Port C* Eq10X2 - 10-band equalizer:Out Left connected to [system:playback_1]
Port C* Eq10X2 - 10-band equalizer:Out Right connected to [system:playback_2]
Port C* Eq10X2 - 10-band equalizer:events-in
Port XMMS2:out_1 connected to [system:playback_3, C* Eq10X2 - 10-band equalizer:In Left]
Port XMMS2:out_2 connected to [system:playback_4, C* Eq10X2 - 10-band equalizer:In Right]
Graph order changed
{n=lol, t=13648382471, ft=653322826, lft=653322752, ctf=0, sr=48000, bs=256}
{n=lol, t=13649383522, ft=653370879, lft=653370880, ctf=0, sr=48000, bs=256}
{n=lol, t=13650384297, ft=653418916, lft=653418752, ctf=0, sr=48000, bs=256}
{n=lol, t=13651384960, ft=653466952, lft=653466880, ctf=0, sr=48000, bs=256}
{n=lol, t=13652385701, ft=653514988, lft=653514752, ctf=0, sr=48000, bs=256}
{n=lol, t=13653386534, ft=653563020, lft=653562880, ctf=0, sr=48000, bs=256}
...
Disconnected PulseAudio JACK Sink:front-right and system:playback_4
Disconnected XMMS2:out_2 and system:playback_4
Graph order changed
Connected PulseAudio JACK Sink:front-right and system:playback_4
Graph order changed
Connected XMMS2:out_2 and system:playback_4
Graph order changed
{n=lol, t=13700416596, ft=655820473, lft=655820288, ctf=0, sr=48000, bs=256}
{n=lol, t=13701416909, ft=655868490, lft=655868416, ctf=0, sr=48000, bs=256}
{n=lol, t=13702417300, ft=655916505, lft=655916288, ctf=0, sr=48000, bs=256}
```
