package org.unix4j.unix.sed;

import org.unix4j.line.Line;
import org.unix4j.line.SimpleLine;
import org.unix4j.processor.LineProcessor;

class ChangeProcessor extends AbstractTextProcessor {
	public ChangeProcessor(Command command, SedArguments args, LineProcessor output) {
		super(command, args, output);
	}
	public ChangeProcessor(Command command, String script, SedArguments args, LineProcessor output) {
		super(command, script, args, output);
	}

	@Override
	public boolean processLine(Line line) {
		final boolean matches = regexp.matcher(line).find();
		if (matches) {
			return output.processLine(new SimpleLine(text, line.getLineEnding()));
		}
		if (!args.isQuiet()) {
			return output.processLine(line);
		}
		return true;
	}
}