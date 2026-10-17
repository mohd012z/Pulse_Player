"""Dependency-free local PCM WAV generation proof; not a neural voice model."""
import argparse, hashlib, math, struct, wave
from pathlib import Path

RATE = 24000
NOTES = [(261.63, 'a'), (293.66, 'e'), (329.63, 'i'), (392.0, 'o'),
         (349.23, 'u'), (329.63, 'a'), (293.66, 'e'), (261.63, 'a')]
FORMANTS = {'a': (800, 1150, 2900), 'e': (500, 1700, 2500),
            'i': (320, 2200, 3000), 'o': (500, 900, 2600),
            'u': (350, 800, 2200)}

def synth(path, seconds=8):
    if not 1 <= seconds <= 120:
        raise ValueError("duration must be 1..120 seconds")
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    peak, energy = 0, 0
    with wave.open(str(path), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        chunk = bytearray()
        for i in range(RATE * seconds):
            t = i / RATE
            f, vowel = NOTES[int(t * 2) % len(NOTES)]
            local = (t * .5) % .5
            envelope = max(0, min(1, local / .035, (.5 - local) / .07))
            f0 = f * (1 + .002 * math.sin(2 * math.pi * 5 * t))
            voice = 0
            for h in range(1, 32):
                freq = h * f0
                weight = sum(math.exp(-.5 * ((freq - fm) / 125) ** 2)
                             for fm in FORMANTS[vowel])
                voice += weight * math.sin(2 * math.pi * h * f0 * t) / h
            bass = .16 * math.sin(2 * math.pi * f / 4 * t)
            value = math.tanh(1.7 * (.3 * envelope * voice + bass)) * .78
            sample = int(max(-1, min(1, value)) * 32767)
            peak = max(peak, abs(sample))
            energy += sample * sample
            chunk.extend(struct.pack('<h', sample))
            if len(chunk) >= 48000:
                w.writeframesraw(chunk)
                chunk.clear()
        if chunk:
            w.writeframesraw(chunk)
    return {'path': str(path), 'duration': seconds, 'sample_rate': RATE,
            'peak': round(peak / 32768, 5),
            'rms': round(math.sqrt(energy / (RATE * seconds)) / 32768, 5),
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default='ai_pulse_local_demo.wav')
    parser.add_argument('--seconds', type=int, default=8)
    args = parser.parse_args()
    print(synth(args.output, args.seconds))
