package dev.overflight.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * How hard the ground below is pushing air upwards.
 *
 * The surface does reach the upper troposphere, but through circulation rather
 * than directly: warm wet ground drives deep convection that carries moisture to
 * cruise altitude, while the sinking air that makes a subtropical desert also
 * leaves the air ten kilometres above it among the driest anywhere. So a
 * rainforest should be trailing far more often than a desert.
 *
 * Read from a spread of points and then eased towards over time. Two reasons:
 * a client only has biomes for chunks it has loaded, which is a small window,
 * and a hard edge in the sky at a biome boundary would be worse than no effect
 * at all. Real ice-supersaturated regions are hundreds of kilometres across and
 * do not trace coastlines.
 */
public final class SurfaceConvection {
	/** Neither rising nor sinking. */
	private static final double NEUTRAL = 0.5;
	/** How far apart the samples are spread, in blocks. */
	private static final int SPREAD = 96;
	/** Ticks between readings. The sky has no business changing faster than this. */
	private static final long INTERVAL_TICKS = 40L;
	/** Share of the way to the new reading each time, for a gentle drift. */
	private static final double EASING = 0.08;

	private double current = NEUTRAL;
	private long lastSampledTick = Long.MIN_VALUE;

	/** The eased value, safe to call every frame. */
	public double value() {
		return current;
	}

	public void update(ClientLevel level, double x, double y, double z, long gameTime) {
		if (level == null || gameTime - lastSampledTick < INTERVAL_TICKS) {
			return;
		}
		lastSampledTick = gameTime;

		double total = 0.0;
		int samples = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos pos = BlockPos.containing(x + dx * SPREAD, y, z + dz * SPREAD);
				total += convectionOf(level.getBiome(pos).value());
				samples++;
			}
		}

		double reading = samples > 0 ? total / samples : NEUTRAL;
		current += (reading - current) * EASING;
	}

	/**
	 * Where a biome sits between sinking air and deep convection.
	 *
	 * Rain is the giveaway. Somewhere warm that rains is convecting; somewhere
	 * warm that never does is under exactly the descending air that dries the
	 * troposphere above it. Cold biomes sit in between, since polar air reaches
	 * ice saturation readily enough on its own.
	 */
	private static double convectionOf(Biome biome) {
		double temperature = biome.getBaseTemperature();
		if (biome.hasPrecipitation()) {
			// Warmer and wetter carries more moisture higher.
			double warmth = clamp((temperature - 0.4) / 0.7, 0.0, 1.0);
			return 0.62 + 0.28 * warmth;
		}
		// Warm and rainless is the signature of subsidence; cold and rainless is
		// just cold, and no drier aloft than anywhere else.
		double aridity = clamp((temperature - 0.6) / 1.0, 0.0, 1.0);
		return 0.55 - 0.40 * aridity;
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : (value > max ? max : value);
	}
}
