package net.montyclt.redstore.block.gate;

/**
 * What a logic gate computes from its two inputs, before inversion.
 *
 * <p>Any signal counts as true and the answer is a boolean. Arithmetic on signal strengths is the
 * comparator's job, not a gate's; see spec/ideas for the analog variants that were considered.
 */
public enum GateOperation {
	AND("and", "nand"),
	OR("or", "nor"),
	XOR("xor", "xnor");

	private final String plain;
	private final String negated;

	GateOperation(String plain, String negated) {
		this.plain = plain;
		this.negated = negated;
	}

	/** What the gate is currently computing, as the tail of a translation key. */
	public String name(boolean inverted) {
		return inverted ? this.negated : this.plain;
	}

	public boolean test(int a, int b) {
		boolean left = a > 0;
		boolean right = b > 0;

		return switch (this) {
			case AND -> left && right;
			case OR -> left || right;
			case XOR -> left ^ right;
		};
	}
}
