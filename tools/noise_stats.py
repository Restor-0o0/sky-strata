import math
import sys

import numpy as np

GRAD = np.array([[1, 1, 0], [-1, 1, 0], [1, -1, 0], [-1, -1, 0], [1, 0, 1], [-1, 0, 1], [1, 0, -1], [-1, 0, -1],
                 [0, 1, 1], [0, -1, 1], [0, 1, -1], [0, -1, -1], [1, 1, 0], [0, -1, 1], [-1, 1, 0], [0, -1, -1]], float)


class Improved:
    def __init__(self, rng):
        self.o = rng.random(3) * 256
        p = np.arange(256)
        rng.shuffle(p)
        self.p = np.concatenate([p, p])

    def __call__(self, x, y, z):
        x = x + self.o[0]; y = y + self.o[1]; z = z + self.o[2]
        xi, yi, zi = np.floor(x).astype(int), np.floor(y).astype(int), np.floor(z).astype(int)
        xf, yf, zf = x - xi, y - yi, z - zi
        xi &= 255; yi &= 255; zi &= 255
        p = self.p

        def g(h, dx, dy, dz):
            gv = GRAD[h & 15]
            return gv[..., 0] * dx + gv[..., 1] * dy + gv[..., 2] * dz

        fade = lambda t: t * t * t * (t * (t * 6 - 15) + 10)
        u, v, w = fade(xf), fade(yf), fade(zf)
        a, b = p[xi] + yi, p[xi + 1] + yi
        aa, ab, ba, bb = p[a] + zi, p[a + 1] + zi, p[b] + zi, p[b + 1] + zi
        lerp = lambda t, a_, b_: a_ + t * (b_ - a_)
        x1 = lerp(u, g(p[aa], xf, yf, zf), g(p[ba], xf - 1, yf, zf))
        x2 = lerp(u, g(p[ab], xf, yf - 1, zf), g(p[bb], xf - 1, yf - 1, zf))
        y1 = lerp(v, x1, x2)
        x3 = lerp(u, g(p[aa + 1], xf, yf, zf - 1), g(p[ba + 1], xf - 1, yf, zf - 1))
        x4 = lerp(u, g(p[ab + 1], xf, yf - 1, zf - 1), g(p[bb + 1], xf - 1, yf - 1, zf - 1))
        y2 = lerp(v, x3, x4)
        return lerp(w, y1, y2)


class Perlin:
    def __init__(self, rng, first, amps):
        self.oct = [Improved(rng) if a != 0 else None for a in amps]
        self.amps = amps
        n = len(amps)
        self.freq0 = 2.0 ** first
        self.val0 = 2.0 ** (n - 1) / (2.0 ** n - 1)

    def __call__(self, x, y, z):
        out = 0
        f, v = self.freq0, self.val0
        for o, a in zip(self.oct, self.amps):
            if o is not None:
                out = out + a * o(x * f, y * f, z * f) * v
            f *= 2; v /= 2
        return out


class Normal:
    def __init__(self, first, amps, seed=1):
        rng = np.random.default_rng(seed)
        self.a = Perlin(rng, first, amps)
        self.b = Perlin(rng, first, amps)
        nz = [i for i, a in enumerate(amps) if a != 0]
        span = nz[-1] - nz[0]
        self.k = (1 / 6) / (0.1 * (1 + 1 / (span + 1)))

    def __call__(self, x, y, z):
        s = 1.0181268882175227
        return (self.a(x, y, z) + self.b(x * s, y * s, z * s)) * self.k


def stats(first, amps, size=4096, step=4):
    n = Normal(first, amps)
    xs, zs = np.meshgrid(np.arange(0, size, step, dtype=float), np.arange(0, size, step, dtype=float))
    v = n(xs, np.zeros_like(xs), zs)
    gz, gx = np.gradient(v, step)
    g = np.hypot(gx, gz)
    return v, g


if __name__ == "__main__":
    first = int(sys.argv[1]); amps = [float(a) for a in sys.argv[2].split(",")]
    v, g = stats(first, amps)
    print(f"oct {first} {amps}: std {v.std():.3f}  p5..p95 {np.percentile(v, 5):.2f}..{np.percentile(v, 95):.2f}  "
          f"median |grad| {np.median(g):.4f}/block")
    for w in (0.1, 0.17, 0.25, 0.35):
        m = np.abs(v) < w
        width = 2 * w / np.median(g[m])
        print(f"  |n|<{w}: area {m.mean() * 100:.0f}%  typical width ~{width:.0f} blocks")
