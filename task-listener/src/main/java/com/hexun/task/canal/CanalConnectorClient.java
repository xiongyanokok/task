package com.hexun.task.canal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.EntryType;
import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowChange;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;
import com.alibaba.otter.canal.protocol.Message;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hexun.common.sms.SmsMessage;
import com.hexun.common.sms.SmsUtils;
import com.hexun.common.utils.IpUtils;
import com.hexun.task.common.disconf.CommonDisconf;
import com.hexun.task.common.utils.CommonUtils;

/**
 * canal客户端
 * 
 * @author xiongyan
 * @date 2017年8月4日 上午10:10:44
 */
@Component
public class CanalConnectorClient implements ApplicationListener<ContextRefreshedEvent>, DisposableBean {
	
	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(CanalConnectorClient.class);

	/**
	 * 单线程池
	 */
	private static final ExecutorService THREADPOOL = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>(),
			new ThreadFactoryBuilder().setNameFormat("canal-pool-%d").build(), new ThreadPoolExecutor.AbortPolicy());
	
	/**
	 * 观察者map
	 */
	private static final Map<String, BinlogObserver> OBSERVERMAP = new HashMap<>();
	
	/**
	 * 最大重试次数
	 */
	private static final int MAX_NUM = 10;
	
	/**
	 * CanalConnector
	 */
	private CanalConnector connector;
	
	/**
	 * 销毁
	 */
	@Override
	public void destroy() throws Exception {
		if (null != connector) {
			connector.disconnect();
		}
		THREADPOOL.shutdown();
	}

	/**
	 * 初始化连接canal服务
	 */
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		// 连接Canal服务消费数据库日志
		connector = CanalConnectors.newClusterConnector(CommonDisconf.getZookeeper(), CommonDisconf.getDestination(), "", ""); 
		// 开始
		start();
	}
	
	/**
	 * 使用单线程池处理任务
	 */
	private void start() {
		THREADPOOL.execute(this::process);
    }
	
	/**
	 * 处理消息
	 */
	private void process() {
        connector.connect();
        connector.subscribe();
        int num = 0;
    	while (true) {
        	if (CommonDisconf.getConsumption()) {
        		// 获取指定数量或指定时间的数据
        		Message message = connector.getWithoutAck(CommonDisconf.getBatchsize(), 1L, TimeUnit.SECONDS); 
        		long batchId = message.getId();
        		if (batchId != -1) {
        			try {
        				for (Entry entry : message.getEntries()) {
        					notifyObserver(entry);
        				}
        			} catch (Exception e) {
        				logger.error("binlog日志通知消费端失败", e);
						// 超过最大重试次数关闭消费
        				if (num++ >= MAX_NUM) {
	        				// 关闭消费（线程等待）
	        				CommonDisconf.setConsumption(false);
	        				// 发短信
	        				sendSms();
        				} else {
	        				// 休眠1分钟
	        				try {
								Thread.sleep(60000);
							} catch (Exception ex) {
								logger.error("thread sleep error", ex);
							}
        				}
        				continue;
        			}
        		}
        		// 提交确认
        		connector.ack(batchId);
        		// 重置计数器
        		if (num > 0) {
        			num = 0;
        		}
        	} else {
        		// 休眠5秒钟
    			try {
					Thread.sleep(5000);
				} catch (Exception e) {
					// 
				}
        	}
		}
    }
	
	/**
	 * 发短信通知解决问题
	 */
	private void sendSms() {
		SmsMessage<List<String>> smsMessage = new SmsMessage<>();
		smsMessage.setMobile(Arrays.asList(CommonDisconf.getSmsMobile().split(",")));
		smsMessage.setContent("IP:" + IpUtils.getHostIP() + " 日志消费失败，尽快解决。");
		SmsUtils.sendGroupSms(smsMessage);
	}
	
	/**
	 * 添加观察者
	 * 
	 * @param key
	 * @param binlogObserver
	 */
	public void addObserver(String key, BinlogObserver binlogObserver) {
		OBSERVERMAP.put(key, binlogObserver);
	}
	
	/**
	 * 通知观察者
	 * 
	 * @param entry
	 * @throws InvalidProtocolBufferException 
	 */
	public void notifyObserver(Entry entry) throws InvalidProtocolBufferException {
		EventType eventType = entry.getHeader().getEventType();
		if (EntryType.ROWDATA != entry.getEntryType()) {
			return;
		}
		if (EventType.INSERT != eventType && EventType.UPDATE != eventType) {
			return;
		}
		String schemaName = entry.getHeader().getSchemaName();
    	String tableName = entry.getHeader().getTableName();
    	if (StringUtils.isAnyEmpty(schemaName, tableName)) {
    		return;
    	}
    	// 具体观察者key
    	String key = schemaName+"#"+tableName;
    	BinlogObserver binlogObserver = OBSERVERMAP.get(key);
    	if (null != binlogObserver) {
    		List<RowData> rowDatas = RowChange.parseFrom(entry.getStoreValue()).getRowDatasList();
    		if (CollectionUtils.isEmpty(rowDatas)) {
    			return;
    		}
    		
    		for (RowData rowData : rowDatas) {
    			List<Column> columns = rowData.getAfterColumnsList();
    			Map<String, Object> data = new HashMap<>();
    			for (Column column : columns) {
    				data.put(CommonUtils.lineToHump(column.getName()), column.getValue());
    			}
    			BinlogInfo binlogInfo = new BinlogInfo();
    			binlogInfo.setSchemaName(schemaName);
    			binlogInfo.setTableName(tableName);
    			binlogInfo.setEventType(eventType);
    			binlogInfo.setData(data);
    			binlogObserver.message(binlogInfo);
    		}
    	}
	}
	
}
