"""Generate six original, offline PCM alarm loops; no third-party recordings."""
import math, struct, wave
from pathlib import Path
out = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
out.mkdir(parents=True, exist_ok=True)
melodies = {
    'morning_light': [523.25, 659.25, 783.99, 1046.5, 783.99, 659.25],
    'deep_moon': [220, 261.63, 329.63, 261.63, 220, 329.63],
    'solar_rise': [440, 554.37, 659.25, 880, 659.25, 880],
    'star_night': [587.33, 739.99, 880, 739.99, 587.33, 440],
    'soft_piano': [261.63, 329.63, 392, 523.25, 392, 329.63],
    'minimal': [880, 880, 660, 660, 880, 660],
}
rate = 22050
for name, notes in melodies.items():
    data = bytearray()
    for note in notes:
        for i in range(rate // 2):
            t = i / rate
            envelope = min(1, t / .015) * min(1, (.5-t)/.04) * math.exp(-3*t)
            signal = (math.sin(2*math.pi*note*t) + .2*math.sin(4*math.pi*note*t)) / 1.2
            data += struct.pack('<h', int(24000 * envelope * signal))
    with wave.open(str(out / (name+'.wav')), 'wb') as f:
        f.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
        f.writeframes(data)
