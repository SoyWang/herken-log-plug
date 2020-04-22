package com.sunsheen.jfids.studio.monitor;

import org.apache.log4j.Logger;

public class HKMonitor {
	private Logger logger;

	protected HKMonitor(Logger logger) {
		this.logger = logger;

	}

	public void error(Object message) {
		this.logger.error(message);
	}

	public void error(Object message, Throwable t) {
		this.logger.error(message, t);
	}
	
	public void info(Object message) {
		this.logger.info(message);
	}

	public void info(Object message, Throwable t) {
		this.logger.info(message, t);
	}
	
	public void debug(Object message) {
		this.logger.debug(message);
	}
	
	public void monitor(MonitorInfo info) {
		this.logger.debug(info.toString());
	}

	public void debug(Object message, Throwable t) {
		this.logger.debug(message, t);
	}
	
	public void warn(Object message) {
		this.logger.warn(message);
	}

	public void warn(Object message, Throwable t) {
		this.logger.warn(message, t);
	}

}
