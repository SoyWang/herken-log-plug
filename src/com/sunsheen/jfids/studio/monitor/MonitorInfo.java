package com.sunsheen.jfids.studio.monitor;

public class MonitorInfo {
	public static final String SEPARATOR = "|";
	// 用户电脑IP
	private String ip;
	// 当前使用用户
	private String name;
	// 平台版本
	private String version = "5.0";
	// 操作编辑器
	private String editor;
	// 具体操作内容
	private String content;

	public void setEditor(String editor) {
		this.editor = editor;
	}

	public String getEditor() {
		return editor;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getContent() {
		return content;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getIp() {
		return ip;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public String toString() {
		return getIp() + SEPARATOR + getName() + SEPARATOR + getVersion() + SEPARATOR + getEditor() + SEPARATOR
				+ getContent();
	}
}
