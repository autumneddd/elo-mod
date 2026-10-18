package dev.autumneddd.elo;

/**
 * Standard Elo formula, same as https://www.omnicalculator.com/sports/elo
 *
 * expected = 1 / (10^((B - A) / 400) + 1)
 * change   = K * (score - expected)
 */
public final class EloCalculator {
	public static final double K_FACTOR = 32.0;

	private EloCalculator() {
	}

	public static double expected(double rating, double opponentRating) {
		return 1.0 / (Math.pow(10.0, (opponentRating - rating) / 400.0) + 1.0);
	}

	/** @param score 1 = win, 0.5 = draw, 0 = loss */
	public static double change(double rating, double opponentRating, double score) {
		return round1(K_FACTOR * (score - expected(rating, opponentRating)));
	}

	public static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
