package com.sunsheen.jfids.studio.monitor.utils.local;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sunsheen.jfids.studio.monitor.common.LogInfo;

/**
 * 遍历当前eclipse运行空间下所有项目名跟对应日志
 * @author WangSong
 *
 */
public class LocalLogUtil {	
	
	private LocalLogUtil(){}
	
	
	/**
	 * 遍历出存在项目日志文件的文件夹
	 * @return
	 */
	public static Map<String,Set<File>> getPlugLogs(){
		Map<String,Set<File>> associatedLogMap = new ConcurrentHashMap<String,Set<File>>();//
		//截取出正确的运行空间目录
		String runtimeSpace = LogInfo.RUNTIME_SPACE.substring(1,LogInfo.RUNTIME_SPACE.length() - 1);		
		String[] arr = runtimeSpace.split("/");
		StringBuffer sb = new StringBuffer();
		for(String space : arr)
			sb.append(space+File.separator);
		String logParentFolder = sb + LogInfo.LOG_PARENT_PATH;
		
		File file = new File(logParentFolder);//存放所有日志文件的文件夹
		listExistingLogFolder(associatedLogMap,file);
		return associatedLogMap;
	}

	
	//遍历当前文件夹下面所有文件
	private static void listExistingLogFolder(Map<String,Set<File>> associatedLogMap,File file){
		//遍历当前文件夹
		File[]  innerFiles = file.listFiles();
		for(File result : innerFiles){
			//存放对应关系
			if(result.isDirectory())
				listExistingLogFolder(associatedLogMap,result);
			else{
				String name = result.getName();//当前文件名
				//是日志文件，存入
				if(name.contains(".log")){
					String projectName = result.getParent();//上层项目名路径
					//如果不是项目日志文件不记录
					if(!projectName.contains("com.sunsheen.jfids"))
						continue;
					//截取出正确的插件项目名
					projectName = projectName.substring(projectName.lastIndexOf("c"));
					//保证能添加所有的日志文件
					if(associatedLogMap.containsKey(projectName)){
						//当前项目存在日志文件时
						Set<File> currentLogs = associatedLogMap.get(projectName);
						currentLogs.add(result);
						associatedLogMap.put(projectName, currentLogs);//保存最新的关系
					}else{
						//不存在当前项目日志文件时
						Set<File> currentLogs = new HashSet<File>();
						currentLogs.add(result);
						associatedLogMap.put(projectName,currentLogs);//创建一个新关联
					}
				}
			}
		}
	}
	
}
