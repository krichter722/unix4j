package org.unix4j.unix;

import static org.unix4j.util.Assert.assertArgNotNull;

import java.util.regex.Pattern;

import org.unix4j.builder.CommandBuilder;
import org.unix4j.command.AbstractArgs;
import org.unix4j.command.AbstractCommand;
import org.unix4j.command.CommandInterface;
import org.unix4j.io.Output;
import org.unix4j.line.Line;
import org.unix4j.line.LineProcessor;
import org.unix4j.util.TypedMap;

/**
 * Non-instantiable module with inner types making up the grep command.
 */
public final class Grep {

	/**
	 * The "grep" command name.
	 */
	public static final String NAME = "grep";

	/**
	 * Interface defining all method signatures for the grep command.
	 * 
	 * @param <R>
	 *            the return type for all command signature methods, usually a
	 *            new command instance or a command fromFile providing methods
	 *            for chained invocation of following commands
	 */
	public static interface Interface<R> extends CommandInterface<R> {
		/**
		 * Filters the input lines and writes the matching lines to the output.
		 * A line matches if it contains the given {@code matchString} using
		 * case-sensitive string comparison.
		 * 
		 * @param matchString
		 *            the string to be matched by the lines
		 * @return the generic type {@code <R>} defined by the implementing
		 *         class, even if the command itself returns no value and writes
		 *         its result to an {@link Output} object. This serves
		 *         implementing classes like the command {@link Factory} to
		 *         return a new {@link Command} instance for the argument values
		 *         passed to this method. {@link CommandBuilder} extensions also
		 *         implementing this this command interface usually return an
		 *         instance to itself facilitating chained invocation of joined
		 *         commands.
		 */
		R grep(String matchString);

		/**
		 * Filters the input lines and writes the matching lines to the output.
		 * Whether or not a line matches the given {@code matchString} depends
		 * on the specified {@code options}.
		 * 
		 * @param matchString
		 *            the string to be matched by the lines
		 * @param options
		 *            the grep options
		 * @return the generic type {@code <R>} defined by the implementing
		 *         class, even if the command itself returns no value and writes
		 *         its result to an {@link Output} object. This serves
		 *         implementing classes like the command {@link Factory} to
		 *         return a new {@link Command} instance for the argument values
		 *         passed to this method. {@link CommandBuilder} extensions also
		 *         implementing this this command interface usually return an
		 *         instance to itself facilitating chained invocation of joined
		 *         commands.
		 */
		R grep(String matchString, Option... options);
	}

	/**
	 * Option flags for the grep command.
	 */
	public static enum Option implements org.unix4j.optset.Option<Option> {
		/**
		 * Match lines ignoring the case when comparing the strings, also know
		 * from Unix with its acronym 'i'.
		 */
		ignoreCase('i'),
		/**
		 * Invert the match result, that is, a non-matching line is written to
		 * the output and a matching line is not. This option is also known from
		 * Unix with its acronym 'v'.
		 */
		invert('v'),
		/**
		 * Uses fixed-strings matching instead of regular expressions. This
		 * option is also known from Unix with its acronym 'f'.
		 */
		fixedStrings('f');
		private final char acronym;

		private Option(char acronym) {
			this.acronym = acronym;
		}

		@Override
		public char acronym() {
			return acronym;
		}
	}

	/**
	 * Arguments and options for the grep command.
	 */
	public static class Args extends AbstractArgs<Option, Args> {
		public static final TypedMap.Key<String> MATCH_STRING = TypedMap.keyFor("matchString", String.class);

		public Args(String matchString) {
			super(Option.class);
			assertArgNotNull("matchString cannot be null", matchString);
			setArg(MATCH_STRING, matchString);
		}

		public Args(String matchString, Option... options) {
			this(matchString);
			setOpts(options);
		}

		public String getMatchString() {
			return getArg(MATCH_STRING);
		}

		public boolean isIgnoreCase() {
			return hasOpt(Option.ignoreCase);
		}

		public boolean isFixedStrings() {
			return hasOpt(Option.fixedStrings);
		}

		public boolean isInvert() {
			return hasOpt(Option.invert);
		}

		public String getRegexToRun() {
			return ".*" + getMatchString() + ".*";
		}
	}

	/**
	 * Singleton {@link Factory} for the grep command.
	 */
	public static final Factory FACTORY = new Factory();

	/**
	 * Factory class returning a new {@link Command} instance from every
	 * signature method.
	 */
	public static final class Factory implements Interface<Command> {
		@Override
		public Command grep(String matchString) {
			return new Command(new Args(matchString));
		}

		@Override
		public Command grep(String matchString, Option... options) {
			return new Command(new Args(matchString, options));
		}
	};

	/**
	 * Grep command implementation.
	 */
	public static class Command extends AbstractCommand<Args> {
		public Command(Args arguments) {
			super(NAME, arguments);
		}

		@Override
		public Command withArgs(Args arguments) {
			return new Command(arguments);
		}

		@Override
		public LineProcessor execute(LineProcessor output) {
			final Args args = getArguments();
			if (getArguments().isFixedStrings()) {
				return new FixedStringsProcessor(args, output);
			} else {
				return new RegexpProcessor(args, output);
			}
		}

		private static abstract class AbstractProcessor implements LineProcessor {
			protected final boolean isIgnoreCase;
			protected final boolean isInvert;
			protected final LineProcessor output;

			public AbstractProcessor(Args args, LineProcessor output) {
				this.isIgnoreCase = args.isIgnoreCase();
				this.isInvert = args.isInvert();
				this.output = output;
			}

			@Override
			public boolean processLine(Line line) {
				final boolean matches = matches(line);
				if (isInvert ^ matches) {
					return output.processLine(line);
				}
				return true;
			}

			@Override
			public void finish() {
				output.finish();
			}

			abstract protected boolean matches(Line line);
		}

		private static final class RegexpProcessor extends AbstractProcessor {
			private final Pattern pattern;

			public RegexpProcessor(Args args, LineProcessor output) {
				super(args, output);
				final String regex = args.getRegexToRun();
				this.pattern = Pattern.compile(regex, isIgnoreCase ? Pattern.CASE_INSENSITIVE : 0);
			}

			@Override
			public boolean matches(Line line) {
				// NOTE: we use content here because . does not match line
				// ending characters, see {@link Pattern#DOTALL}
				return pattern.matcher(line.getContent()).matches();
			}
		}

		private static final class FixedStringsProcessor extends AbstractProcessor {
			private final String matchString;

			public FixedStringsProcessor(Args args, LineProcessor output) {
				super(args, output);
				if (isIgnoreCase) {
					this.matchString = args.getMatchString().toLowerCase();
				} else {
					this.matchString = args.getMatchString();
				}
			}

			@Override
			public boolean matches(Line line) {
				if (isIgnoreCase) {
					return line.toString().toLowerCase().contains(matchString);
				} else {
					return line.toString().contains(matchString);
				}
			}
		}
	}

	// no instances
	private Grep() {
		super();
	}
}