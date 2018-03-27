package com.hexun.task.canal;

import java.util.Map;

import com.alibaba.otter.canal.protocol.CanalEntry.EventType;

/**
 * binlog日志信息
 * 
 * @author xiongyan
 * @date 2017年8月8日 下午1:03:13
 */
public class BinlogInfo {

	/**
	 * 数据库名称
	 */
	private String schemaName;
	
	/**
	 * 表名称
	 */
	private String tableName;
	
	/**
	 * 操作类型，insert/update/delete
	 */
	private EventType eventType;
	
	/**
	 * 数据 字段名->字段值
	 */
	private Map<String, Object> data;

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public EventType getEventType() {
		return eventType;
	}

	public void setEventType(EventType eventType) {
		this.eventType = eventType;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public void setData(Map<String, Object> data) {
		this.data = data;
	}
	
	/**
     * toString
     */
    @Override
    public String toString() {
    	return "BinlogInfo [schemaName=" + schemaName + ", tableName=" + tableName + ", eventType=" + eventType + ", data=" + data + "]";
    }
	
}
