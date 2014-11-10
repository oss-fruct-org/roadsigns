package org.slf4j.impl;

import org.fruct.oss.ikm.log.TestLoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

public class StaticLoggerBinder implements LoggerFactoryBinder {
	public static String REQUESTED_API_VERSION = "1.7.7";
	private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

	private TestLoggerFactory factory = new TestLoggerFactory();

	public static StaticLoggerBinder getSingleton() {
		return SINGLETON;
	}

	@Override
	public ILoggerFactory getLoggerFactory() {
		return factory;
	}

	@Override
	public String getLoggerFactoryClassStr() {
		return TestLoggerFactory.class.getName();
	}
}
