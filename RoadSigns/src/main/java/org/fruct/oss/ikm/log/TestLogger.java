package org.fruct.oss.ikm.log;

import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.impl.StaticLoggerBinder;

public class TestLogger extends MarkerIgnoringBase {
	public TestLogger(String name) {
		this.name = name;
	}

	@Override
	public boolean isTraceEnabled() {
		return true;
	}

	@Override
	public void trace(String msg) {
		log(Log.VERBOSE, msg, null);
	}

	@Override
	public void trace(String format, Object arg) {
		formatAndLog(Log.VERBOSE, format, arg);

	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		formatAndLog(Log.VERBOSE, format, arg1, arg2);
	}

	@Override
	public void trace(String format, Object... arguments) {
		formatAndLog(Log.VERBOSE, format, arguments);
	}

	@Override
	public void trace(String msg, Throwable t) {
		log(Log.VERBOSE, msg, t);
	}

	@Override
	public boolean isDebugEnabled() {
		return true;
	}

	@Override
	public void debug(String msg) {
		log(Log.DEBUG, msg, null);
	}

	@Override
	public void debug(String format, Object arg) {
		formatAndLog(Log.DEBUG, format, arg);
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		formatAndLog(Log.DEBUG, format, arg1, arg2);
	}

	@Override
	public void debug(String format, Object... arguments) {
		formatAndLog(Log.DEBUG, format, arguments);
	}

	@Override
	public void debug(String msg, Throwable t) {
		log(Log.DEBUG, msg, t);
	}

	@Override
	public boolean isInfoEnabled() {
		return true;
	}

	@Override
	public void info(String msg) {
		log(Log.INFO, msg, null);
	}

	@Override
	public void info(String format, Object arg) {
		formatAndLog(Log.INFO, format, arg);
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		formatAndLog(Log.INFO, format, arg1, arg2);
	}

	@Override
	public void info(String format, Object... arguments) {
		formatAndLog(Log.INFO, format, arguments);
	}

	@Override
	public void info(String msg, Throwable t) {
		log(Log.INFO, msg, t);
	}

	@Override
	public boolean isWarnEnabled() {
		return true;
	}

	@Override
	public void warn(String msg) {
		log(Log.WARN, msg, null);
	}

	@Override
	public void warn(String format, Object arg) {
		formatAndLog(Log.WARN, format, arg);
	}

	@Override
	public void warn(String format, Object... arguments) {
		formatAndLog(Log.WARN, format, arguments);
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		formatAndLog(Log.WARN, format, arg1, arg2);
	}

	@Override
	public void warn(String msg, Throwable t) {
		log(Log.WARN, msg, t);
	}

	@Override
	public boolean isErrorEnabled() {
		return true;
	}

	@Override
	public void error(String msg) {
		log(Log.ERROR, msg, null);
	}

	@Override
	public void error(String format, Object arg) {
		formatAndLog(Log.ERROR, format, arg);
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		formatAndLog(Log.ERROR, format, arg1, arg2);
	}

	@Override
	public void error(String format, Object... arguments) {
		formatAndLog(Log.ERROR, format, arguments);
	}

	@Override
	public void error(String msg, Throwable t) {
		log(Log.ERROR, msg, t);
	}

	private void formatAndLog(int priority, String format, Object... argArray) {
		FormattingTuple ft = MessageFormatter.arrayFormat(format, argArray);
		log(priority, ft.getMessage(), ft.getThrowable());
	}

	private void log(int level, String message, Throwable throwable) {
		if (throwable != null) {
			message += "\n" +  Log.getStackTraceString(throwable);
		}

		Log.println(level, name, message);
	}
}
