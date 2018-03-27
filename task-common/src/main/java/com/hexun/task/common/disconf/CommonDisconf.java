package com.hexun.task.common.disconf;

import com.baidu.disconf.client.common.annotations.DisconfFile;
import com.baidu.disconf.client.common.annotations.DisconfFileItem;

/**
 * 统一配置 修改disconf控制台数据，不用重启服务器，可立即生效
 *
 * @author xiongyan
 * @date 2016年4月14日 上午10:02:35
 */
@DisconfFile(filename = "common.properties")
public final class CommonDisconf {
	
    /**
     * zookeeper address
     */
    private static String zookeeper;

    /**
     * canal destination
     */
    private static String destination;
    
    /**
     * canal batchsize
     */
    private static Integer batchsize;
    
    /**
     * cannal consumption
     */
    private static Boolean consumption;
    
    /**
     * canal sms mobile
     */
    private static String smsMobile;
    
    private CommonDisconf() {
		
	}
    
    @DisconfFileItem(name = "zookeeper.address")
	public static String getZookeeper() {
		return zookeeper;
	}
    
    public static void setZookeeper(String zookeeper) {
		CommonDisconf.zookeeper = zookeeper;
	}

    @DisconfFileItem(name = "canal.destination")
	public static String getDestination() {
		return destination;
	}

	public static void setDestination(String destination) {
		CommonDisconf.destination = destination;
	}

	@DisconfFileItem(name = "canal.batchsize")
	public static Integer getBatchsize() {
		return batchsize;
	}

	public static void setBatchsize(Integer batchsize) {
		CommonDisconf.batchsize = batchsize;
	}

	@DisconfFileItem(name = "canal.consumption")
	public static Boolean getConsumption() {
		return consumption;
	}

	public static void setConsumption(Boolean consumption) {
		CommonDisconf.consumption = consumption;
	}

	@DisconfFileItem(name = "canal.sms.mobile")
	public static String getSmsMobile() {
		return smsMobile;
	}

	public static void setSmsMobile(String smsMobile) {
		CommonDisconf.smsMobile = smsMobile;
	}
	
}
