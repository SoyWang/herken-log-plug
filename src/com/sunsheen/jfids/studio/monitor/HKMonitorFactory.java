package com.sunsheen.jfids.studio.monitor;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;

import com.sunsheen.jfids.studio.monitor.log4j.PluginLogManager;

public class HKMonitorFactory {
	//配置信息
	public static final String LOG_PROPERTIES_FILE = "hklogger.properties";
	
	/**
	 * 获取日志操作类
	 * 
	 * @param plugin
	 *            插件,例：LoggingPlugin.getDefault()
	 * @param name
	 *            名称,例：Log.class.getName()
	 * @param propPath
	 *            配置文件路径,例："/logger.properties"
	 * @return
	 */
	public static HKMonitor getLogger(Plugin plugin, String name, String propPath) {
		PluginLogManager logManager = null;
		try {
			URL url = getUrl(plugin, propPath);
			InputStream propertiesInputStream = url.openStream();
			if (propertiesInputStream != null) {
				Properties props = new Properties();
				props.load(propertiesInputStream);
				propertiesInputStream.close();
				logManager = new PluginLogManager(plugin, props);
				logManager.hookPlugin(plugin.getBundle().getSymbolicName(), plugin.getLog());
			}
		} catch (Exception e) {
			String message = "初始化核格平台日志类出错." + e.getMessage();
			IStatus status = new Status(IStatus.ERROR, plugin.getBundle().getSymbolicName(), IStatus.ERROR, message, e);
			plugin.getLog().log(status);
			throw new RuntimeException("初始化核格平台日志类出错.", e);
		}
		return new HKMonitor(logManager.getLogger(name));
	}

	/**
	 * 获取日志操作类
	 * <p>
	 * 默认当前工程配置文件"/logger.properties"
	 * 
	 * @param plugin
	 *            插件,例：LoggingPlugin.getDefault()
	 * @param name
	 *            名称,例：Log.class.getName()
	 * @return
	 */
	public static HKMonitor getLogger(Plugin plugin, String name) {
		return getLogger(plugin, name, LOG_PROPERTIES_FILE);
	}

	/**
	 * 获取日志操作类
	 * <p>
	 * 默认Logging工程配置文件"/logger.properties"
	 * 
	 * @param name
	 *            名称,例：Log.class.getName()
	 * @return
	 */
	public static HKMonitor getLogger(String name) {
		return getLogger(Activator.getDefault(), name, LOG_PROPERTIES_FILE);
	}

	private static URL getUrl(Plugin plugin, String propPath) {
		URL url = plugin.getBundle().getEntry(propPath);
		if (url == null) {
			url = Activator.getDefault().getBundle().getEntry(propPath);
		}
		return url;
	}
}
