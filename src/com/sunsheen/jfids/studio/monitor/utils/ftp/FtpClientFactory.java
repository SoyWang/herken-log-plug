package com.sunsheen.jfids.studio.monitor.utils.ftp;

import java.io.IOException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;
/**
 * ftp客戶端工廠
 * @author WangSong
 *
 */
public class FtpClientFactory extends BasePooledObjectFactory<FTPClient> {

	private HKMonitor log = HKMonitorFactory.getLogger(FtpClientFactory.class.getName());

	
	
    /**
     * 创建FtpClient对象
     */
    @Override
    public FTPClient create() {
        FTPClient ftpClient = new FTPClient();
        ftpClient.setControlEncoding("utf-8");
        ftpClient.setConnectTimeout(30000);//连接超时时间设置
        try {
            ftpClient.connect(LogInfo.SERVERS_ADDRESS, LogInfo.SERVERS_FTP_PORT);
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                ftpClient.disconnect();
                log.warn("FTPServer refused connection,replyCode:"+ replyCode);
                return null;
            }
 
            if (!ftpClient.login(LogInfo.SERVERS_USERNAME, LogInfo.SERVERS_PASSWORD)) {
            	System.out.println("ftpClient login failed... username is "+ LogInfo.SERVERS_USERNAME
                		+"; password: "+ LogInfo.SERVERS_PASSWORD);
                log.warn("ftpClient login failed... username is "+ LogInfo.SERVERS_USERNAME
                		+"; password: "+ LogInfo.SERVERS_PASSWORD);
            }
 
            ftpClient.setBufferSize(1024);//缓冲大小
//            ftpClient.setFileType(1);
//            if (config.isPassiveMode()) {//是否被动模式
//                ftpClient.enterLocalPassiveMode();
//            }
 
        } catch (IOException e) {
            log.error("create ftp connection failed...", e);
        }
        return ftpClient;
    }
 
    /**
     * 用PooledObject封装对象放入池中
     */
    @Override
    public PooledObject<FTPClient> wrap(FTPClient ftpClient) {
        return new DefaultPooledObject<FTPClient>(ftpClient);
    }
 
    /**
     * 销毁FtpClient对象
     */
    @Override
    public void destroyObject(PooledObject<FTPClient> ftpPooled) {
        if (ftpPooled == null) {
            return;
        }
 
        FTPClient ftpClient = ftpPooled.getObject();
 
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
            }
        } catch (IOException io) {
            log.error("ftp client logout failed...{}", io);
        } finally {
            try {
                ftpClient.disconnect();
            } catch (IOException io) {
                log.error("close ftp client failed...{}", io);
            }
        }
    }
 
    /**
     * 验证FtpClient对象
     */
    @Override
    public boolean validateObject(PooledObject<FTPClient> ftpPooled) {
        try {
            FTPClient ftpClient = ftpPooled.getObject();
            return ftpClient.sendNoOp();
        } catch (IOException e) {
            log.error("Failed to validate client: {}", e);
        }
        return false;
    }
	
}
