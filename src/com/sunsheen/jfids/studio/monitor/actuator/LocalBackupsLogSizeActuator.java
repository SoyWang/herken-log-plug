package com.sunsheen.jfids.studio.monitor.actuator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import com.sunsheen.jfids.studio.monitor.Activator;
import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;
import com.sunsheen.jfids.studio.monitor.utils.local.FileUtil;


/**
 * 本地备份的日志文件大小监控
 * @author WangSong
 *
 */
public class LocalBackupsLogSizeActuator implements Runnable {
	//日志记录
	private final HKMonitor monitor =
			HKMonitorFactory.getLogger(Activator.getDefault(), LocalBackupsLogSizeActuator.class.getName());

	
	/**
	 * 日志大小控制
	 */
	public void moniterLogSize(){
		//得到所有的日志文件夹
		final File parent = new File(LogInfo.LOCAL_LOG_ZIP_FILE_LOCATION);//本地所有日志文件夹父目录   e:\\hk-logs
		final File[] folders = parent.listFiles();//所有的项目日志zip文件夹
		final FileUtil fu = new FileUtil();
//		ExecutorService deletePool = Executors.newFixedThreadPool(100);
//		deletePool.execute(new Runnable(){
//			public void run(){
//				for(final File folder : folders){
//					double folderSize = fu.getTotalSizeOfFile(folder.getPath());//当前项目文件夹大小
//					if(folderSize > LogInfo.MAX_LOG_SIZE){
//						//每个项目分配对应线程，删文件
//						Thread delete = new Thread(new DeleteLogs(folder,null));
//						delete.setDaemon(false);
//						delete.start();
//					}				
//				}
//			}
//		});
//		deletePool.shutdown();
		
		//将需要删除log.zip的文件夹过滤出来
		List<File> folderList = new ArrayList<File>();//需要删除过期文件的文件夹
		for(final File folder : folders){
			double folderSize = fu.getTotalSizeOfFile(folder.getPath());//当前项目文件夹大小
			if(folderSize > LogInfo.MAX_LOG_SIZE)
				folderList.add(folder);
		}
		//删除
		if(!folderList.isEmpty()){
			int size = folderList.size();
			CountDownLatch latch = new CountDownLatch(size);//发令枪
			Thread[] deleteThreadArr = new Thread[size];//刪除文件的所有綫程
			for(int i = 0;i<size;i++){
				deleteThreadArr[i] = new Thread(new DeleteLogs(folderList.get(i),latch));
				deleteThreadArr[i].start();
			}
			//所有线程没有准备就绪之前，都先等待
			try {
				latch.await(); 
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	//实现删除日志zip文件的类 (删除最早创建的日志)
	private class DeleteLogs implements Runnable{
		private File file;
		private CountDownLatch latch;
		
		public DeleteLogs(final File folder,CountDownLatch latch){
			this.file = folder;
			this.latch = latch;
		}
		
		@Override
		public void run() {
			System.out.println("本地【"+ file.getName() +"】项目日志备份文件夹超出大小限制，开始删除策略 .");
			monitor.info("本地【"+ file.getName() +"】项目日志备份文件夹超出大小限制，开始删除策略 .");
			
			if(latch.getCount() == 0){
				System.out.println("删除结果：");
			}
			//删除操作
			synchronized (this) {
				try{
					remove();
				}finally{
					latch.countDown();//计数器--
				}
			}
		}
		
		//删除操作
		private void remove(){
			File[] logs = file.listFiles();//所有的zip日志文件
			//文件最后修改日期跟对应的文件map（一个修改日期可能同一时间操作了多个文件）
			Map<Long,Set<File>> waitingBeDeleteMap = new ConcurrentHashMap<Long,Set<File>>();
			//将当前文件的最后修改日期跟对应文件存入关系map
			for(File log : logs){
				//如果已经存在当前日期修改的文件，更新关联文件
				if(waitingBeDeleteMap.containsKey(log.lastModified())){
					Set<File> current = waitingBeDeleteMap.get(log.lastModified());
					current.add(log);//添加一个关联文件
					waitingBeDeleteMap.put(log.lastModified(), current);//覆盖之前的关联
				}else{
					//新创关联关系
					Set<File> current = new HashSet<File>();
					current.add(log);
					waitingBeDeleteMap.put(log.lastModified(), current);
				}
			}
			//将当前所有日志文件日期排序
			Set<Long> dates = waitingBeDeleteMap.keySet();
			Long[] orgArr = dates.toArray(new Long[dates.size()]);//转换成long数组
			Long[] resultArr = sort(orgArr);
			//依照日期，删除中间日期之间日期的所有日志
			for(int i=0;i<resultArr.length;i++){
				if(i < resultArr.length/2){
					Set<File> files = waitingBeDeleteMap.get(resultArr[i]);
					for(File f : files){
						f.delete();//立即删除
						System.out.println("删除的备份日志文件："+f.getName());
						monitor.info("【"+file.getName()+"】项目自动删除备份zip文件： "+f.getName()+"  成功！");
					}
				}
			}
		}
		
		//排序(从小到大)
		private Long[] sort(Long[] orgArr){
			for(int i=0;i<orgArr.length-1;i++) {//做第i趟排序
				int replace = i;
				//控制每次比较的次数
				for(int j=replace+1;j<orgArr.length;j++) {//选最小的记录
					if(orgArr[j]<orgArr[replace]) {
						replace = j;//记下最小值的位置下标，
					}
				}
				//在内层循环结束，即找到本轮循环则最小数之后，进行数据的交换
				if(i != replace) {
					long mid = orgArr[i];
					orgArr[i] = orgArr[replace];
					orgArr[replace] = mid;
				}
			}
			return orgArr;
		}
		
	}

	/**
	 * 运行监控
	 */
	@Override
	public void run() {
		moniterLogSize();//监控
	}
	
	
	
}
