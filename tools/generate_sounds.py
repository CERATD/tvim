"""Original synthesized alarm loops. Deterministic, offline, no third-party recordings."""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
DURATION = 8
OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
OUT.mkdir(parents=True, exist_ok=True)

def tone(track, start, length, hz, kind, gain=0.5):
    first = int(start * RATE)
    for i in range(min(int(length * RATE), len(track) - first)):
        t = i / RATE
        attack = min(1.0, t / 0.012)
        release = min(1.0, (length - t) / 0.045)
        if kind == "bell":
            sample = sum(a * math.sin(2 * math.pi * hz * ratio * t) * math.exp(-decay*t)
                         for ratio, a, decay in [(1, 1, 2.5), (2.76, .45, 4), (5.4, .15, 8)])
        elif kind == "piano":
            sample = sum(math.sin(2*math.pi*hz*h*t) * math.exp(-(2+h)*t) / h**1.5 for h in range(1, 6))
        elif kind == "pad":
            sample = (math.sin(2*math.pi*hz*t) + .4*math.sin(2*math.pi*hz*1.005*t)) * math.sin(math.pi*t/length)**.5
        elif kind == "arcade":
            sample = sum(math.sin(2*math.pi*hz*h*t)/h for h in (1, 3, 5, 7)) * math.exp(-3*t)
        elif kind == "chirp":
            sample = math.sin(2*math.pi*(hz*t + 1800*t*t)) * math.sin(math.pi*t/length)**2
        elif kind == "pulse":
            sample = (math.sin(2*math.pi*hz*t) + .3*math.sin(2*math.pi*hz*2*t)) * math.exp(-9*t)
        else:
            sample = math.sin(2*math.pi*hz*t)
        track[first+i] += sample * attack * max(0, release) * gain

def create(name):
    track = [0.0] * (RATE * DURATION)
    if name == "morning_light":
        for beat, hz in enumerate([523.25, 659.25, 783.99, 1046.5]*2):
            tone(track, beat*.85, 1.15, hz, "bell", .45)
    elif name == "deep_moon":
        for start, notes in [(0, [110, 164.81, 220]), (3.7, [130.81, 196, 261.63])]:
            for hz in notes: tone(track, start, 3.5, hz, "pad", .22)
    elif name == "solar_rise":
        for beat, hz in enumerate([220, 220, 330, 440, 220, 330, 440, 660]*2):
            tone(track, beat*.45, .35, hz, "pulse", .8)
    elif name == "star_night":
        for beat, hz in enumerate([880, 1318.5, 1174.66, 1760, 1318.5, 1046.5]):
            tone(track, beat*1.1, 1.8, hz, "bell", .5)
    elif name == "soft_piano":
        for beat, hz in enumerate([261.63, 329.63, 392, 523.25, 392, 329.63, 293.66, 392, 493.88, 587.33]):
            tone(track, beat*.7, 1.2, hz, "piano", .65)
    elif name == "minimal":
        for start in [0, .25, .5, 2, 2.25, 2.5, 4, 4.25, 4.5, 6, 6.25, 6.5]:
            tone(track, start, .15, 880, "sine", .5)
    elif name == "classic_bell":
        for start in [0, .16, .32, 1.6, 1.76, 1.92, 3.2, 3.36, 3.52, 4.8, 4.96, 5.12, 6.4, 6.56, 6.72]:
            tone(track, start, .8, 740 if int(start*100)%32 else 990, "bell", .6)
    elif name == "forest_birds":
        for start, hz in [(0, 1500), (.3, 2100), (1.3, 1800), (1.6, 2400), (3, 1400), (3.25, 1900), (4.5, 2300), (5.4, 1700), (5.7, 2200), (6.6, 1600)]:
            tone(track, start, .22, hz, "chirp", .5)
    elif name == "rain_drops":
        rng = random.Random(42)
        smooth = 0.0
        for i in range(len(track)):
            smooth = .94*smooth + .06*rng.uniform(-1, 1)
            track[i] = smooth*.9
        for _ in range(45):
            tone(track, rng.uniform(.1, 7.7), .12, rng.uniform(700, 1800), "pulse", .06)
    elif name == "retro_arcade":
        notes = [523.25, 659.25, 783.99, 1046.5, 783.99, 659.25, 587.33, 880]
        for beat in range(28):
            tone(track, beat*.27, .21, notes[beat%len(notes)], "arcade", .4)
    peak = max(abs(x) for x in track)
    assert peak > 0
    samples = []
    for i, value in enumerate(track):
        edge = min(1.0, i/(RATE*.02), (len(track)-1-i)/(RATE*.02))
        samples.append(int(value/peak * .88 * 32767 * edge))
    with wave.open(str(OUT / (name + ".wav")), "wb") as f:
        f.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        f.writeframes(struct.pack("<" + "h"*len(samples), *samples))

if __name__ == "__main__":
    for name in ["morning_light", "deep_moon", "solar_rise", "star_night", "soft_piano", "minimal",
                 "classic_bell", "forest_birds", "rain_drops", "retro_arcade"]:
        create(name)
