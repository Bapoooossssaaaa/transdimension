"""
Prints which share of the Trans Realm's caves each biome gets at each depth, the way the game picks them: the realm's
own biome-source entries (data/transdimension/dimension/trans_realm.json, from generate_worldgen.py) against climate
points with the overworld climate noises' real spread.

The climate noises are sampled from a copy of the game's own noise (ImprovedNoise octaves summed as PerlinNoise, two
of them as NormalNoise) with the overworld's noise parameters (tools/vanilla_extra/templates/worldgen/overworld/noise).
Its seeding isn't the game's, but any seed gives the same spread, and the spread is what matters: humidity, for one,
rarely leaves -0.4 to 0.4, so a biome that needs humidity over 0.7 is far rarer than its range looks. Only land columns
count (continentalness over -0.11). Depth is about (surface height - y) / 128: 0.5 is some 64 blocks down.

    python3 tools/sim_cave_biomes.py            (needs: pip install numpy)
"""

import json
import os

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REALM = os.path.join(HERE, "..", "src", "main", "resources", "data", "transdimension", "dimension", "trans_realm.json")
NOISE = os.path.join(HERE, "vanilla_extra", "templates", "worldgen", "overworld", "noise")
CAVES = {"pastel_lush_caves", "crystal_caves", "frosted_caves", "blooming_caverns", "pink_deep_dark"}
CLIMATE = ("temperature", "humidity", "continentalness", "erosion", "weirdness")
DEPTHS = (0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2)
GRADIENTS = np.array([[1, 1, 0], [-1, 1, 0], [1, -1, 0], [-1, -1, 0], [1, 0, 1], [-1, 0, 1], [1, 0, -1], [-1, 0, -1],
                      [0, 1, 1], [0, -1, 1], [0, 1, -1], [0, -1, -1], [1, 1, 0], [0, -1, 1], [-1, 1, 0], [0, -1, -1]], dtype=float)


class ImprovedNoise:
    """The game's ImprovedNoise (Perlin noise with its 16 gradients and smootherstep)."""

    def __init__(self, rng):
        self.origin = rng.random(3) * 256.0
        self.p = rng.permutation(256)

    def noise(self, x, y, z):
        x, y, z = x + self.origin[0], y + self.origin[1], z + self.origin[2]
        i, j, k = (np.floor(v).astype(np.int64) for v in (x, y, z))
        dx, dy, dz = x - i, y - j, z - k
        p = lambda n: self.p[n & 0xFF]
        a, b = p(i), p(i + 1)
        aa, ab, ba, bb = p(a + j), p(a + j + 1), p(b + j), p(b + j + 1)
        grad = lambda h, gx, gy, gz: GRADIENTS[h & 15][:, 0] * gx + GRADIENTS[h & 15][:, 1] * gy + GRADIENTS[h & 15][:, 2] * gz
        smooth = lambda t: t * t * t * (t * (t * 6 - 15) + 10)
        lerp = lambda t, u, v: u + t * (v - u)
        u, v, w = smooth(dx), smooth(dy), smooth(dz)
        near = lerp(v, lerp(u, grad(p(aa + k), dx, dy, dz), grad(p(ba + k), dx - 1, dy, dz)),
                    lerp(u, grad(p(ab + k), dx, dy - 1, dz), grad(p(bb + k), dx - 1, dy - 1, dz)))
        far = lerp(v, lerp(u, grad(p(aa + k + 1), dx, dy, dz - 1), grad(p(ba + k + 1), dx - 1, dy, dz - 1)),
                   lerp(u, grad(p(ab + k + 1), dx, dy - 1, dz - 1), grad(p(bb + k + 1), dx - 1, dy - 1, dz - 1)))
        return lerp(w, near, far)


class NormalNoise:
    """The game's NormalNoise: two PerlinNoise sums (octaves from firstOctave, each twice the frequency and half the
    weight of the last), the second at 1.0181268882175227 times the input, scaled to a deviation of about 1/3."""

    def __init__(self, rng, first_octave, amplitudes):
        self.amplitudes = amplitudes
        self.octaves = [[ImprovedNoise(rng) if a else None for a in amplitudes] for _ in range(2)]
        self.input_factor = 2.0 ** first_octave
        self.value_factor = 2.0 ** (len(amplitudes) - 1) / (2.0 ** len(amplitudes) - 1.0)
        used = [i for i, a in enumerate(amplitudes) if a]
        self.factor = (1.0 / 6.0) / (0.1 * (1.0 + 1.0 / (used[-1] - used[0] + 1)))

    def perlin(self, octaves, x, y, z):
        total, scale, weight = 0.0, self.input_factor, self.value_factor
        for amplitude, octave in zip(self.amplitudes, octaves):
            if octave is not None:
                total = total + amplitude * octave.noise(x * scale, y * scale, z * scale) * weight
            scale, weight = scale * 2.0, weight / 2.0
        return total

    def value(self, x, y, z):
        k = 1.0181268882175227
        return (self.perlin(self.octaves[0], x, y, z) + self.perlin(self.octaves[1], x * k, y * k, z * k)) * self.factor


def climate(count, seed=5):
    """Climate points for {@code count} random land columns, sampled as the noise router does (xz scale 0.25)."""
    rng = np.random.default_rng(seed)
    x, z = (rng.uniform(-300000.0, 300000.0, count * 2) * 0.25 for _ in range(2))
    y = np.zeros(count * 2)
    point = {}
    for key, name in zip(CLIMATE, ("temperature", "vegetation", "continentalness", "erosion", "ridge")):
        with open(os.path.join(NOISE, name + ".json"), encoding="utf-8") as f:
            params = json.load(f)
        point[key] = NormalNoise(rng, params["firstOctave"], params["amplitudes"]).value(x, y, z)
    land = point["continentalness"] > -0.11
    return {k: v[land] for k, v in point.items()}


def distance(value, bounds):
    lo, hi = (bounds, bounds) if isinstance(bounds, (int, float)) else bounds
    return np.where(value < lo, lo - value, np.where(value > hi, value - hi, 0.0))


def shares(entries, point, depth):
    """Each biome's share of the climate points at {@code depth} (plain caves counted as one)."""
    best = np.full(len(point["temperature"]), np.inf)
    winner = np.full(len(best), "plain caves", dtype=object)
    for name, p in entries:
        fitness = sum(distance(point[k], p[k]) ** 2 for k in CLIMATE) + distance(np.float64(depth), p["depth"]) ** 2 + p["offset"] ** 2
        better = fitness < best
        best = np.where(better, fitness, best)
        winner = np.where(better, name if name in CAVES else "plain caves", winner)
    names, counts = np.unique(winner, return_counts=True)
    return {n: c / len(best) for n, c in zip(names, counts)}


def main():
    with open(REALM, encoding="utf-8") as f:
        realm = json.load(f)
    entries = [(b["biome"].split(":", 1)[1], b["parameters"]) for b in realm["generator"]["biome_source"]["biomes"]]
    point = climate(40000)
    total = {}
    for depth in DEPTHS:
        share = shares(entries, point, depth)
        for k, v in share.items():
            total[k] = total.get(k, 0.0) + v / len(DEPTHS)
        print(f"depth {depth:.1f}: " + ", ".join(f"{k} {100 * v:.0f}%" for k, v in sorted(share.items(), key=lambda kv: -kv[1])))
    print("all:       " + ", ".join(f"{k} {100 * v:.1f}%" for k, v in sorted(total.items(), key=lambda kv: -kv[1])))


if __name__ == "__main__":
    main()
