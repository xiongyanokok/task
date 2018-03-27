package com.hexun.task.canal;

/**
 * 观察者模式
 * 
 * @author xiongyan
 * @date 2017年8月8日 下午1:24:53
 */
@FunctionalInterface
public interface BinlogObserver {

	/**
	 * 消息
	 * 
	 * @param binlogInfo
	 */
	void message(BinlogInfo binlogInfo);
}
