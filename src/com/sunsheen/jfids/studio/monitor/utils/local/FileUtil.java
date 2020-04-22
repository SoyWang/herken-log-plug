package com.sunsheen.jfids.studio.monitor.utils.local;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 得到指定文件夹大小
 * @author WangSong
 *
 */
public class FileUtil {
	
	private ExecutorService service;
    final private AtomicLong pendingFileVisits = new AtomicLong();
	
	/** 通过CountdownLatch 得到文件夹大小的初始常量 **/
    final private AtomicLong totalSize = new AtomicLong();
    final private CountDownLatch latch = new CountDownLatch(1);
    
    /** 通过BlockingQueue得到文件夹大小的初始常量 **/
    final private BlockingQueue<Long> fileSizes = new ArrayBlockingQueue<Long>(500);
    

    /////////////////////////////////////CountdownLatch/////////////////////////////////////////
    //更新文件总大小（多线程）
    private void updateTotalSizeOfFilesInDir(final File file) {
        long fileSize = 0;//初始化文件大小
        //文件，直接返回大小
        if (file.isFile())
            fileSize = file.length();
        //文件夹，遍历所有文件总大小
        else {
            final File[] children = file.listFiles();
            if(null == children){
            	totalSize.set(0);
            	return;
            }
            for (final File child : children) {
            	//文件：直接加当前文件的大小
                if (child.isFile())
                    fileSize += child.length();
                //文件夹：遍历里面的文件的大小
                else {
                    pendingFileVisits.incrementAndGet();//增加一个当前值（用来观察这里的线程是否启动）
                    service.execute(new Runnable() {
                        public void run() {
                            updateTotalSizeOfFilesInDir(child);
                        }
                    });
                }
            }
        }
        totalSize.addAndGet(fileSize);
        //如果没有遍历的子文件夹，则pendingFileVisits-1 = 0，当前线程等待
        if (pendingFileVisits.decrementAndGet() == 0)
            latch.countDown();//发令枪 - 1
    }

    /**
     * 得到指定文件的大小
     * @param fileName	文件名（全路径）
     * @return	文件夹大小（M）
     * @throws InterruptedException
     */
    public double getTotalSizeOfFile(final String filePath){
        service = Executors.newCachedThreadPool();//初始化线程池
        pendingFileVisits.incrementAndGet();//增加当前值1
        double result = 0;//初始化结果
        try {
            updateTotalSizeOfFilesInDir(new File(filePath));
			latch.await(100, TimeUnit.SECONDS);//当前线程等待，直到锁存器计数到0
//			latch.await();
            //将k转换成m
            long resultK = totalSize.longValue();
            BigDecimal bdK = new BigDecimal(resultK);
            BigDecimal bdM = bdK.divide(new BigDecimal(1024 * 1024)).setScale(5, RoundingMode.HALF_UP);//保留5位小数
            result = bdM.doubleValue();
        }catch (InterruptedException e) {
			e.printStackTrace();
		}finally {
            service.shutdown();
        }
		return result;
    }
    /////////////////////////////////////CountdownLatch/////////////////////////////////////////

    
    /////////////////////////////////////BlockingQueue/////////////////////////////////////////
    private void startExploreDir(final File file) {
        pendingFileVisits.incrementAndGet();//記錄遍历文件夹次数
        service.execute(new Runnable() {
            public void run() {
                exploreDir(file);
            }
        });
    }
    
    private void exploreDir(final File file) {
        long fileSize = 0;
        if (file.isFile())
            fileSize = file.length();
        else {
            final File[] children = file.listFiles();
            if (children != null)
                for (final File child : children) {
                    if (child.isFile())
                        fileSize += child.length();
                    else {
                        startExploreDir(child);
                    }
                }
        }
        try {
            fileSizes.put(fileSize);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        pendingFileVisits.decrementAndGet();
    }

    /**
     * 得到指定文件的大小
     * @param fileName
     * @return
     * @throws InterruptedException
     */
    public double getTotalSizeOfFile1(final String fileName){
        service = Executors.newFixedThreadPool(100);
        double result = 0;
        try {
            startExploreDir(new File(fileName));
            long totalSize = 0;
            while (pendingFileVisits.get() > 0 || fileSizes.size() > 0) {
                final Long size = fileSizes.poll(10, TimeUnit.SECONDS);
                totalSize += size;
            }
            //将k转换成m
            BigDecimal bdK = new BigDecimal(totalSize);
            BigDecimal bdM = bdK.divide(new BigDecimal(1024 * 1024)).setScale(5, RoundingMode.HALF_UP);//保留5位小数            
            result = bdM.doubleValue();
        }catch(Exception e){
        	e.printStackTrace();
        }finally {
            service.shutdown();
        }
		return result;
    }
    /////////////////////////////////////BlockingQueue/////////////////////////////////////////

    
    
    /**
     * 先根遍历序递归删除文件夹
     *
     * @param dirFile 要被删除的文件或者目录
     * @return 删除成功返回true, 否则返回false
     */
    public boolean deleteFile(File dirFile) {
        // 如果dir对应的文件不存在，则退出
        if (!dirFile.exists()) {
            return false;
        }
        if (dirFile.isFile()) {
            return dirFile.delete();
        } else {
            for (File file : dirFile.listFiles()) {
                deleteFile(file);
            }
        }
        return dirFile.delete();
    }
    
}
