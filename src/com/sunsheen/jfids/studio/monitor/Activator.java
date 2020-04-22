package com.sunsheen.jfids.studio.monitor;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.core.runtime.Plugin;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.sunsheen.jfids.studio.monitor.log4j.PluginLogManager;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.sunsheen.jfids.studio.logging"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	private ArrayList<PluginLogManager> logManagers = new ArrayList<PluginLogManager>();

	/**
	 * The constructor
	 */
	public Activator() {
		plugin = this;
	}

	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Iterates over the list of active log managers and shutdowns each one
	 * before calling the base class implementation.
	 * 
	 * @see Plugin#stop
	 */
	public void stop(BundleContext context) throws Exception {
		synchronized (this.logManagers) {
			Iterator<PluginLogManager> it = this.logManagers.iterator();
			while (it.hasNext()) {
				PluginLogManager logManager = (PluginLogManager) it.next();
				logManager.internalShutdown();
			}
			this.logManagers.clear();
		}
		super.stop(context);
	}

	/**
	 * Adds a log manager object to the list of active log managers
	 */
	public void addLogManager(PluginLogManager logManager) {
		synchronized (this.logManagers) {
			if (logManager != null)
				this.logManagers.add(logManager);
		}
	}

	/**
	 * Removes a log manager object from the list of active log managers
	 */
	public void removeLogManager(PluginLogManager logManager) {
		synchronized (this.logManagers) {
			if (logManager != null)
				this.logManagers.remove(logManager);
		}
	}
}
